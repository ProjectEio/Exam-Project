package com.exam.module.statistics.service;

import com.exam.module.statistics.dto.ChartItem;
import com.exam.module.statistics.dto.OverviewVO;
import com.exam.module.statistics.mapper.StatisticsMapper;
import com.exam.module.statistics.repository.StatisticsChartCacheRepository;
import com.exam.module.statistics.repository.OverviewCountRepository;
import com.exam.module.course.mapper.CourseMapper;
import com.exam.module.plan.mapper.ExamPlanMapper;
import com.exam.module.plan.entity.ExamPlan;
import com.exam.shard.RegistrationShardRepository;
import com.exam.shard.ScoreShardRepository;
import com.exam.shard.UserShardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);
    /** 统计缓存有效期：60 秒 */
    private static final long CACHE_TTL_MS = 60_000L;

    @Autowired private StatisticsMapper            statMapper;
    @Autowired private OverviewCountRepository     overviewCountRepo;
    @Autowired private StatisticsChartCacheRepository chartCacheRepo;
    @Autowired private UserShardRepository         userRepo;
    @Autowired private ScoreShardRepository        scoreRepo;
    @Autowired private RegistrationShardRepository regRepo;
    @Autowired private CourseMapper                courseMapper;
    @Autowired private ExamPlanMapper              planMapper;

    /** 缓存概览结果，避免每次请求都 fan-out 8 分片 */
    private final AtomicReference<OverviewVO> cachedOverview = new AtomicReference<>(null);
    private volatile long cacheExpiry = 0;

    /**
     * 启动完成后异步预热统计缓存，首页打开即显示正确数据。
     * 异步执行，不阻塞启动过程。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmCache() {
        Thread t = new Thread(() -> {
            try {
                log.info("[Stats] 启动预热统计缓存...");
                refreshOverviewCounterTable();
                refreshChartCacheTables();
                OverviewVO vo = buildOverview();
                cachedOverview.set(vo);
                cacheExpiry = System.currentTimeMillis() + CACHE_TTL_MS;
                log.info("[Stats] 预热完成 userCount={} studentCount={} scoreCount={}",
                        vo.getUserCount(), vo.getStudentCount(), vo.getScoreCount());
            } catch (Exception e) {
                log.warn("[Stats] 预热失败，将在首次请求时重试", e);
            }
            // 预热后异步触发 WAL checkpoint，把 WAL 合并回主文件以加快后续读取
            Thread ckpt = new Thread(() -> {
                try {
                    log.info("[Stats] 开始 WAL checkpoint...");
                    scoreRepo.checkpointAll();
                    regRepo.checkpointAll();
                    log.info("[Stats] WAL checkpoint 完成");
                } catch (Exception e) {
                    log.warn("[Stats] WAL checkpoint 出错: {}", e.getMessage());
                }
            }, "wal-checkpoint");
            ckpt.setDaemon(true);
            ckpt.start();
        }, "stats-warm");
        t.setDaemon(true);
        t.start();
    }

    public OverviewVO overview() {
        long now = System.currentTimeMillis();
        OverviewVO cached = cachedOverview.get();
        if (cached != null && now < cacheExpiry) return cached;
        // 缓存失效或未预热，同步重建并写入
        OverviewVO vo = buildOverview();
        if (isEmptyOverview(vo)) {
            refreshOverviewCounterTable();
            vo = buildOverview();
        }
        cachedOverview.set(vo);
        cacheExpiry = now + CACHE_TTL_MS;
        return vo;
    }

    /** 直接从总览计数表读取首页数据，避免 cache miss 时 fan-out 分片。 */
    private OverviewVO buildOverview() {
        return overviewCountRepo.loadOverview();
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private void refreshOverviewCounterTable() {
        overviewCountRepo.ensureMetadataSupport();
        overviewCountRepo.refreshUserCounts(userRepo.countAll(), userRepo.countByRole("STUDENT"));

        Map<String, Object> scoreStats = scoreRepo.globalStats();
        overviewCountRepo.refreshScoreCounts(
                toLong(scoreStats.get("totalCount")),
                toLong(scoreStats.get("passCount")));

        Map<String, Object> regStats = regRepo.globalStats();
        overviewCountRepo.refreshRegistrationCounts(
                toLong(regStats.get("totalCount")),
                toLong(regStats.get("approvedCount")));
    }

    private boolean isEmptyOverview(OverviewVO vo) {
        return vo.getUserCount() == null || vo.getUserCount() == 0L;
    }

    public void refreshRegistrationTrendCache() {
        Map<Long, Long> planCounts = regRepo.countByPlanId();
        List<ExamPlan> plans = planMapper.selectList(null);

        Map<String, Long> byTerm = new LinkedHashMap<>();
        plans.stream()
                .sorted(Comparator.comparingInt(ExamPlan::getExamYear)
                        .thenComparing(p -> "上".equals(p.getExamTerm()) ? 1 : 2))
                .forEach(p -> {
                    String label = p.getExamYear() + "-" + p.getExamTerm();
                    long cnt = planCounts.getOrDefault(p.getId(), 0L);
                    byTerm.merge(label, cnt, Long::sum);
                });

        chartCacheRepo.refreshRegistrationTrend(byTerm);
    }

    public void refreshCoursePassRateCache() {
        Map<Long, long[]> stats = scoreRepo.coursePassStats();
        Map<Long, String> nameMap = new HashMap<>();
        courseMapper.selectList(null).forEach(c -> nameMap.put(c.getId(), c.getCourseName()));
        chartCacheRepo.refreshPassRate(stats, nameMap);
    }

    private void refreshChartCacheTables() {
        refreshRegistrationTrendCache();
        refreshCoursePassRateCache();
    }

    /**
     * 报名趋势（按学期）：从报名分片聚合 plan_id→count，
     * 再关联主库 exam_plan 得到学期标签。
     */
    public List<ChartItem> registrationTrend() {
        if (!chartCacheRepo.hasRegistrationTrend()) {
            refreshRegistrationTrendCache();
        }
        return chartCacheRepo.loadRegistrationTrend();
    }

    /**
     * 课程合格率 Top10：从成绩分片聚合 course_id→(total,pass)，
     * 再关联主库 sys_course 得到课程名。
     */
    public List<ChartItem> coursePassRate() {
        if (!chartCacheRepo.hasPassRate()) {
            refreshCoursePassRateCache();
        }
        return chartCacheRepo.loadPassRate();
    }

    /** 专业-计划数分布（主库数据，无需走分片）*/
    public List<ChartItem> majorDistribution() {
        return statMapper.majorDistribution();
    }

    /**
     * 成绩状态分布：从成绩分片聚合，返回与旧接口相同的 List<Map<label,value>>。
     */
    public List<Map<String, Object>> scoreStatusDist() {
        Map<String, Long> dist = scoreRepo.statusDist();
        return dist.entrySet().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("label", e.getKey());
                    m.put("value", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
