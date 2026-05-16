package com.exam.shard;

import com.exam.cache.MemoryCacheManager;
import com.exam.common.PageResult;
import com.exam.module.statistics.repository.OverviewCountRepository;
import com.exam.module.user.dto.UserQueryDTO;
import com.exam.module.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户分片仓库
 * ─────────────────────────────────────────────────────────
 * 完全替代 UserMapper（MyBatis），所有 sys_user 操作走分片。
 *
 * 路由策略（与 ScoreShardRepository 统一）：
 *   singleShard  → hash(userId) & 7     （精准路由，O(1)）
 *   fanOut       → 8 分片并行查         （用户名/关键字查找）
 *
 * 缓存策略：
 *   USER_CACHE  → 单用户对象（50K,10min）
 *   PAGE_CACHE  → 分页结果  （5K, 30s）
 */
@Repository
public class UserShardRepository {

    private static final int    NUM_SHARDS = UserShardDataSourceConfig.NUM_SHARDS;
    private static final Logger log        = LoggerFactory.getLogger(UserShardRepository.class);
    private static final long   GENERATED_BASE_STUDENT_ID = 1_000_000L;
    private static final int    GENERATED_STUDENT_COUNT   = 100_000;
    private static final long   GENERATED_MAX_STUDENT_ID  = GENERATED_BASE_STUDENT_ID + GENERATED_STUDENT_COUNT - 1;

    // ─── SELECT 列（不含 password，用于 API 响应）───────────
    private static final String COLS =
            "id,username,role,real_name,id_card,phone,email,gender,avatar,status";

    // ─── RowMapper：无密码（API 响应）────────────────────────
    private static final RowMapper<User> MAPPER = (rs, i) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setRole(rs.getString("role"));
        u.setRealName(rs.getString("real_name"));
        u.setIdCard(rs.getString("id_card"));
        u.setPhone(rs.getString("phone"));
        u.setEmail(rs.getString("email"));
        u.setGender(rs.getString("gender"));
        u.setAvatar(rs.getString("avatar"));
        u.setStatus(rs.getInt("status"));
        return u;
    };

    // ─── RowMapper：含密码（仅用于登录验证）──────────────────
    private static final RowMapper<User> MAPPER_PWD = (rs, i) -> {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setUsername(rs.getString("username"));
        u.setPassword(rs.getString("password"));
        u.setRole(rs.getString("role"));
        u.setRealName(rs.getString("real_name"));
        u.setStatus(rs.getInt("status"));
        return u;
    };

    private final JdbcTemplate[]  shards;
    private final ShardRouter      router;
    private final MemoryCacheManager cache;
    private final OverviewCountRepository overviewCountRepo;
    private final ExecutorService  pool;
    private final AtomicLong       nextIdGen;

    @Autowired
    public UserShardRepository(
            @Qualifier("userShardDataSources") DataSource[] userShardDataSources,
            MemoryCacheManager cache,
            OverviewCountRepository overviewCountRepo) {

        this.shards = new JdbcTemplate[NUM_SHARDS];
        for (int i = 0; i < NUM_SHARDS; i++) {
            this.shards[i] = new JdbcTemplate(userShardDataSources[i]);
        }
        this.router = new ShardRouter(NUM_SHARDS);
        this.cache  = cache;
        this.overviewCountRepo = overviewCountRepo;
        this.pool   = new ThreadPoolExecutor(
                NUM_SHARDS, NUM_SHARDS * 2,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                r -> { Thread t = new Thread(r, "user-shard"); t.setDaemon(true); return t; });

        // 从各分片的最大 ID 初始化全局序列号
        // 种子用户 1~5，批量生成用户从 1_000_000 开始
        // 手动注册用户从 6 开始（避免与两端冲突）
        long maxId = 5L;
        for (JdbcTemplate jt : shards) {
            try {
                Long m = jt.queryForObject("SELECT MAX(id) FROM sys_user", Long.class);
                if (m != null && m > maxId) maxId = m;
            } catch (Exception ignored) {}
        }
        // 确保不跑入批量生成用户范围（1_000_000+）
        long startId = maxId < 6L ? 6L : (maxId < 1_000_000L ? maxId + 1 : maxId + 1);
        this.nextIdGen = new AtomicLong(startId);
    }

    // ═══════════════════════════════════════════════════════
    //  读操作
    // ═══════════════════════════════════════════════════════

    /** 按 ID 精准查找（单分片，O(1)） */
    public User findById(long id) {
        String key = "u:id:" + id;
        User cached = cache.get(MemoryCacheManager.USER_CACHE, key);
        if (cached != null) return cached;

        List<User> rows = shards[router.route(id)].query(
                "SELECT " + COLS + " FROM sys_user WHERE id=? AND deleted=0",
                MAPPER, id);
        User u = rows.isEmpty() ? null : rows.get(0);
        if (u != null) cache.put(MemoryCacheManager.USER_CACHE, key, u);
        return u;
    }

    /** 按用户名查找（含密码，用于登录）— fan-out + 缓存 */
    public User findByUsernameWithPwd(String username) {
        String key = "u:npwd:" + username;
        User cached = cache.get(MemoryCacheManager.USER_CACHE, key);
        if (cached != null) return cached;

        User u = fanOut(username, true);
        if (u != null) cache.put(MemoryCacheManager.USER_CACHE, key, u);
        return u;
    }

    /** 按用户名查找（不含密码，用于成绩导入等） */
    public User findByUsername(String username) {
        String key = "u:name:" + username;
        User cached = cache.get(MemoryCacheManager.USER_CACHE, key);
        if (cached != null) return cached;

        User u = fanOut(username, false);
        if (u != null) cache.put(MemoryCacheManager.USER_CACHE, key, u);
        return u;
    }

    /** 用户名唯一性检查（fan-out，短路） */
    public boolean existsByUsername(String username) {
        String key = "u:exists:" + username;
        Boolean cached = cache.get(MemoryCacheManager.USER_CACHE, key);
        if (cached != null) return cached;

        CountDownLatch latch = new CountDownLatch(NUM_SHARDS);
        AtomicBoolean  found = new AtomicBoolean(false);
        for (JdbcTemplate jt : shards) {
            pool.submit(() -> {
                try {
                    Integer r = jt.queryForObject(
                            "SELECT 1 FROM sys_user WHERE username=? AND deleted=0 LIMIT 1",
                            Integer.class, username);
                    if (r != null) found.set(true);
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        boolean exists = found.get();
        cache.put(MemoryCacheManager.USER_CACHE, key, exists);
        return exists;
    }

    /**
     * 分页查询（fan-out 并行 count + 并行 data → 内存合并排序 → 分页）
     *
     * 每分片仅取前 page*size 条，总内存占用 = 8 × page × size，
     * 对管理页面（通常不超过 500 页）完全可接受。
     */
    public PageResult<User> page(UserQueryDTO query) {
        long pageNum  = query.getCurrent() == null ? 1 : query.getCurrent();
        long pageSize = query.getSize()    == null ? 10 : query.getSize();
        String role    = query.getRole();
        Integer status = query.getStatus();
        String keyword = query.getKeyword();
        if (keyword != null && keyword.isBlank()) keyword = null;

        String cacheKey = "u:page:" + pageNum + ":" + pageSize + ":"
                + role + ":" + status + ":" + keyword;
        PageResult<User> cachedPage = cache.get(MemoryCacheManager.PAGE_CACHE, cacheKey);
        if (cachedPage != null) return cachedPage;

        // 构造 WHERE
        StringBuilder sb = new StringBuilder("deleted=0");
        List<Object>  ps = new ArrayList<>();
        if (role != null && !role.isBlank()) { sb.append(" AND role=?");   ps.add(role); }
        if (status != null)                  { sb.append(" AND status=?"); ps.add(status); }
        if (keyword != null) {
            sb.append(" AND (username LIKE ? OR real_name LIKE ? OR phone LIKE ?)");
            String kw = "%" + keyword + "%";
            ps.add(kw); ps.add(kw); ps.add(kw);
        }
        String where = sb.toString();
        Object[] args = ps.toArray();

        boolean roleBlank = role == null || role.isBlank();
        boolean studentOnly = "STUDENT".equals(role);
        boolean fastPath = keyword == null && status == null && (roleBlank || studentOnly);

        if (fastPath) {
            long total = roleBlank ? countAll() : countByRole(role);
            PageResult<User> result = fastPage(pageNum, pageSize, total, studentOnly);
            cache.put(MemoryCacheManager.PAGE_CACHE, cacheKey, result);
            return result;
        }

        boolean noKeyword = keyword == null;
        int    limit    = (int)(pageNum * pageSize);
        String dataSql  = "SELECT " + COLS + " FROM sys_user WHERE " + where
                + " ORDER BY id DESC LIMIT " + limit;

        // 并行执行 count + data
        List<Future<List<User>>> dataFutures  = new ArrayList<>(NUM_SHARDS);
        for (JdbcTemplate jt : shards) {
            dataFutures .add(pool.submit(() -> jt.query(dataSql, MAPPER, args)));
        }

        long total;
        if (noKeyword && status == null && (role == null || role.isBlank())) {
            total = countAll();
        } else if (noKeyword && status == null && role != null && !role.isBlank()) {
            total = countByRole(role);
        } else {
            String countSql = "SELECT COUNT(*) FROM sys_user WHERE " + where;
            List<Future<Long>> countFutures = new ArrayList<>(NUM_SHARDS);
            for (JdbcTemplate jt : shards) {
                countFutures.add(pool.submit(() -> jt.queryForObject(countSql, Long.class, args)));
            }
            total = 0;
            for (Future<Long> f : countFutures) {
                try { Long c = f.get(); if (c != null) total += c; }
                catch (Exception e) { log.error("User shard count error", e); }
            }
        }

        List<User> all = new ArrayList<>();
        for (Future<List<User>> f : dataFutures) {
            try { all.addAll(f.get()); }
            catch (Exception e) { log.error("User shard data error", e); }
        }

        // 合并排序
        all.sort((a, b) -> Long.compare(b.getId(), a.getId()));

        int from    = (int)((pageNum - 1) * pageSize);
        int to      = (int) Math.min(from + pageSize, all.size());
        List<User> records = from < all.size()
                ? new ArrayList<>(all.subList(from, to))
                : Collections.emptyList();

        PageResult<User> result = PageResult.of(records, total, pageNum, pageSize);
        cache.put(MemoryCacheManager.PAGE_CACHE, cacheKey, result);
        return result;
    }

    private PageResult<User> fastPage(long pageNum, long pageSize, long total, boolean studentOnly) {
        if (total == 0) return PageResult.of(Collections.emptyList(), 0L, pageNum, pageSize);

        long offset = Math.max(0L, (pageNum - 1) * pageSize);
        if (offset >= total) return PageResult.of(Collections.emptyList(), total, pageNum, pageSize);

        List<User> highUsers = loadSpecialUsers(true, studentOnly);
        List<User> lowUsers = loadSpecialUsers(false, studentOnly);
        List<User> records = new ArrayList<>((int) pageSize);
        long remainingOffset = offset;

        if (remainingOffset < highUsers.size()) {
            int from = (int) remainingOffset;
            int to = (int) Math.min(highUsers.size(), from + pageSize);
            records.addAll(highUsers.subList(from, to));
            remainingOffset = 0;
        } else {
            remainingOffset -= highUsers.size();
        }

        if (records.size() < pageSize) {
            if (remainingOffset < GENERATED_STUDENT_COUNT) {
                long generatedIndex = remainingOffset;
                while (records.size() < pageSize && generatedIndex < GENERATED_STUDENT_COUNT) {
                    int need = (int) (pageSize - records.size());
                    List<Long> ids = new ArrayList<>(need);
                    while (ids.size() < need && generatedIndex < GENERATED_STUDENT_COUNT) {
                        ids.add(GENERATED_MAX_STUDENT_ID - generatedIndex);
                        generatedIndex++;
                    }
                    List<User> batch = loadUsersByIdsDescending(ids);
                    if (batch.isEmpty()) break;
                    records.addAll(batch);
                }
                remainingOffset = 0;
            } else {
                remainingOffset -= GENERATED_STUDENT_COUNT;
            }
        }

        if (records.size() < pageSize && remainingOffset < lowUsers.size()) {
            int from = (int) remainingOffset;
            int to = (int) Math.min(lowUsers.size(), from + (pageSize - records.size()));
            records.addAll(lowUsers.subList(from, to));
        }

        return PageResult.of(records, total, pageNum, pageSize);
    }

    @SuppressWarnings("unchecked")
    private List<User> loadSpecialUsers(boolean highRange, boolean studentOnly) {
        String key = "u:special:" + (highRange ? "high" : "low") + ":" + (studentOnly ? "student" : "all");
        List<User> cached = cache.get(MemoryCacheManager.USER_CACHE, key);
        if (cached != null) return cached;

        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM sys_user WHERE deleted=0 ");
        List<Object> args = new ArrayList<>();
        if (highRange) {
            sql.append("AND id>? ");
            args.add(GENERATED_MAX_STUDENT_ID);
        } else {
            sql.append("AND id<? ");
            args.add(GENERATED_BASE_STUDENT_ID);
        }
        if (studentOnly) {
            sql.append("AND role=? ");
            args.add("STUDENT");
        }
        sql.append("ORDER BY id DESC");

        List<Future<List<User>>> futures = new ArrayList<>(NUM_SHARDS);
        Object[] params = args.toArray();
        for (JdbcTemplate jt : shards) {
            futures.add(pool.submit(() -> jt.query(sql.toString(), MAPPER, params)));
        }

        List<User> all = new ArrayList<>();
        for (Future<List<User>> f : futures) {
            try { all.addAll(f.get()); }
            catch (Exception e) { log.error("User special page query error", e); }
        }
        all.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        cache.put(MemoryCacheManager.USER_CACHE, key, all);
        return all;
    }

    private List<User> loadUsersByIdsDescending(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Collections.emptyList();

        java.util.Map<Integer, List<Long>> byShard = new java.util.LinkedHashMap<>();
        for (Long id : ids) {
            byShard.computeIfAbsent(router.route(id), k -> new ArrayList<>()).add(id);
        }

        List<Future<List<User>>> futures = new ArrayList<>(byShard.size());
        for (java.util.Map.Entry<Integer, List<Long>> entry : byShard.entrySet()) {
            int shard = entry.getKey();
            List<Long> shardIds = entry.getValue();
            futures.add(pool.submit(() -> {
                String placeholders = String.join(",", java.util.Collections.nCopies(shardIds.size(), "?"));
                String sql = "SELECT " + COLS + " FROM sys_user WHERE deleted=0 AND id IN (" + placeholders + ")";
                Object[] params = shardIds.toArray();
                return shards[shard].query(sql, MAPPER, params);
            }));
        }

        List<User> users = new ArrayList<>(ids.size());
        for (Future<List<User>> f : futures) {
            try { users.addAll(f.get()); }
            catch (Exception e) { log.error("User id batch load error", e); }
        }
        users.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        return users;
    }

    // ═══════════════════════════════════════════════════════
    //  写操作
    // ═══════════════════════════════════════════════════════

    /** 新增用户（自动分配全局唯一 ID，路由到对应分片） */
    public void insert(User user) {
        if (user.getId() == null) {
            user.setId(nextIdGen.getAndIncrement());
        }
        int shard = router.route(user.getId());
        shards[shard].update(
                "INSERT INTO sys_user(id,username,password,role,real_name,id_card,phone,email,gender,status)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                user.getId(), user.getUsername(), user.getPassword(), user.getRole(),
                user.getRealName(), user.getIdCard(), user.getPhone(), user.getEmail(),
                user.getGender(), user.getStatus() == null ? 1 : user.getStatus());
        // 清除存在性缓存
        cache.remove(MemoryCacheManager.USER_CACHE, "u:exists:" + user.getUsername());
        evictUserCountCaches();
        cache.invalidateAll(MemoryCacheManager.PAGE_CACHE);
        refreshOverviewCounts();
    }

    /** 更新用户信息 */
    public void update(User user) {
        int shard = router.route(user.getId());
        shards[shard].update(
                "UPDATE sys_user SET role=?,real_name=?,id_card=?,phone=?,email=?,"
                + "gender=?,status=?,avatar=?,update_time=datetime('now') WHERE id=? AND deleted=0",
                user.getRole(), user.getRealName(), user.getIdCard(), user.getPhone(),
                user.getEmail(), user.getGender(), user.getStatus(), user.getAvatar(),
                user.getId());
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            shards[shard].update(
                    "UPDATE sys_user SET password=? WHERE id=? AND deleted=0",
                    user.getPassword(), user.getId());
        }
        // 清除缓存
        cache.remove(MemoryCacheManager.USER_CACHE, "u:id:" + user.getId());
        evictUserCountCaches();
        cache.invalidateAll(MemoryCacheManager.PAGE_CACHE);
        refreshOverviewCounts();
    }

    /** 软删除 */
    public void deleteById(long id) {
        shards[router.route(id)].update(
                "UPDATE sys_user SET deleted=1 WHERE id=?", id);
        cache.remove(MemoryCacheManager.USER_CACHE, "u:id:" + id);
        evictUserCountCaches();
        cache.invalidateAll(MemoryCacheManager.PAGE_CACHE);
        refreshOverviewCounts();
    }

    private void evictUserCountCaches() {
        cache.remove(MemoryCacheManager.USER_CACHE, "u:cnt:all");
        for (String role : List.of("ADMIN", "TEACHER", "STUDENT")) {
            cache.remove(MemoryCacheManager.USER_CACHE, "u:cnt:role:" + role);
        }
        cache.remove(MemoryCacheManager.USER_CACHE, "u:special:high:all");
        cache.remove(MemoryCacheManager.USER_CACHE, "u:special:low:all");
        cache.remove(MemoryCacheManager.USER_CACHE, "u:special:high:student");
        cache.remove(MemoryCacheManager.USER_CACHE, "u:special:low:student");
    }

    private void refreshOverviewCounts() {
        overviewCountRepo.refreshUserCounts(countAll(), countByRole("STUDENT"));
    }

    // ═══════════════════════════════════════════════════════
    //  内部工具
    // ═══════════════════════════════════════════════════════

    private User fanOut(String username, boolean withPwd) {
        String cols = withPwd
                ? "id,username,password,role,real_name,status"
                : COLS;
        String sql = "SELECT " + cols
                + " FROM sys_user WHERE username=? AND deleted=0 LIMIT 1";
        RowMapper<User> mapper = withPwd ? MAPPER_PWD : MAPPER;

        List<Future<List<User>>> futures = new ArrayList<>(NUM_SHARDS);
        for (JdbcTemplate jt : shards) {
            futures.add(pool.submit(() -> jt.query(sql, mapper, username)));
        }
        for (Future<List<User>> f : futures) {
            try {
                List<User> r = f.get();
                if (!r.isEmpty()) return r.get(0);
            } catch (Exception e) {
                log.error("User fan-out error for username={}", username, e);
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════
    //  统计接口（替代主库 StatisticsMapper）
    // ═══════════════════════════════════════════════════════

    /** 统计所有未删除用户总数（fan-out 8 分片求和） */
    public long countAll() {
        String key = "u:cnt:all";
        Long hit = cache.get(MemoryCacheManager.USER_CACHE, key);
        if (hit != null) return hit;
        long total = parallelSumCacheTable("total_user");
        cache.put(MemoryCacheManager.USER_CACHE, key, total);
        return total;
    }

    /** 统计指定角色的用户数（fan-out 8 分片求和） */
    public long countByRole(String role) {
        String key = "u:cnt:role:" + role;
        Long hit = cache.get(MemoryCacheManager.USER_CACHE, key);
        if (hit != null) return hit;
        String cacheKey = switch (role) {
            case "ADMIN" -> "role_admin";
            case "TEACHER" -> "role_teacher";
            case "STUDENT" -> "role_student";
            default -> null;
        };
        long total;
        if (cacheKey != null) {
            total = parallelSumCacheTable(cacheKey);
        } else {
            List<Future<Long>> futures = new ArrayList<>(NUM_SHARDS);
            for (JdbcTemplate jt : shards) {
                futures.add(pool.submit(() ->
                    jt.queryForObject("SELECT COUNT(*) FROM sys_user WHERE deleted=0 AND role=?", Long.class, role)));
            }
            total = 0;
            for (Future<Long> f : futures) {
                try { Long c = f.get(); if (c != null) total += c; }
                catch (Exception e) { log.error("User countByRole shard error", e); }
            }
        }
        cache.put(MemoryCacheManager.USER_CACHE, key, total);
        return total;
    }

    private long parallelSumCacheTable(String cacheKey) {
        List<Future<Long>> futures = new ArrayList<>(NUM_SHARDS);
        for (JdbcTemplate jt : shards) {
            futures.add(pool.submit(() ->
                    jt.queryForObject("SELECT COALESCE((SELECT value FROM user_count_cache WHERE key=?),0)", Long.class, cacheKey)));
        }
        long total = 0;
        for (Future<Long> f : futures) {
            try { Long c = f.get(); if (c != null) total += c; }
            catch (Exception e) { log.error("User count cache read error", e); }
        }
        return total;
    }
}
