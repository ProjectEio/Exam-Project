package com.exam.module.registration.dto;

import com.exam.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "报名查询")
public class RegistrationQueryDTO extends PageQuery {
    private Long studentId;
    private Long planId;
    private String status;
    private String paymentStatus;
}
