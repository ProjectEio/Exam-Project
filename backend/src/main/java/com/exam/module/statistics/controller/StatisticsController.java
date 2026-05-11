package com.exam.module.statistics.controller;

import com.exam.common.R;
import com.exam.common.RequireRole;
import com.exam.common.Role;
import com.exam.module.statistics.dto.ChartItem;
import com.exam.module.statistics.dto.OverviewVO;
import com.exam.module.statistics.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "08-统计分析")
@RestController
@RequestMapping("/api/statistics")
@RequireRole({Role.ADMIN, Role.TEACHER})
public class StatisticsController {

    @Autowired
    private StatisticsService statService;

    @Operation(summary = "总览数据(仪表盘卡片)")
    @GetMapping("/overview")
    public R<OverviewVO> overview() {
        return R.ok(statService.overview());
    }

    @Operation(summary = "报名趋势(按学期)")
    @GetMapping("/registration-trend")
    public R<List<ChartItem>> registrationTrend() {
        return R.ok(statService.registrationTrend());
    }

    @Operation(summary = "课程合格率 Top10")
    @GetMapping("/pass-rate")
    public R<List<ChartItem>> passRate() {
        return R.ok(statService.coursePassRate());
    }

    @Operation(summary = "专业-计划数分布")
    @GetMapping("/major-distribution")
    public R<List<ChartItem>> majorDistribution() {
        return R.ok(statService.majorDistribution());
    }

    @Operation(summary = "成绩状态分布")
    @GetMapping("/score-status")
    public R<List<Map<String, Object>>> scoreStatus() {
        return R.ok(statService.scoreStatusDist());
    }
}
