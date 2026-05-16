package com.exam.benchmark;

import com.exam.module.statistics.repository.OverviewCountRepository;
import com.exam.module.statistics.service.StatisticsService;
import com.exam.shard.RegistrationShardRepository;
import com.exam.shard.ScoreShardRepository;
import com.exam.shard.ShardDataSourceConfig;
import com.exam.shard.ShardRouter;
import com.exam.shard.UserShardDataSourceConfig;
import com.exam.shard.UserShardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 2000万数据生成服务
 * ─────────────────────────────────────────────────────────
 * 生成策略：
 *   sys_score        – 10万学生 × 200条 = 2000万
 *   sys_registration – 10万学生 × 200条 = 2000万
 *
 * 性能优化手段：
 *   1. 4线程并行（每线程负责一个分片）
 *   2. 每次 batchUpdate 500条（平衡内存与吞吐）
 *   3. 每个分片独立事务（减少锁竞争）
 *   4. 使用 WAL 模式 + synchronous=NORMAL（已在 datasource 配置）
 *   5. 每个分片写入前关闭自动提交，手动 COMMIT
 *
 * 生成进度通过 AtomicLong 实时报告，可通过 /api/benchmark/progress 查询。
 */
@Service
public class DataGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(DataGeneratorService.class);

    // ── 生成参数 ──────────────────────────────────────────
    private static final int  TOTAL_STUDENTS  = 100_000;   // 10万学生
    private static final int  SCORES_PER_STU  = 200;       // 每生200条成绩
    private static final int  REGS_PER_STU    = 200;       // 每生200条报名
    private static final int  BATCH_SIZE      = 500;       // 每次批量提交行数
    private static final long BASE_STUDENT_ID = 1_000_000L;// 起始 studentId（避免与真实数据冲突）
    private static final int  BENCHMARK_DIM   = 200;

    private static final String[] STATUSES   = {"PASS", "FAIL", "ABSENT"};
    private static final String[] TERMS      = {"上", "下"};
    private static final String[] PAY_STATUS = {"PAID", "UNPAID"};
    private static final String[] REG_STATUS = {"APPROVED", "PENDING", "REJECTED"};

    @Autowired private ScoreShardRepository        scoreRepo;
    @Autowired private RegistrationShardRepository regRepo;
    @Autowired private UserShardRepository         userRepo;
    @Autowired private OverviewCountRepository     overviewCountRepo;
    @Autowired private StatisticsService           statisticsService;

    /** 用户分片数据源（generateUsers 直接批量写入，绕过 ORM 开销） */
    @Autowired
    @Qualifier("userShardDataSources")
    private DataSource[] userShardDataSources;

    @Autowired
    private DataSource dataSource;

    // ── 进度追踪（private + getter，避免 CGLIB 代理字段为 null）────
    private final AtomicLong scoreInserted = new AtomicLong(0);
    private final AtomicLong regInserted   = new AtomicLong(0);
    private final AtomicLong userInserted  = new AtomicLong(0);
    private volatile boolean running       = false;
    private volatile String  lastError     = null;
    private volatile long    startTimeMs   = 0;

    public long    getScoreInserted() { return scoreInserted.get(); }
    public long    getRegInserted()   { return regInserted.get();   }
    public long    getUserInserted()  { return userInserted.get();  }
    public boolean isRunning()        { return running;             }
    public String  getLastError()     { return lastError;           }
    public long    getStartTimeMs()   { return startTimeMs;         }

    public void ensureBenchmarkMetadata() {
        JdbcTemplate tpl = new JdbcTemplate(dataSource);

        List<Object[]> courseRows = new ArrayList<>();
        for (int courseId = 13; courseId <= BENCHMARK_DIM; courseId++) {
            courseRows.add(new Object[]{
                    courseId,
                    benchmarkCourseCode(courseId),
                    benchmarkCourseName(courseId),
                    3 + (courseId % 4),
                    courseId % 3 == 0 ? "公共课" : "专业课",
                    "性能基准课程 " + courseId
            });
        }
        if (!courseRows.isEmpty()) {
            tpl.batchUpdate(
                    "INSERT OR IGNORE INTO sys_course(id,course_code,course_name,credit,course_type,description,deleted) VALUES(?,?,?,?,?,?,0)",
                    courseRows);
        }

        List<Object[]> planRows = new ArrayList<>();
        for (int planId = 7; planId <= BENCHMARK_DIM; planId++) {
            int examYear = 2024 + ((planId - 1) / 100);
            String examTerm = TERMS[(planId - 1) % 2];
            int examMonth = "上".equals(examTerm) ? 4 : 10;
            int examDay = ((planId - 1) % 28) + 1;
            String examDate = String.format("%d-%02d-%02d", examYear, examMonth, examDay);
            int majorId = ((planId - 1) % 4) + 1;
            String registerStart = String.format("%d-%02d-01", examYear, "上".equals(examTerm) ? 2 : 8);
            String registerEnd = String.format("%d-%02d-15", examYear, "上".equals(examTerm) ? 3 : 9);
            planRows.add(new Object[]{
                    planId,
                    benchmarkPlanCode(planId, examYear, examTerm),
                    benchmarkPlanName(planId, examYear, examTerm),
                    examYear,
                    examTerm,
                    planId,
                    majorId,
                    examDate,
                    "上".equals(examTerm) ? "09:00" : "14:30",
                    "上".equals(examTerm) ? "11:30" : "17:00",
                    "模拟考点-" + (((planId - 1) % 8) + 1) + "号教室",
                    200_000,
                    registerStart,
                    registerEnd,
                    "PUBLISHED",
                    "基准压测计划 " + planId
            });
        }
        if (!planRows.isEmpty()) {
            tpl.batchUpdate(
                    "INSERT OR IGNORE INTO sys_exam_plan(id,plan_code,plan_name,exam_year,exam_term,course_id,major_id,exam_date,start_time,end_time,location,capacity,registered_count,register_start,register_end,status,remark,deleted) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?,?,0)",
                    planRows);
        }
    }

    // ─────────────────────────────────────────────────────
    //  用户 10 万 生成（主库 sys_user）
    // ─────────────────────────────────────────────────────

    /**
     * 向 8 个用户分片并行写入 10 万学生账号。
     * ID 范围: BASE_STUDENT_ID ~ BASE_STUDENT_ID+TOTAL_STUDENTS-1
     * 与分片成绩/报名表的 student_id 完全对应。
     */
    public void generateUsers() {
        final int numShards = UserShardDataSourceConfig.NUM_SHARDS;
        final ShardRouter router = new ShardRouter(numShards);
        final String PWD_HASH = "$2a$10$DZyY1hz9uXKjUp4LSXx8UOchNHXGdGutWFtDuGZ5AGXlpx/yi9y6i";
        final String[] GENDERS = {"男", "女"};
        final String INSERT_SQL =
                "INSERT OR IGNORE INTO sys_user(id,username,password,role,real_name,id_card,phone,gender,status)"
                + " VALUES(?,?,?,?,?,?,?,?,1)";

        // 按分片预分配 ID
        @SuppressWarnings("unchecked")
        List<long[]>[] shardIds = new List[numShards];
        for (int s = 0; s < numShards; s++) shardIds[s] = new ArrayList<>();
        for (int i = 0; i < TOTAL_STUDENTS; i++) {
            long id = BASE_STUDENT_ID + i;
            shardIds[router.route(id)].add(new long[]{id, i});
        }

        // 检查是否已存在（任意分片查一下）
        try {
            Long cnt = new JdbcTemplate(userShardDataSources[0]).queryForObject(
                    "SELECT COUNT(*) FROM sys_user WHERE id >= " + BASE_STUDENT_ID, Long.class);
            if (cnt != null && cnt > 0) {
                log.info("[UserGen] 用户分片已存在数据，跳过生成");
                long total = 0;
                for (DataSource ds : userShardDataSources) {
                    Long c = new JdbcTemplate(ds).queryForObject(
                            "SELECT COUNT(*) FROM sys_user WHERE id >= " + BASE_STUDENT_ID, Long.class);
                    if (c != null) total += c;
                }
                userInserted.set(total);
                return;
            }
        } catch (Exception ignored) {}

        log.info("[UserGen] 开始并行写入 {} 条学生账号到 {} 个分片...", TOTAL_STUDENTS, numShards);
        ExecutorService pool = Executors.newFixedThreadPool(numShards);
        List<Future<Long>> futures = new ArrayList<>(numShards);

        for (int s = 0; s < numShards; s++) {
            final int shardIdx = s;
            final List<long[]> ids = shardIds[s];
            final DataSource ds = userShardDataSources[s];
            futures.add(pool.submit(() -> {
                try (Connection conn = ds.getConnection()) {
                    conn.setAutoCommit(false);
                    try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                        int batchCnt = 0;
                        for (long[] entry : ids) {
                            long id = entry[0];
                            int  i  = (int) entry[1];
                            ps.setLong(1, id);
                            ps.setString(2, "stu_" + id);
                            ps.setString(3, PWD_HASH);
                            ps.setString(4, "STUDENT");
                            ps.setString(5, "学生" + id);
                            ps.setString(6, String.format("1101012000%06d%04d", i % 1000000, i % 10000));
                            ps.setString(7, String.format("139%08d", i % 100000000));
                            ps.setString(8, GENDERS[i % 2]);
                            ps.addBatch();
                            if (++batchCnt % BATCH_SIZE == 0) {
                                ps.executeBatch();
                                conn.commit();
                                userInserted.addAndGet(BATCH_SIZE);
                            }
                        }
                        ps.executeBatch();
                        conn.commit();
                        int rem = ids.size() % BATCH_SIZE;
                        if (rem > 0) userInserted.addAndGet(rem);
                    }
                }
                log.info("[UserGen] 分片[{}] 完成, 共 {} 条", shardIdx, ids.size());
                return (long) ids.size();
            }));
        }
        pool.shutdown();
        for (Future<Long> f : futures) {
            try { f.get(); } catch (Exception e) { log.error("[UserGen] 分片写入失败", e); }
        }
        log.info("[UserGen] 完成，共写入 {} 条", userInserted.get());
        overviewCountRepo.refreshUserCounts(userRepo.countAll(), userRepo.countByRole("STUDENT"));
    }

    // ─────────────────────────────────────────────────────
    //  成绩 2000万 生成
    // ─────────────────────────────────────────────────────

    public void generateScores() {
        ensureBenchmarkMetadata();
        int numShards = ShardDataSourceConfig.NUM_SHARDS;
        ShardRouter router = scoreRepo.getRouter();
        Map<Long, CourseMeta> courseMap = loadCourseMetaMap();
        Map<Long, PlanMeta> planMap = loadPlanMetaMap();
        ExecutorService pool = Executors.newFixedThreadPool(numShards);

        // 按分片分组学生
        List<List<Long>> shardStudents = new ArrayList<>();
        for (int i = 0; i < numShards; i++) shardStudents.add(new ArrayList<>());
        for (int i = 0; i < TOTAL_STUDENTS; i++) {
            long sid = BASE_STUDENT_ID + i;
            shardStudents.get(router.route(sid)).add(sid);
        }

        List<Future<Long>> futures = new ArrayList<>();
        for (int s = 0; s < numShards; s++) {
            final int shardIdx = s;
            final List<Long> students = shardStudents.get(s);
            futures.add(pool.submit(() -> generateScoresShard(shardIdx, students, planMap, courseMap)));
        }

        long total = 0;
        for (Future<Long> f : futures) {
            try { total += f.get(); } catch (Exception e) { log.error("Score 生成出错", e); }
        }
        pool.shutdown();
        Map<String, Object> scoreStats = scoreRepo.globalStats();
        overviewCountRepo.refreshScoreCounts(
                toLong(scoreStats.get("totalCount")),
                toLong(scoreStats.get("passCount")));
        statisticsService.refreshCoursePassRateCache();
        log.info("Score 生成完成，共写入 {} 条", total);
    }

    private long generateScoresShard(int shardIdx, List<Long> students,
                                     Map<Long, PlanMeta> planMap,
                                     Map<Long, CourseMeta> courseMap) {
        JdbcTemplate tpl = scoreRepo.getTemplate(shardIdx);
        long written = 0;
        Random rnd = new Random(shardIdx);
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        // 开启手动事务以提升批量写入速度
        tpl.execute("BEGIN");
        try {
            for (long studentId : students) {
                String studentName = benchmarkStudentName(studentId);
                for (int planId = 1; planId <= SCORES_PER_STU; planId++) {
                    PlanMeta plan = planMap.get((long) planId);
                    if (plan == null) continue;
                    CourseMeta course = courseMap.get(plan.courseId);
                    if (course == null) continue;

                    int pick = rnd.nextInt(100);
                    double score;
                    String status;
                    if (pick < 5) {
                        score = 0.0;
                        status = "ABSENT";
                    } else if (pick < 30) {
                        score = 40 + rnd.nextInt(20);
                        status = "FAIL";
                    } else {
                        score = 60 + rnd.nextInt(41);
                        status = "PASS";
                    }

                    batch.add(new Object[]{
                            studentId,
                            plan.courseId,
                            plan.planId,
                            plan.examYear,
                            plan.examTerm,
                            score,
                            status,
                            plan.examDate,
                            studentName,
                            course.courseCode,
                            course.courseName
                    });

                    if (batch.size() >= BATCH_SIZE) {
                        scoreRepo.batchInsert(shardIdx, batch);
                        long cnt = scoreInserted.addAndGet(batch.size());
                        written += batch.size();
                        batch.clear();
                        // 每 10 万条提交一次事务（减少 WAL 文件膨胀）
                        if (cnt % 100_000 == 0) {
                            tpl.execute("COMMIT");
                            tpl.execute("BEGIN");
                            log.info("[Score Shard {}] 已写入 {} 条 (总进度 {})", shardIdx, written, cnt);
                        }
                    }
                }
            }
            if (!batch.isEmpty()) {
                scoreRepo.batchInsert(shardIdx, batch);
                scoreInserted.addAndGet(batch.size());
                written += batch.size();
                batch.clear();
            }
            tpl.execute("COMMIT");
        } catch (Exception e) {
            tpl.execute("ROLLBACK");
            log.error("[Score Shard {}] 写入失败，已回滚", shardIdx, e);
            throw new RuntimeException(e);
        }
        return written;
    }

    // ─────────────────────────────────────────────────────
    //  报名 2000万 生成
    // ─────────────────────────────────────────────────────

    public void generateRegistrations() {
        ensureBenchmarkMetadata();
        int numShards = ShardDataSourceConfig.NUM_SHARDS;
        ShardRouter router = regRepo.getRouter();
        Map<Long, CourseMeta> courseMap = loadCourseMetaMap();
        Map<Long, PlanMeta> planMap = loadPlanMetaMap();
        ExecutorService pool = Executors.newFixedThreadPool(numShards);

        List<List<Long>> shardStudents = new ArrayList<>();
        for (int i = 0; i < numShards; i++) shardStudents.add(new ArrayList<>());
        for (int i = 0; i < TOTAL_STUDENTS; i++) {
            long sid = BASE_STUDENT_ID + i;
            shardStudents.get(router.route(sid)).add(sid);
        }

        List<Future<Long>> futures = new ArrayList<>();
        for (int s = 0; s < numShards; s++) {
            final int shardIdx = s;
            final List<Long> students = shardStudents.get(s);
            futures.add(pool.submit(() -> generateRegShard(shardIdx, students, planMap, courseMap)));
        }

        long total = 0;
        for (Future<Long> f : futures) {
            try { total += f.get(); } catch (Exception e) { log.error("Reg 生成出错", e); }
        }
        pool.shutdown();
        syncRegisteredCount();
        Map<String, Object> regStats = regRepo.globalStats();
        overviewCountRepo.refreshRegistrationCounts(
            toLong(regStats.get("totalCount")),
            toLong(regStats.get("approvedCount")));
        statisticsService.refreshRegistrationTrendCache();
        log.info("Registration 生成完成，共写入 {} 条", total);
    }

    private long generateRegShard(int shardIdx, List<Long> students,
                                  Map<Long, PlanMeta> planMap,
                                  Map<Long, CourseMeta> courseMap) {
        JdbcTemplate tpl = regRepo.getTemplate(shardIdx);
        long written = 0;
        Random rnd = new Random(shardIdx + 100);
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        tpl.execute("BEGIN");
        try {
            for (long studentId : students) {
                String studentName = benchmarkStudentName(studentId);
                String studentIdCard = benchmarkStudentIdCard(studentId);
                for (int p = 1; p <= REGS_PER_STU; p++) {
                    long planId = (long) p;
                    PlanMeta plan = planMap.get(planId);
                    if (plan == null) continue;
                    CourseMeta course = courseMap.get(plan.courseId);
                    if (course == null) continue;
                    // 报名编号：shard前缀 + studentId + planId，保证全局唯一
                    String regNo = "R" + shardIdx + "-" + studentId + "-" + planId;
                    String payStatus = PAY_STATUS[rnd.nextInt(2)];
                    String status    = REG_STATUS[rnd.nextInt(3)];
                        String ticketNo  = buildTicketNo(plan, studentId);
                    batch.add(new Object[]{
                            studentId,
                            planId,
                            regNo,
                            ticketNo,
                            payStatus,
                            status,
                            studentName,
                            studentIdCard,
                            plan.planCode,
                            plan.planName,
                            plan.courseId,
                            course.courseCode,
                            course.courseName,
                            plan.examYear,
                            plan.examTerm,
                            plan.examDate,
                            plan.examLocation,
                            plan.startTime,
                            plan.endTime
                    });

                    if (batch.size() >= BATCH_SIZE) {
                        regRepo.batchInsert(shardIdx, batch);
                        long cnt = regInserted.addAndGet(batch.size());
                        written += batch.size();
                        batch.clear();
                        if (cnt % 100_000 == 0) {
                            tpl.execute("COMMIT");
                            tpl.execute("BEGIN");
                            log.info("[Reg Shard {}] 已写入 {} 条 (总进度 {})", shardIdx, written, cnt);
                        }
                    }
                }
            }
            if (!batch.isEmpty()) {
                regRepo.batchInsert(shardIdx, batch);
                regInserted.addAndGet(batch.size());
                written += batch.size();
            }
            tpl.execute("COMMIT");
        } catch (Exception e) {
            tpl.execute("ROLLBACK");
            log.error("[Reg Shard {}] 写入失败，已回滚", shardIdx, e);
            throw new RuntimeException(e);
        }
        return written;
    }

    // ─────────────────────────────────────────────────────
    //  全量生成入口（异步）
    // ─────────────────────────────────────────────────────

    /** 异步启动全量 4000万 数据生成（Score + Registration 顺序生成） */
    public void startFullGeneration() {
        if (running) throw new IllegalStateException("数据生成任务正在运行中");
        running     = true;
        lastError   = null;
        startTimeMs = System.currentTimeMillis();
        scoreInserted.set(0);
        regInserted.set(0);

        Thread t = new Thread(() -> {
            try {
                log.info("===== 开始生成 Score 2000万 =====");
                generateScores();
                log.info("===== 开始生成 Registration 2000万 =====");
                generateRegistrations();
                long elapsed = (System.currentTimeMillis() - startTimeMs) / 1000;
                log.info("===== 全量生成完成，耗时 {}s =====", elapsed);
            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("全量生成出错", e);
            } finally {
                running = false;
            }
        }, "data-gen-main");
        t.setDaemon(true);
        t.start();
    }

    /** 清空所有分片数据（用于重置后重新生成） */
    public void truncateAll() {
        for (int i = 0; i < ShardDataSourceConfig.NUM_SHARDS; i++) {
            scoreRepo.getTemplate(i).execute("DELETE FROM sys_score");
            regRepo.getTemplate(i).execute("DELETE FROM sys_registration");
        }
        scoreInserted.set(0);
        regInserted.set(0);
        log.info("所有分片数据已清空");
        overviewCountRepo.refreshScoreCounts(0L, 0L);
        overviewCountRepo.refreshRegistrationCounts(0L, 0L);
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private Map<Long, CourseMeta> loadCourseMetaMap() {
        JdbcTemplate tpl = new JdbcTemplate(dataSource);
        Map<Long, CourseMeta> map = new LinkedHashMap<>();
        tpl.query("SELECT id,course_code,course_name FROM sys_course WHERE deleted=0 AND id<=? ORDER BY id",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> map.put(rs.getLong("id"), new CourseMeta(
                rs.getLong("id"),
                rs.getString("course_code"),
                rs.getString("course_name"))),
                BENCHMARK_DIM);
        return map;
    }

    private Map<Long, PlanMeta> loadPlanMetaMap() {
        JdbcTemplate tpl = new JdbcTemplate(dataSource);
        Map<Long, PlanMeta> map = new LinkedHashMap<>();
        tpl.query("SELECT id,plan_code,plan_name,course_id,exam_year,exam_term,exam_date,location,start_time,end_time FROM sys_exam_plan WHERE deleted=0 AND id<=? ORDER BY id",
            (org.springframework.jdbc.core.RowCallbackHandler) rs -> map.put(rs.getLong("id"), new PlanMeta(
                rs.getLong("id"),
                rs.getString("plan_code"),
                rs.getString("plan_name"),
                rs.getLong("course_id"),
                rs.getInt("exam_year"),
                rs.getString("exam_term"),
                rs.getString("exam_date"),
                rs.getString("location"),
                rs.getString("start_time"),
                rs.getString("end_time"))),
                BENCHMARK_DIM);
        return map;
    }

    private void syncRegisteredCount() {
        JdbcTemplate tpl = new JdbcTemplate(dataSource);
        Map<Long, Long> counts = regRepo.countByPlanId();
        tpl.update("UPDATE sys_exam_plan SET registered_count=0 WHERE id<=?", BENCHMARK_DIM);
        List<Object[]> rows = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : counts.entrySet()) {
            if (entry.getKey() != null && entry.getKey() <= BENCHMARK_DIM) {
                rows.add(new Object[]{entry.getValue(), entry.getKey()});
            }
        }
        if (!rows.isEmpty()) {
            tpl.batchUpdate("UPDATE sys_exam_plan SET registered_count=? WHERE id=?", rows);
        }
    }

    private String benchmarkStudentName(long studentId) {
        return "学生" + studentId;
    }

    private String benchmarkStudentIdCard(long studentId) {
        long offset = Math.max(0L, studentId - BASE_STUDENT_ID);
        return String.format("1101012000%06d%04d", offset % 1_000_000L, offset % 10_000L);
    }

    private String benchmarkCourseCode(int courseId) {
        return String.format("SIM%03d", courseId);
    }

    private String benchmarkCourseName(int courseId) {
        return String.format("模拟课程%03d", courseId);
    }

    private String benchmarkPlanCode(int planId, int examYear, String examTerm) {
        return String.format("PLAN%d%s-%03d", examYear, "上".equals(examTerm) ? "01" : "02", planId);
    }

    private String benchmarkPlanName(int planId, int examYear, String examTerm) {
        return String.format("%d%s 模拟考试计划%03d", examYear, examTerm, planId);
    }

    private String buildTicketNo(PlanMeta plan, long studentId) {
        return "AT" + plan.examYear + ("上".equals(plan.examTerm) ? "01" : "02")
                + String.format("%04d", plan.planId)
                + String.format("%04d", studentId % 10_000);
    }

    private static final class CourseMeta {
        private final long courseId;
        private final String courseCode;
        private final String courseName;

        private CourseMeta(long courseId, String courseCode, String courseName) {
            this.courseId = courseId;
            this.courseCode = courseCode;
            this.courseName = courseName;
        }
    }

    private static final class PlanMeta {
        private final long planId;
        private final String planCode;
        private final String planName;
        private final long courseId;
        private final int examYear;
        private final String examTerm;
        private final String examDate;
        private final String examLocation;
        private final String startTime;
        private final String endTime;

        private PlanMeta(long planId, String planCode, String planName, long courseId,
                         int examYear, String examTerm, String examDate,
                         String examLocation, String startTime, String endTime) {
            this.planId = planId;
            this.planCode = planCode;
            this.planName = planName;
            this.courseId = courseId;
            this.examYear = examYear;
            this.examTerm = examTerm;
            this.examDate = examDate;
            this.examLocation = examLocation;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
