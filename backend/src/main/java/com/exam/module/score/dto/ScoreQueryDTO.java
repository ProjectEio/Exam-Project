package com.exam.module.score.dto;

import com.exam.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "成绩查询")
public class ScoreQueryDTO extends PageQuery {
    private Long studentId;
    private Long courseId;
    private Integer examYear;
    private String examTerm;
    private String status;
}
