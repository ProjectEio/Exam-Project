package com.exam.module.statistics.service;

import com.exam.module.statistics.dto.ChartItem;
import com.exam.module.statistics.dto.OverviewVO;
import com.exam.module.statistics.mapper.StatisticsMapper;
import com.exam.shard.RegistrationShardRepository;
import com.exam.shard.ScoreShardRepository;
import com.exam.shard.UserShardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);
    /** 统计缓存有效期：60 秒 */
    private static final long CACHE_TTL_MS = 60_000L;

    @Autowired private StatisticsMapper            statMapper;
    @Autowired private UserShardRepository         userRepo;
    @Autowired private ScoreShardRepository        scoreRepo;
    @Autowired private RegistrationShardRepository regRepo;

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
        cachedOverview.set(vo);
        cacheExpiry = now + CACHE_TTL_MS;
        return vo;
    }

    /** 实际从分片 + 主库聚合统计数据 */
    private OverviewVO buildOverview() {
        // 成绩分片数据（含 pass数）
        Map<String, Object> scoreStats = scoreRepo.globalStats();
        long scoreTotal = toLong(scoreStats.get("totalCount"));
        long scorePass  = toLong(scoreStats.get("passCount"));
        // 报名分片数据（含 approved）
        Map<String, Object> regStats = regRepo.globalStats();
        long regTotal    = toLong(regStats.get("totalCount"));
        long regApproved = toLong(regStats.get("approvedCount"));

        OverviewVO vo = new OverviewVO();
        vo.setUserCount(userRepo.countAll());
        vo.setStudentCount(userRepo.countByRole("STUDENT"));
        vo.setMajorCount(statMapper.countMajor());
        vo.setCourseCount(statMapper.countCourse());
        vo.setPlanCount(statMapper.countPlan());
        vo.setPublishedPlanCount(statMapper.countPublishedPlan());
        vo.setRegistrationCount(regTotal);
        vo.setApprovedCount(regApproved);
        vo.setScoreCount(scoreTotal);
        vo.setPassRate(scoreTotal == 0 ? 0.0 : Math.round(scorePass * 1000.0 / scoreTotal) / 10.0);
        return vo;
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    public List<ChartItem> registrationTrend() {
        return statMapper.registrationTrend();
    }

    public List<ChartItem> coursePassRate() {
        return statMapper.coursePassRate();
    }

    public List<ChartItem> majorDistribution() {
        return statMapper.majorDistribution();
    }

    public List<java.util.Map<String, Object>> scoreStatusDist() {
        return statMapper.scoreStatusDist();
    }
}
