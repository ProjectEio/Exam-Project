package com.exam.benchmark;

import com.exam.shard.RegistrationShardRepository;
import com.exam.shard.ScoreShardRepository;
import com.exam.shard.ShardDataSourceConfig;
import com.exam.shard.ShardRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

    private static final String[] STATUSES   = {"PASS", "FAIL", "ABSENT"};
    private static final String[] TERMS      = {"上", "下"};
    private static final String[] PAY_STATUS = {"PAID", "UNPAID"};
    private static final String[] REG_STATUS = {"APPROVED", "PENDING", "REJECTED"};

    @Autowired private ScoreShardRepository        scoreRepo;
    @Autowired private RegistrationShardRepository regRepo;

    // ── 进度追踪 ──────────────────────────────────────────
    public final AtomicLong scoreInserted = new AtomicLong(0);
    public final AtomicLong regInserted   = new AtomicLong(0);
    public volatile boolean running       = false;
    public volatile String  lastError     = null;
    public volatile long    startTimeMs   = 0;

    // ─────────────────────────────────────────────────────
    //  成绩 2000万 生成
    // ─────────────────────────────────────────────────────

    public void generateScores() {
        int numShards = ShardDataSourceConfig.NUM_SHARDS;
        ShardRouter router = scoreRepo.getRouter();
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
            futures.add(pool.submit(() -> generateScoresShard(shardIdx, students)));
        }

        long total = 0;
        for (Future<Long> f : futures) {
            try { total += f.get(); } catch (Exception e) { log.error("Score 生成出错", e); }
        }
        pool.shutdown();
        log.info("Score 生成完成，共写入 {} 条", total);
    }

    private long generateScoresShard(int shardIdx, List<Long> students) {
        JdbcTemplate tpl = scoreRepo.getTemplate(shardIdx);
        long written = 0;
        Random rnd = new Random(shardIdx);
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        // 开启手动事务以提升批量写入速度
        tpl.execute("BEGIN");
        try {
            for (long studentId : students) {
                for (int c = 1; c <= SCORES_PER_STU; c++) {
                    int year  = 2020 + (c % 5);
                    String term = TERMS[c % 2];
                    double score = 40 + rnd.nextInt(61);     // 40~100
                    String status = score >= 60 ? "PASS" : (score < 10 ? "ABSENT" : "FAIL");
                    batch.add(new Object[]{studentId, (long) c, year, term, score, status});

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
        int numShards = ShardDataSourceConfig.NUM_SHARDS;
        ShardRouter router = regRepo.getRouter();
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
            futures.add(pool.submit(() -> generateRegShard(shardIdx, students)));
        }

        long total = 0;
        for (Future<Long> f : futures) {
            try { total += f.get(); } catch (Exception e) { log.error("Reg 生成出错", e); }
        }
        pool.shutdown();
        log.info("Registration 生成完成，共写入 {} 条", total);
    }

    private long generateRegShard(int shardIdx, List<Long> students) {
        JdbcTemplate tpl = regRepo.getTemplate(shardIdx);
        long written = 0;
        Random rnd = new Random(shardIdx + 100);
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        tpl.execute("BEGIN");
        try {
            for (long studentId : students) {
                for (int p = 1; p <= REGS_PER_STU; p++) {
                    long planId = (long) p;
                    // 报名编号：shard前缀 + studentId + planId，保证全局唯一
                    String regNo = "R" + shardIdx + "-" + studentId + "-" + planId;
                    String payStatus = PAY_STATUS[rnd.nextInt(2)];
                    String status    = REG_STATUS[rnd.nextInt(3)];
                    batch.add(new Object[]{studentId, planId, regNo, payStatus, status});

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
    }
}
