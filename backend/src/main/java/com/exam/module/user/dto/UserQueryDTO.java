package com.exam.module.user.dto;

import com.exam.common.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Schema(description = "用户分页查询")
public class UserQueryDTO extends PageQuery {

    @Schema(description = "角色筛选")
    private String role;

    @Schema(description = "状态筛选 0/1")
    private Integer status;
}
