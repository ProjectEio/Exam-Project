package com.exam.module.statistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "总览数据")
public class OverviewVO {
    @Schema(description = "用户总数")
    private Long userCount;

    @Schema(description = "考生数")
    private Long studentCount;

    @Schema(description = "专业数")
    private Long majorCount;

    @Schema(description = "课程数")
    private Long courseCount;

    @Schema(description = "考试计划数")
    private Long planCount;

    @Schema(description = "已发布计划数")
    private Long publishedPlanCount;

    @Schema(description = "报名总数")
    private Long registrationCount;

    @Schema(description = "已审核通过数")
    private Long approvedCount;

    @Schema(description = "成绩记录数")
    private Long scoreCount;

    @Schema(description = "总合格率(%)")
    private Double passRate;
}
