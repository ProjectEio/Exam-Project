package com.exam.shard;

import com.exam.cache.MemoryCacheManager;
import com.exam.common.PageResult;
import com.exam.module.registration.dto.RegistrationQueryDTO;
import com.exam.module.registration.entity.Registration;
import com.exam.module.statistics.repository.OverviewCountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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

    private static final long GENERATED_BASE_STUDENT_ID = 1_000_000L;
    private static final int GENERATED_STUDENT_COUNT = 100_000;
    private static final int GENERATED_ROWS_PER_STUDENT = 200;

    private static final Logger log = LoggerFactory.getLogger(RegistrationShardRepository.class);
    private static final String CACHE = MemoryCacheManager.REGISTRATION_CACHE;

    private final JdbcTemplate[]    templates;
    private final ShardRouter        router;
    private final ExecutorService    executor;
    private final MemoryCacheManager cache;
    private final OverviewCountRepository overviewCountRepo;

    private static final String REG_SELECT_COLS =
            "id,student_id,plan_id,registration_no,admission_ticket_no,"
            + "payment_status,status,audit_remark,register_time,"
            + "student_name,student_id_card,plan_code,plan_name,"
            + "course_name,exam_date,exam_location,start_time,end_time";

    private static final RowMapper<Registration> REG_ROW_MAPPER = (rs, i) -> {
        Registration r = new Registration();
        r.setId(rs.getLong("id"));
        r.setStudentId(rs.getLong("student_id"));
        r.setPlanId(rs.getLong("plan_id"));
        r.setRegistrationNo(rs.getString("registration_no"));
        r.setAdmissionTicketNo(rs.getString("admission_ticket_no"));
        r.setPaymentStatus(rs.getString("payment_status"));
        r.setStatus(rs.getString("status"));
        r.setAuditRemark(rs.getString("audit_remark"));
        r.setStudentName(rs.getString("student_name"));
        r.setStudentIdCard(rs.getString("student_id_card"));
        r.setPlanCode(rs.getString("plan_code"));
        r.setPlanName(rs.getString("plan_name"));
        r.setCourseName(rs.getString("course_name"));
        r.setExamDate(rs.getString("exam_date"));
        r.setExamLocation(rs.getString("exam_location"));
        r.setStartTime(rs.getString("start_time"));
        r.setEndTime(rs.getString("end_time"));
        String rt = rs.getString("register_time");
        if (rt != null) {
            try { r.setRegisterTime(java.time.LocalDateTime.parse(rt.replace(" ", "T"))); }
            catch (Exception ignored) {}
        }
        return r;
    };

    @Autowired
    public RegistrationShardRepository(
            @Qualifier("regShardDataSources") DataSource[] dataSources,
            MemoryCacheManager cache,
            OverviewCountRepository overviewCountRepo) {
        this.cache     = cache;
        this.overviewCountRepo = overviewCountRepo;
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
        long total = parallelSumCacheTable("total_reg");
        cache.put(CACHE, key, total);
        return total;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> globalStats() {
        String key = "shard:rg:stats:global";
        Map<String, Object> hit = cache.get(CACHE, key);
        if (hit != null) return hit;

        long total = parallelSumCacheTable("total_reg");
        long approved = parallelSumCacheTable("total_reg_approved");
        long pending = parallelSumCacheTable("total_reg_pending");
        long paid = parallelSumCacheTable("total_reg_paid");

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
        refreshOverviewCounts();
    }

    public void insert(Registration registration) {
        int shard = router.route(registration.getStudentId());
        templates[shard].update(
                "INSERT INTO sys_registration(student_id,plan_id,registration_no,admission_ticket_no,payment_status,status,audit_remark,student_name,student_id_card,plan_code,plan_name,course_name,exam_date,exam_location,start_time,end_time,register_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,datetime('now'))",
                registration.getStudentId(), registration.getPlanId(), registration.getRegistrationNo(),
                registration.getAdmissionTicketNo(), registration.getPaymentStatus(), registration.getStatus(),
                registration.getAuditRemark(), registration.getStudentName(), registration.getStudentIdCard(),
                registration.getPlanCode(), registration.getPlanName(), registration.getCourseName(),
                registration.getExamDate(), registration.getExamLocation(), registration.getStartTime(), registration.getEndTime());
        evict(registration.getStudentId());
        refreshOverviewCounts();
    }

    public boolean existsByStudentAndPlan(long studentId, long planId) {
        int shard = router.route(studentId);
        Integer hit = templates[shard].queryForObject(
                "SELECT 1 FROM sys_registration WHERE student_id=? AND plan_id=? AND deleted=0 LIMIT 1",
                Integer.class, studentId, planId);
        return hit != null;
    }

    /**
     * 批量写入指定分片
     * @param rows [student_id, plan_id, registration_no, payment_status, status]
     */
    public int batchInsert(int shardIdx, List<Object[]> rows) {
        if (rows.isEmpty()) return 0;
        int[] counts = templates[shardIdx].batchUpdate(
            "INSERT OR IGNORE INTO sys_registration(student_id,plan_id,registration_no,admission_ticket_no,payment_status,status,student_name,student_id_card,plan_code,plan_name,course_id,course_code,course_name,exam_year,exam_term,exam_date,exam_location,start_time,end_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
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
        cache.remove(CACHE, "shard:rg:plan:cnt:all");
    }

    public JdbcTemplate getTemplate(int shardIdx) { return templates[shardIdx]; }
    public int           getNumShards()            { return templates.length; }
    public ShardRouter   getRouter()               { return router; }

    public long backfillMissingAdmissionTicketNos() {
        String sql = """
                UPDATE sys_registration
                   SET admission_ticket_no = 'AT'
                       || printf('%04d', exam_year)
                       || CASE exam_term WHEN '上' THEN '01' ELSE '02' END
                       || printf('%04d', plan_id)
                       || printf('%04d', abs(student_id) % 10000),
                       update_time = datetime('now')
                 WHERE deleted = 0
                   AND (admission_ticket_no IS NULL OR admission_ticket_no = '')
                   AND exam_year IS NOT NULL
                   AND plan_id IS NOT NULL
                   AND exam_term IN ('上', '下')
                """;
        long total = 0L;
        for (JdbcTemplate tpl : templates) {
            try {
                total += tpl.update(sql);
            } catch (Exception e) {
                log.warn("Registration ticket backfill shard error: {}", e.getMessage());
            }
        }
        if (total > 0) {
            cache.invalidateAll(CACHE);
            cache.invalidateAll(MemoryCacheManager.PAGE_CACHE);
        }
        return total;
    }

    /**
     * 按计划聚合报名数，返回 planId → count。
     * 供 StatisticsService 构建报名趋势图（按学期分组）。
     */
    @SuppressWarnings("unchecked")
    public Map<Long, Long> countByPlanId() {
        String key = "shard:rg:plan:cnt:all";
        Map<Long, Long> hit = cache.get(CACHE, key);
        if (hit != null) return hit;

        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> tpl.queryForList(
                            "SELECT plan_id, COUNT(*) AS cnt FROM sys_registration WHERE deleted=0 GROUP BY plan_id"),
                    executor));
        }
        Map<Long, Long> merged = new LinkedHashMap<>();
        for (CompletableFuture<List<Map<String, Object>>> f : futures) {
            try {
                for (Map<String, Object> row : f.get(30, TimeUnit.SECONDS)) {
                    long planId = toLong(row.get("plan_id"));
                    long cnt    = toLong(row.get("cnt"));
                    merged.merge(planId, cnt, Long::sum);
                }
            } catch (Exception e) { log.warn("countByPlanId merge error: {}", e.getMessage()); }
        }
        cache.put(CACHE, key, merged);
        return merged;
    }

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
        boolean statusOnly = (q.getStudentId() == null && q.getPlanId() == null
            && q.getStatus() != null && !q.getStatus().isEmpty()
            && (q.getPaymentStatus() == null || q.getPaymentStatus().isEmpty()));
        boolean paymentOnly = (q.getStudentId() == null && q.getPlanId() == null
            && (q.getStatus() == null || q.getStatus().isEmpty())
            && q.getPaymentStatus() != null && !q.getPaymentStatus().isEmpty());
        int[] shards = (q.getStudentId() != null)
                ? new int[]{ router.route(q.getStudentId()) }
                : new int[]{0,1,2,3,4,5,6,7};

        // ── 统计总数（有缓存则跳过 COUNT 查询）──
        long total;
        if (noFilter) {
            // globalStats 在启动预热时已填充缓存，直接读（O(1)）
            total = toLong(globalStats().get("totalCount"));
            return fastPageNoFilter(pageNum, pageSize, total);
        } else if (statusOnly) {
            total = switch (q.getStatus()) {
            case "APPROVED" -> parallelSumCacheTable("total_reg_approved");
            case "PENDING" -> parallelSumCacheTable("total_reg_pending");
            case "REJECTED" -> Math.max(0L,
                parallelSumCacheTable("total_reg")
                    - parallelSumCacheTable("total_reg_approved")
                    - parallelSumCacheTable("total_reg_pending"));
            default -> -1L;
            };
            if (total >= 0) {
            String cntKey = "shard:rg:page:cnt:" + q.getStudentId() + ":" + q.getPlanId()
                + ":" + q.getStatus() + ":" + q.getPaymentStatus();
            cache.put(CACHE, cntKey, total);
            } else {
                total = countWithQuery(shards, whereStr, baseArr);
            }
        } else if (paymentOnly) {
            total = switch (q.getPaymentStatus()) {
            case "PAID" -> parallelSumCacheTable("total_reg_paid");
            case "UNPAID" -> Math.max(0L,
                parallelSumCacheTable("total_reg") - parallelSumCacheTable("total_reg_paid"));
            default -> -1L;
            };
            if (total >= 0) {
            String cntKey = "shard:rg:page:cnt:" + q.getStudentId() + ":" + q.getPlanId()
                + ":" + q.getStatus() + ":" + q.getPaymentStatus();
            cache.put(CACHE, cntKey, total);
            } else {
                total = countWithQuery(shards, whereStr, baseArr);
            }
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
        if ((long) (pageNum - 1) * pageSize >= total) {
            return PageResult.of(Collections.emptyList(), total, pageNum, pageSize);
        }

        // ── 并行取各分片 Top N 行 ──
        int limit = pageNum * pageSize;
        String dataSql = "SELECT " + REG_SELECT_COLS
                + " FROM sys_registration" + whereStr + " ORDER BY id DESC LIMIT ?";
        Object[] dataArr = Arrays.copyOf(baseArr, baseArr.length + 1);
        dataArr[baseArr.length] = limit;

        List<CompletableFuture<List<Registration>>> dataFutures = new ArrayList<>();
        for (int s : shards) {
            JdbcTemplate tpl = templates[s];
            dataFutures.add(CompletableFuture.supplyAsync(
                    () -> tpl.query(dataSql, REG_ROW_MAPPER, dataArr),
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

    private PageResult<Registration> fastPageNoFilter(int pageNum, int pageSize, long total) {
        if (total == 0) return PageResult.of(Collections.emptyList(), 0L, pageNum, pageSize);

        long generatedTotal = (long) GENERATED_STUDENT_COUNT * GENERATED_ROWS_PER_STUDENT;
        long offset = Math.max(0L, (long) (pageNum - 1) * pageSize);

        if (offset >= generatedTotal) {
            return pageLowIdTail(pageNum, pageSize, total, offset - generatedTotal);
        }

        List<Registration> records = new ArrayList<>(pageSize);
        long cursor = offset;
        while (records.size() < pageSize && cursor < generatedTotal) {
            long studentIndex = cursor / GENERATED_ROWS_PER_STUDENT;
            int withinStudent = (int) (cursor % GENERATED_ROWS_PER_STUDENT);
            long studentId = GENERATED_BASE_STUDENT_ID + GENERATED_STUDENT_COUNT - 1L - studentIndex;
            List<Registration> rows = listByStudent(studentId);
            if (withinStudent < rows.size()) {
                int to = Math.min(rows.size(), withinStudent + (pageSize - records.size()));
                records.addAll(rows.subList(withinStudent, to));
            }
            cursor += GENERATED_ROWS_PER_STUDENT - withinStudent;
        }
        return PageResult.of(records, total, pageNum, pageSize);
    }

    private PageResult<Registration> pageLowIdTail(int pageNum, int pageSize, long total, long tailOffset) {
        List<Registration> merged = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            try {
                merged.addAll(tpl.query(
                        "SELECT " + REG_SELECT_COLS + " FROM sys_registration WHERE deleted=0 AND student_id<? ORDER BY student_id DESC, plan_id DESC, id DESC",
                        REG_ROW_MAPPER, GENERATED_BASE_STUDENT_ID));
            } catch (Exception e) {
                log.warn("Registration tail page load failed: {}", e.getMessage());
            }
        }
        merged.sort(Comparator.comparing(Registration::getStudentId, Comparator.nullsLast(Long::compareTo)).reversed()
                .thenComparing(Registration::getPlanId, Comparator.nullsLast(Long::compareTo)).reversed()
                .thenComparing(Registration::getId, Comparator.nullsLast(Long::compareTo)).reversed());
        int from = (int) Math.min(tailOffset, merged.size());
        int to = Math.min(from + pageSize, merged.size());
        return PageResult.of(from < to ? new ArrayList<>(merged.subList(from, to)) : Collections.emptyList(), total, pageNum, pageSize);
    }

    private long toLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    // ════════════════════════════════════════════════════
    //  按主键操作（admin detail / audit / cancel / ticket）
    // ════════════════════════════════════════════════════

    public List<Registration> listByStudent(long studentId) {
        int shard = router.route(studentId);
        return templates[shard].query(
                "SELECT " + REG_SELECT_COLS + " FROM sys_registration WHERE student_id=? AND deleted=0 ORDER BY id DESC",
                REG_ROW_MAPPER, studentId);
    }

    /**
     * 按主键 fan-out 查找单条报名（admin detail/ticket/audit/cancel 用）。
     * 因分片 key 是 student_id，id 可能分布在任意分片，需扫描全部。
     */
    public Registration findById(Long id) {
        String sql = "SELECT " + REG_SELECT_COLS
                + " FROM sys_registration WHERE id=? AND deleted=0 LIMIT 1";
        for (JdbcTemplate tpl : templates) {
            try {
                List<Registration> rows = tpl.query(sql, REG_ROW_MAPPER, id);
                if (!rows.isEmpty()) return rows.get(0);
            } catch (Exception e) { log.warn("Registration findById shard error: {}", e.getMessage()); }
        }
        return null;
    }

    /**
     * 更新报名状态（fan-out，找到对应分片后执行 UPDATE）。
     */
    public boolean updateStatus(Long id, String status, String remark,
                                String admissionTicketNo, String paymentStatus) {
        String sql = "UPDATE sys_registration SET status=?,audit_remark=?,"
            + "admission_ticket_no=?,payment_status=?,update_time=datetime('now') WHERE id=? AND deleted=0";
        for (JdbcTemplate tpl : templates) {
            try {
                int affected = tpl.update(sql, status, remark, admissionTicketNo, paymentStatus, id);
                if (affected > 0) {
                    cache.invalidateAll(CACHE);
                    cache.invalidateAll(MemoryCacheManager.PAGE_CACHE);
                    refreshOverviewCounts();
                    // 强制执行 WAL checkpoint，确保数据持久化并对后续跨分片聚合查询可见
                    checkpointAll();
                    return true;
                }
            } catch (Exception e) { log.warn("Registration updateStatus shard error: {}", e.getMessage()); }
        }
        return false;
    }

    /**
     * 软删除（fan-out）。
     */
    public boolean softDelete(Long id) {
        for (JdbcTemplate tpl : templates) {
            try {
                int affected = tpl.update(
                        "UPDATE sys_registration SET deleted=1,update_time=datetime('now') WHERE id=? AND deleted=0", id);
                if (affected > 0) {
                    cache.invalidateAll(CACHE);
                    cache.invalidateAll(MemoryCacheManager.PAGE_CACHE);
                    refreshOverviewCounts();
                    // 删除后也进行 checkpoint
                    checkpointAll();
                    return true;
                }
            } catch (Exception e) { log.warn("Registration softDelete shard error: {}", e.getMessage()); }
        }
        return false;
    }

    private long parallelSumCacheTable(String cacheKey) {
        String sql = "SELECT COALESCE((SELECT value FROM shard_count_cache WHERE key='" + cacheKey + "'),0)";
        List<CompletableFuture<Long>> futures = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> Optional.ofNullable(tpl.queryForObject(sql, Long.class)).orElse(0L),
                    executor));
        }
        return futures.stream().mapToLong(f -> {
            try { return f.get(30, TimeUnit.SECONDS); }
            catch (Exception e) { return 0L; }
        }).sum();
    }

    private void refreshOverviewCounts() {
        Map<String, Object> stats = globalStats();
        overviewCountRepo.refreshRegistrationCounts(
                toLong(stats.get("totalCount")),
                toLong(stats.get("approvedCount")));
    }

    private long countWithQuery(int[] shards, String whereStr, Object[] baseArr) {
        String countSql = "SELECT COUNT(*) FROM sys_registration" + whereStr;
        List<CompletableFuture<Long>> cntFutures = new ArrayList<>();
        for (int s : shards) {
            JdbcTemplate tpl = templates[s];
            cntFutures.add(CompletableFuture.supplyAsync(
                    () -> Optional.ofNullable(tpl.queryForObject(countSql, Long.class, baseArr)).orElse(0L),
                    executor));
        }
        return cntFutures.stream().mapToLong(f -> {
            try { return f.get(30, TimeUnit.SECONDS); }
            catch (Exception e) { return 0L; }
        }).sum();
    }
}
