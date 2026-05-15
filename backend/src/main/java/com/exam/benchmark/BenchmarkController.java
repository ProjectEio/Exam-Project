package com.exam.benchmark;

import com.exam.cache.CacheStats;
import com.exam.cache.MemoryCacheManager;
import com.exam.common.PageQuery;
import com.exam.common.PageResult;
import com.exam.module.course.service.CourseService;
import com.exam.module.major.service.MajorService;
import com.exam.module.plan.dto.PlanQueryDTO;
import com.exam.module.plan.service.ExamPlanService;
import com.exam.module.registration.dto.RegistrationQueryDTO;
import com.exam.module.score.dto.ScoreQueryDTO;
import com.exam.module.user.dto.UserQueryDTO;
import com.exam.shard.RegistrationShardRepository;
import com.exam.shard.ScoreShardRepository;
import com.exam.shard.ShardDataSourceConfig;
import com.exam.shard.ShardRouter;
import com.exam.shard.UserShardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能基准测试控制器
 * ─────────────────────────────────────────────────────────
 * 接口列表：
 *
 *  数据生成
 *    POST /api/benchmark/generate/start       启动 2000万 数据生成（异步）
 *    POST /api/benchmark/generate/truncate    清空分片数据
 *    GET  /api/benchmark/generate/progress    查看生成进度
 *
 *  性能测试
 *    GET  /api/benchmark/test/cache           缓存读写吞吐量测试
 *    GET  /api/benchmark/test/shard-query     分片查询性能测试
 *    GET  /api/benchmark/test/cross-shard     跨分片聚合性能测试
 *    GET  /api/benchmark/test/concurrent      并发压测（模拟多用户）
 *
 *  统计信息
 *    GET  /api/benchmark/stats/cache          缓存命中率统计
 *    GET  /api/benchmark/stats/shard          分片数据量统计
 */
@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    @Autowired private MemoryCacheManager          cacheManager;
    @Autowired private ScoreShardRepository        scoreRepo;
    @Autowired private RegistrationShardRepository regRepo;
    @Autowired private UserShardRepository         userRepo;
    @Autowired private DataGeneratorService        generator;
    @Autowired private MajorService                majorService;
    @Autowired private CourseService               courseService;
    @Autowired private ExamPlanService             planService;

    // ════════════════════════════════════════════
    //  数据生成接口
    // ════════════════════════════════════════════

    @PostMapping("/generate/start")
    public Map<String, Object> startGenerate() {
        generator.startFullGeneration();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status",  "started");
        r.put("message", "开始异步生成 Score+Registration 各2000万条，请通过 /progress 查询进度");
        return r;
    }

    @PostMapping("/generate/truncate")
    public Map<String, Object> truncate() {
        generator.truncateAll();
        return Map.of("status", "ok", "message", "所有分片数据已清空");
    }

    @GetMapping("/generate/progress")
    public Map<String, Object> progress() {
        long scoreTotal = 20_000_000L;
        long regTotal   = 20_000_000L;
        long scoreNow   = generator.getScoreInserted();
        long regNow     = generator.getRegInserted();
        long elapsed    = generator.getStartTimeMs() > 0
                ? (System.currentTimeMillis() - generator.getStartTimeMs()) / 1000 : 0;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("running",          generator.isRunning());
        r.put("elapsedSeconds",   elapsed);
        r.put("scoreInserted",    scoreNow);
        r.put("scoreTarget",      scoreTotal);
        r.put("scoreProgress",    String.format("%.2f%%", 100.0 * scoreNow / scoreTotal));
        r.put("regInserted",      regNow);
        r.put("regTarget",        regTotal);
        r.put("regProgress",      String.format("%.2f%%", 100.0 * regNow / regTotal));
        r.put("totalInserted",    scoreNow + regNow);
        r.put("insertRate",       elapsed > 0 ? (scoreNow + regNow) / elapsed + " 条/秒" : "N/A");
        if (generator.getLastError() != null) r.put("lastError", generator.getLastError());
        return r;
    }

    // ════════════════════════════════════════════
    //  性能测试接口
    // ════════════════════════════════════════════

    /**
     * 缓存读写吞吐量测试
     * 连续写入 N 条，再读取（命中 + 缺失），统计 QPS 和命中率
     */
    @GetMapping("/test/cache")
    public Map<String, Object> testCache(@RequestParam(defaultValue = "100000") int rounds) {
        String cacheName = MemoryCacheManager.SCORE_LIST_CACHE;
        Random rnd = new Random();

        // ── 写测试 ──
        long writeStart = System.nanoTime();
        for (int i = 0; i < rounds; i++) {
            String key = "bench:score:" + i;
            cacheManager.put(cacheName, key, List.of("score_" + i));
        }
        long writeNs = System.nanoTime() - writeStart;

        // ── 读测试（100% 命中） ──
        long readHitStart = System.nanoTime();
        long hits = 0;
        for (int i = 0; i < rounds; i++) {
            if (cacheManager.get(cacheName, "bench:score:" + i) != null) hits++;
        }
        long readHitNs = System.nanoTime() - readHitStart;

        // ── 读测试（100% 缺失） ──
        long readMissStart = System.nanoTime();
        long misses = 0;
        for (int i = 0; i < rounds; i++) {
            if (cacheManager.get(cacheName, "bench:miss:" + (rounds + i)) == null) misses++;
        }
        long readMissNs = System.nanoTime() - readMissStart;

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rounds",              rounds);
        r.put("writeQps",            qps(rounds, writeNs));
        r.put("writeAvgNs",          writeNs / rounds);
        r.put("readHitQps",          qps(rounds, readHitNs));
        r.put("readHitAvgNs",        readHitNs / rounds);
        r.put("readMissQps",         qps(rounds, readMissNs));
        r.put("readMissAvgNs",       readMissNs / rounds);
        r.put("hitCount",            hits);
        r.put("missCount",           misses);
        r.put("cacheSize",           cacheManager.allStats().get(cacheName).size());
        return r;
    }

    /**
     * 分片查询性能测试
     * 随机抽取 N 个 studentId，测试单分片查询延迟
     */
    @GetMapping("/test/shard-query")
    public Map<String, Object> testShardQuery(@RequestParam(defaultValue = "1000") int rounds) {
        long baseId = 1_000_000L;
        Random rnd = new Random();

        long totalNs = 0;
        long cacheHit = 0;
        String cacheName = MemoryCacheManager.SCORE_LIST_CACHE;

        for (int i = 0; i < rounds; i++) {
            long studentId = baseId + rnd.nextInt(100_000);
            String cacheKey = "score:student:" + studentId;

            long t0 = System.nanoTime();
            // 先查缓存
            List<?> cached = cacheManager.get(cacheName, cacheKey);
            if (cached != null) {
                cacheHit++;
            } else {
                // 缓存未命中 → 查分片 DB
                List<Map<String, Object>> rows = scoreRepo.findByStudentId(studentId);
                cacheManager.put(cacheName, cacheKey, rows);
            }
            totalNs += System.nanoTime() - t0;
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rounds",        rounds);
        r.put("totalMs",       totalNs / 1_000_000);
        r.put("avgNs",         totalNs / rounds);
        r.put("avgMs",         totalNs / rounds / 1_000_000.0);
        r.put("qps",           qps(rounds, totalNs));
        r.put("cacheHits",     cacheHit);
        r.put("cacheHitRate",  String.format("%.2f%%", 100.0 * cacheHit / rounds));
        r.put("dbQueries",     rounds - cacheHit);
        return r;
    }

    /**
     * 跨分片聚合性能测试
     * 测试 COUNT + AVG + 分片并行聚合的延迟
     */
    @GetMapping("/test/cross-shard")
    public Map<String, Object> testCrossShard() {
        Map<String, Object> r = new LinkedHashMap<>();

        // COUNT ALL
        long t0 = System.nanoTime();
        long scoreCount = scoreRepo.countAll();
        long scoreCountNs = System.nanoTime() - t0;

        long t1 = System.nanoTime();
        long regCount = regRepo.countAll();
        long regCountNs = System.nanoTime() - t1;

        // 全局统计（avg, passRate）
        long t2 = System.nanoTime();
        Map<String, Object> scoreStats = scoreRepo.globalStats();
        long statsNs = System.nanoTime() - t2;

        long t3 = System.nanoTime();
        Map<String, Object> regStats = regRepo.globalStats();
        long regStatsNs = System.nanoTime() - t3;

        r.put("scoreCount",        scoreCount);
        r.put("scoreCountTimeMs",  scoreCountNs / 1_000_000.0);
        r.put("regCount",          regCount);
        r.put("regCountTimeMs",    regCountNs / 1_000_000.0);
        r.put("scoreStats",        scoreStats);
        r.put("scoreStatsTimeMs",  statsNs / 1_000_000.0);
        r.put("regStats",          regStats);
        r.put("regStatsTimeMs",    regStatsNs / 1_000_000.0);
        r.put("numShards",         ShardDataSourceConfig.NUM_SHARDS);
        return r;
    }

    /**
     * 并发压测：模拟 N 个并发用户同时查询（缓存+分片混合）
     */
    @GetMapping("/test/concurrent")
    public Map<String, Object> testConcurrent(
            @RequestParam(defaultValue = "50")   int threads,
            @RequestParam(defaultValue = "200")  int reqPerThread) throws InterruptedException {

        long baseId    = 1_000_000L;
        int  totalReqs = threads * reqPerThread;
        String cacheName = MemoryCacheManager.SCORE_LIST_CACHE;

        AtomicLong cacheHits  = new AtomicLong();
        AtomicLong dbQueries  = new AtomicLong();
        AtomicLong errors     = new AtomicLong();
        AtomicLong totalNanos = new AtomicLong();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        long wallStart = System.currentTimeMillis();

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                Random rnd = new Random(threadId);
                try {
                    for (int i = 0; i < reqPerThread; i++) {
                        long studentId = baseId + rnd.nextInt(100_000);
                        String cacheKey = "score:student:" + studentId;
                        long t0 = System.nanoTime();
                        try {
                            List<?> cached = cacheManager.get(cacheName, cacheKey);
                            if (cached != null) {
                                cacheHits.incrementAndGet();
                            } else {
                                List<Map<String, Object>> rows = scoreRepo.findByStudentId(studentId);
                                cacheManager.put(cacheName, cacheKey, rows);
                                dbQueries.incrementAndGet();
                            }
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        } finally {
                            totalNanos.addAndGet(System.nanoTime() - t0);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(5, TimeUnit.MINUTES);
        pool.shutdown();

        long wallMs = System.currentTimeMillis() - wallStart;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("threads",       threads);
        r.put("reqPerThread",  reqPerThread);
        r.put("totalRequests", totalReqs);
        r.put("wallTimeMs",    wallMs);
        r.put("throughputQps", wallMs > 0 ? totalReqs * 1000L / wallMs : 0);
        r.put("avgLatencyMs",  totalReqs > 0 ? totalNanos.get() / totalReqs / 1_000_000.0 : 0);
        r.put("cacheHits",     cacheHits.get());
        r.put("dbQueries",     dbQueries.get());
        r.put("errors",        errors.get());
        r.put("cacheHitRate",  String.format("%.2f%%", 100.0 * cacheHits.get() / totalReqs));
        return r;
    }

    @GetMapping("/test/page-profile")
    public Map<String, Object> testPageProfile(@RequestParam(defaultValue = "20") long size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("size", size);
        result.put("user", profilePages(userRepo.countAll(), size, current -> {
            UserQueryDTO q = new UserQueryDTO();
            q.setCurrent(current);
            q.setSize(size);
            return userRepo.page(q);
        }));
        result.put("score", profilePages(scoreRepo.countAll(), size, current -> {
            ScoreQueryDTO q = new ScoreQueryDTO();
            q.setCurrent(current);
            q.setSize(size);
            return scoreRepo.page(q);
        }));
        result.put("registration", profilePages(regRepo.countAll(), size, current -> {
            RegistrationQueryDTO q = new RegistrationQueryDTO();
            q.setCurrent(current);
            q.setSize(size);
            return regRepo.page(q);
        }));
        result.put("major", profilePages(totalOf(majorService.page(pageQuery(1L, size))), size, current ->
                majorService.page(pageQuery(current, size))));
        result.put("course", profilePages(totalOf(courseService.page(pageQuery(1L, size))), size, current ->
                courseService.page(pageQuery(current, size))));
        result.put("plan", profilePages(totalOf(planService.page(planQuery(1L, size))), size, current ->
                planService.page(planQuery(current, size))));
        return result;
    }

    private Map<String, Object> profilePages(long total, long size, java.util.function.LongFunction<PageResult<?>> fetcher) {
        long totalPages = Math.max(1L, (total + size - 1) / size);
        long middle = Math.max(1L, (totalPages + 1) / 2);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("pages", totalPages);
        result.put("first", measurePage(1L, fetcher));
        result.put("middle", measurePage(middle, fetcher));
        result.put("last", measurePage(totalPages, fetcher));
        return result;
    }

    private Map<String, Object> measurePage(long current, java.util.function.LongFunction<PageResult<?>> fetcher) {
        long start = System.nanoTime();
        PageResult<?> page = fetcher.apply(current);
        long elapsedNs = System.nanoTime() - start;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", current);
        result.put("records", page.getRecords() == null ? 0 : page.getRecords().size());
        result.put("total", page.getTotal());
        result.put("elapsedMs", elapsedNs / 1_000_000.0);
        return result;
    }

    private PageQuery pageQuery(Long current, long size) {
        PageQuery q = new PageQuery();
        q.setCurrent(current);
        q.setSize(size);
        return q;
    }

    private PlanQueryDTO planQuery(Long current, long size) {
        PlanQueryDTO q = new PlanQueryDTO();
        q.setCurrent(current);
        q.setSize(size);
        return q;
    }

    private long totalOf(PageResult<?> page) {
        return page == null ? 0L : page.getTotal();
    }

    // ════════════════════════════════════════════
    //  统计接口
    // ════════════════════════════════════════════

    @GetMapping("/stats/cache")
    public Map<String, Object> cacheStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("globalHitRate", String.format("%.2f%%", cacheManager.globalHitRate() * 100));
        Map<String, CacheStats> stats = cacheManager.allStats();
        stats.forEach((name, cs) -> r.put(name, Map.of(
                "size",      cs.size(),
                "hitCount",  cs.hitCount(),
                "missCount", cs.missCount(),
                "evictCount",cs.evictCount(),
                "hitRate",   String.format("%.2f%%", cs.hitRate() * 100)
        )));
        return r;
    }

    @GetMapping("/stats/shard")
    public Map<String, Object> shardStats() {
        Map<String, Object> r = new LinkedHashMap<>();
        // 各分片条目数
        List<Map<String, Object>> scoreShards = new ArrayList<>();
        List<Map<String, Object>> regShards   = new ArrayList<>();
        for (int i = 0; i < ShardDataSourceConfig.NUM_SHARDS; i++) {
            Long sc = scoreRepo.getTemplate(i)
                    .queryForObject("SELECT COUNT(*) FROM sys_score WHERE deleted=0", Long.class);
            scoreShards.add(Map.of("shard", i, "count", sc == null ? 0 : sc));

            Long rc = regRepo.getTemplate(i)
                    .queryForObject("SELECT COUNT(*) FROM sys_registration WHERE deleted=0", Long.class);
            regShards.add(Map.of("shard", i, "count", rc == null ? 0 : rc));
        }
        r.put("numShards",     ShardDataSourceConfig.NUM_SHARDS);
        r.put("scoreShards",   scoreShards);
        r.put("regShards",     regShards);
        r.put("scoreTotalAll", scoreRepo.countAll());
        r.put("regTotalAll",   regRepo.countAll());
        return r;
    }

    @PostMapping("/cache/invalidate")
    public Map<String, Object> invalidateCache(@RequestParam(defaultValue = "all") String name) {
        if ("all".equals(name)) {
            cacheManager.invalidateAllCaches();
        } else {
            cacheManager.invalidateAll(name);
        }
        return Map.of("status", "ok", "invalidated", name);
    }

    // ─────────────────────────────────────────────────────
    //  辅助
    // ─────────────────────────────────────────────────────

    /** 纳秒 → QPS */
    private static long qps(long ops, long nanos) {
        if (nanos <= 0) return 0;
        return (long) ((double) ops / nanos * 1_000_000_000L);
    }
}
