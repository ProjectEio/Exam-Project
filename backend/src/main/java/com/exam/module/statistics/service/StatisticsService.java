package com.exam.module.statistics.service;

import com.exam.module.statistics.dto.ChartItem;
import com.exam.module.statistics.dto.OverviewVO;
import com.exam.module.statistics.mapper.StatisticsMapper;
import com.exam.shard.UserShardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatisticsService {

    @Autowired
    private StatisticsMapper statMapper;

    @Autowired
    private UserShardRepository userRepo;

    public OverviewVO overview() {
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
