package com.exam.module.plan.dto;

import com.exam.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "考试计划查询")
public class PlanQueryDTO extends PageQuery {
    @Schema(description = "年份")
    private Integer examYear;

    @Schema(description = "学期 上/下")
    private String examTerm;

    @Schema(description = "状态")
    private String status;

    @Schema(description = "专业ID")
    private Long majorId;
}
