package com.exam.shard;

import com.exam.cache.MemoryCacheManager;
import com.exam.common.PageResult;
import com.exam.module.registration.dto.RegistrationQueryDTO;
import com.exam.module.registration.entity.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;

/**
 * 分片 Registration 仓库（8 分片 + 内存缓存）
 * ─────────────────────────────────────────────────────────
 * 缓存策略：
 *   findByStudentId   → shard:rg:stu:{studentId}
 *   countByStudentId  → shard:rg:cnt:{studentId}
 *   countAll          → shard:rg:cnt:all
 *   globalStats       → shard:rg:stats:global
 *
 * 注意：findByRegistrationNo 并行扫全分片，不缓存（业务频率低）
 */
@Repository
public class RegistrationShardRepository {

    private static final Logger log = LoggerFactory.getLogger(RegistrationShardRepository.class);
    private static final String CACHE = MemoryCacheManager.REGISTRATION_CACHE;

    private final JdbcTemplate[]    templates;
    private final ShardRouter        router;
    private final ExecutorService    executor;
    private final MemoryCacheManager cache;

    @Autowired
    public RegistrationShardRepository(
            @Qualifier("regShardDataSources") DataSource[] dataSources,
            MemoryCacheManager cache) {
        this.cache     = cache;
        this.templates = new JdbcTemplate[dataSources.length];
        for (int i = 0; i < dataSources.length; i++) {
            this.templates[i] = new JdbcTemplate(dataSources[i]);
            this.templates[i].setFetchSize(1000);
        }
        this.router   = new ShardRouter(ShardDataSourceConfig.NUM_SHARDS);
        this.executor = new ThreadPoolExecutor(
                dataSources.length,
                dataSources.length * 2,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                r -> { Thread t = new Thread(r); t.setDaemon(true); t.setName("shard-reg-" + t.getId()); return t; });
    }

    // ════════════════════════════════════════════════════
    //  单分片查询（缓存优先）
    // ════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findByStudentId(long studentId) {
        String key = "shard:rg:stu:" + studentId;
        List<Map<String, Object>> hit = cache.get(CACHE, key);
        if (hit != null) return hit;
        int shard = router.route(studentId);
        List<Map<String, Object>> rows = templates[shard].queryForList(
                "SELECT * FROM sys_registration WHERE student_id=? AND deleted=0 ORDER BY id DESC",
                studentId);
        cache.put(CACHE, key, rows);
        return rows;
    }

    /** 按报名编号查询：因路由不确定，并行扫全分片（低频，不缓存） */
    public Map<String, Object> findByRegistrationNo(String regNo) {
        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    tpl.queryForList(
                            "SELECT * FROM sys_registration WHERE registration_no=? AND deleted=0 LIMIT 1",
                            regNo), executor));
        }
        for (CompletableFuture<List<Map<String, Object>>> f : futures) {
            try {
                List<Map<String, Object>> rows = f.get(10, TimeUnit.SECONDS);
                if (!rows.isEmpty()) return rows.get(0);
            } catch (Exception e) { log.warn("查询 regNo 失败: {}", e.getMessage()); }
        }
        return null;
    }

    public long countByStudentId(long studentId) {
        String key = "shard:rg:cnt:" + studentId;
        Long hit = cache.get(CACHE, key);
        if (hit != null) return hit;
        int shard = router.route(studentId);
        Long n = templates[shard].queryForObject(
                "SELECT COUNT(*) FROM sys_registration WHERE student_id=? AND deleted=0",
                Long.class, studentId);
        long val = n == null ? 0L : n;
        cache.put(CACHE, key, val);
        return val;
    }

    // ════════════════════════════════════════════════════
    //  跨分片聚合（并行 + 缓存）
    // ════════════════════════════════════════════════════

    public long countAll() {
        String key = "shard:rg:cnt:all";
        Long hit = cache.get(CACHE, key);
        if (hit != null) return hit;

        List<CompletableFuture<Long>> futures = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    Optional.ofNullable(tpl.queryForObject(
                            "SELECT COUNT(*) FROM sys_registration WHERE deleted=0", Long.class)
                    ).orElse(0L), executor));
        }
        long total = futures.stream().mapToLong(f -> {
            try { return f.get(30, TimeUnit.SECONDS); } catch (Exception e) { return 0L; }
        }).sum();
        cache.put(CACHE, key, total);
        return total;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> globalStats() {
        String key = "shard:rg:stats:global";
        Map<String, Object> hit = cache.get(CACHE, key);
        if (hit != null) return hit;

        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                Map<String, Object> m = new HashMap<>();
                m.put("total",    tpl.queryForObject("SELECT COUNT(*) FROM sys_registration WHERE deleted=0", Long.class));
                m.put("approved", tpl.queryForObject("SELECT COUNT(*) FROM sys_registration WHERE status='APPROVED' AND deleted=0", Long.class));
                m.put("pending",  tpl.queryForObject("SELECT COUNT(*) FROM sys_registration WHERE status='PENDING'  AND deleted=0", Long.class));
                m.put("paid",     tpl.queryForObject("SELECT COUNT(*) FROM sys_registration WHERE payment_status='PAID' AND deleted=0", Long.class));
                return m;
            }, executor));
        }

        long total = 0, approved = 0, pending = 0, paid = 0;
        for (CompletableFuture<Map<String, Object>> f : futures) {
            try {
                Map<String, Object> m = f.get(30, TimeUnit.SECONDS);
                total    += toLong(m.get("total"));
                approved += toLong(m.get("approved"));
                pending  += toLong(m.get("pending"));
                paid     += toLong(m.get("paid"));
            } catch (Exception e) { log.warn("注册统计出错: {}", e.getMessage()); }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount",    total);
        result.put("approvedCount", approved);
        result.put("pendingCount",  pending);
        result.put("paidCount",     paid);
        result.put("approvalRate",  total > 0 ? String.format("%.2f%%", 100.0 * approved / total) : "0%");
        cache.put(CACHE, key, result);
        return result;
    }

    // ════════════════════════════════════════════════════
    //  写操作
    // ════════════════════════════════════════════════════

    public void insert(long studentId, long planId, String registrationNo,
                       String paymentStatus, String status) {
        int shard = router.route(studentId);
        templates[shard].update(
                "INSERT OR IGNORE INTO sys_registration(student_id,plan_id,registration_no,payment_status,status) VALUES(?,?,?,?,?)",
                studentId, planId, registrationNo, paymentStatus, status);
        evict(studentId);
    }

    /**
     * 批量写入指定分片
     * @param rows [student_id, plan_id, registration_no, payment_status, status]
     */
    public int batchInsert(int shardIdx, List<Object[]> rows) {
        if (rows.isEmpty()) return 0;
        int[] counts = templates[shardIdx].batchUpdate(
                "INSERT OR IGNORE INTO sys_registration(student_id,plan_id,registration_no,payment_status,status) VALUES(?,?,?,?,?)",
                rows);
        evictGlobalStats();
        return Arrays.stream(counts).sum();
    }

    // ════════════════════════════════════════════════════
    //  缓存失效
    // ════════════════════════════════════════════════════

    private void evict(long studentId) {
        cache.remove(CACHE, "shard:rg:stu:" + studentId);
        cache.remove(CACHE, "shard:rg:cnt:" + studentId);
        evictGlobalStats();
    }

    private void evictGlobalStats() {
        cache.remove(CACHE, "shard:rg:cnt:all");
        cache.remove(CACHE, "shard:rg:stats:global");
    }

    public JdbcTemplate getTemplate(int shardIdx) { return templates[shardIdx]; }
    public int           getNumShards()            { return templates.length; }
    public ShardRouter   getRouter()               { return router; }

    // ════════════════════════════════════════════════════
    //  跨分片分页（管理后台）
    // ════════════════════════════════════════════════════

    /**
     * 支持过滤条件的跨分片分页，供管理后台报名列表使用。
     * 若指定 studentId，精准路由单分片；否则 fan-out 8 分片后内存合并。
     */
    /**
     * 触发 WAL checkpoint（PASSIVE 模式，不阻塞当前读写）。
     */
    public void checkpointAll() {
        for (JdbcTemplate tpl : templates) {
            try { tpl.execute("PRAGMA wal_checkpoint(PASSIVE)"); }
            catch (Exception e) { log.warn("Reg shard WAL checkpoint failed: {}", e.getMessage()); }
        }
        log.info("[RegShard] WAL checkpoint 完成");
    }

    public PageResult<Registration> page(RegistrationQueryDTO q) {
        // ── 构造 WHERE 子句 ──
        StringBuilder where = new StringBuilder(" WHERE deleted=0");
        List<Object> params = new ArrayList<>();
        if (q.getStudentId()     != null) { where.append(" AND student_id=?");     params.add(q.getStudentId()); }
        if (q.getPlanId()        != null) { where.append(" AND plan_id=?");        params.add(q.getPlanId()); }
        if (q.getStatus()        != null && !q.getStatus().isEmpty())        { where.append(" AND status=?");         params.add(q.getStatus()); }
        if (q.getPaymentStatus() != null && !q.getPaymentStatus().isEmpty()) { where.append(" AND payment_status=?"); params.add(q.getPaymentStatus()); }

        String whereStr  = where.toString();
        Object[] baseArr = params.toArray();
        int pageNum  = q.getCurrent() != null ? q.getCurrent().intValue() : 1;
        int pageSize = q.getSize()    != null ? q.getSize().intValue()    : 10;

        // ── 确定目标分片 ──
        boolean noFilter = (q.getStudentId() == null && q.getPlanId() == null
                && (q.getStatus() == null || q.getStatus().isEmpty())
                && (q.getPaymentStatus() == null || q.getPaymentStatus().isEmpty()));
        int[] shards = (q.getStudentId() != null)
                ? new int[]{ router.route(q.getStudentId()) }
                : new int[]{0,1,2,3,4,5,6,7};

        // ── 统计总数（有缓存则跳过 COUNT 查询）──
        long total;
        if (noFilter) {
            // globalStats 在启动预热时已填充缓存，直接读（O(1)）
            total = toLong(globalStats().get("totalCount"));
        } else {
            String cntKey = "shard:rg:page:cnt:" + q.getStudentId() + ":" + q.getPlanId()
                    + ":" + q.getStatus() + ":" + q.getPaymentStatus();
            Long cached = cache.get(CACHE, cntKey);
            if (cached != null) {
                total = cached;
            } else {
                String countSql = "SELECT COUNT(*) FROM sys_registration" + whereStr;
                List<CompletableFuture<Long>> cntFutures = new ArrayList<>();
                for (int s : shards) {
                    JdbcTemplate tpl = templates[s];
                    cntFutures.add(CompletableFuture.supplyAsync(
                            () -> Optional.ofNullable(tpl.queryForObject(countSql, Long.class, baseArr)).orElse(0L),
                            executor));
                }
                total = cntFutures.stream().mapToLong(f -> {
                    try { return f.get(30, TimeUnit.SECONDS); } catch (Exception e) { return 0L; }
                }).sum();
                cache.put(CACHE, cntKey, total);
            }
        }
        if (total == 0) return PageResult.of(Collections.emptyList(), 0L, pageNum, pageSize);

        // ── 并行取各分片 Top N 行 ──
        int limit = pageNum * pageSize;
        String dataSql = "SELECT id,student_id,plan_id,registration_no,admission_ticket_no,"
                + "payment_status,status,audit_remark,register_time"
                + " FROM sys_registration" + whereStr + " ORDER BY id DESC LIMIT ?";
        Object[] dataArr = Arrays.copyOf(baseArr, baseArr.length + 1);
        dataArr[baseArr.length] = limit;

        List<CompletableFuture<List<Registration>>> dataFutures = new ArrayList<>();
        for (int s : shards) {
            JdbcTemplate tpl = templates[s];
            dataFutures.add(CompletableFuture.supplyAsync(
                    () -> tpl.query(dataSql, (rs, i) -> {
                        Registration r = new Registration();
                        r.setId(rs.getLong("id"));
                        r.setStudentId(rs.getLong("student_id"));
                        r.setPlanId(rs.getLong("plan_id"));
                        r.setRegistrationNo(rs.getString("registration_no"));
                        r.setAdmissionTicketNo(rs.getString("admission_ticket_no"));
                        r.setPaymentStatus(rs.getString("payment_status"));
                        r.setStatus(rs.getString("status"));
                        r.setAuditRemark(rs.getString("audit_remark"));
                        String rt = rs.getString("register_time");
                        if (rt != null) {
                            try { r.setRegisterTime(java.time.LocalDateTime.parse(rt.replace(" ", "T"))); }
                            catch (Exception ignored) {}
                        }
                        return r;
                    }, dataArr),
                    executor));
        }
        List<Registration> merged = new ArrayList<>();
        for (CompletableFuture<List<Registration>> f : dataFutures) {
            try { merged.addAll(f.get(30, TimeUnit.SECONDS)); }
            catch (Exception e) { log.warn("Registration page merge failed: {}", e.getMessage()); }
        }
        merged.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        int from = Math.min((pageNum - 1) * pageSize, merged.size());
        int to   = Math.min(from + pageSize, merged.size());
        return PageResult.of(new ArrayList<>(merged.subList(from, to)), total, pageNum, pageSize);
    }

    private long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }
}
