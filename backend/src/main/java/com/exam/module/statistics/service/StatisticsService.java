package com.exam.module.statistics.service;

import com.exam.module.statistics.dto.ChartItem;
import com.exam.module.statistics.dto.OverviewVO;
import com.exam.module.statistics.mapper.StatisticsMapper;
import com.exam.shard.UserShardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);
    /** 统计缓存有效期：60 秒 */
    private static final long CACHE_TTL_MS = 60_000L;

    @Autowired
    private StatisticsMapper statMapper;

    @Autowired
    private UserShardRepository userRepo;

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
        OverviewVO vo = new OverviewVO();
        vo.setUserCount(userRepo.countAll());
        vo.setStudentCount(userRepo.countByRole("STUDENT"));
        vo.setMajorCount(statMapper.countMajor());
        vo.setCourseCount(statMapper.countCourse());
        vo.setPlanCount(statMapper.countPlan());
        vo.setPublishedPlanCount(statMapper.countPublishedPlan());
        vo.setRegistrationCount(statMapper.countRegistration());
        vo.setApprovedCount(statMapper.countApproved());
        vo.setScoreCount(statMapper.countScore());
        Long total = vo.getScoreCount();
        Long pass = statMapper.countPassScore();
        vo.setPassRate(total == 0 ? 0.0 : Math.round(pass * 1000.0 / total) / 10.0);
        return vo;
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
