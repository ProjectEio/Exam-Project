package com.exam.module.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "用户保存DTO")
public class UserSaveDTO {

    @Schema(description = "ID（更新时传）")
    private Long id;

    @NotBlank
    @Schema(description = "用户名")
    private String username;

    @Schema(description = "密码（新增必填，更新留空表示不改）")
    private String password;

    @NotBlank
    @Schema(description = "角色 ADMIN/TEACHER/STUDENT")
    private String role;

    private String realName;
    private String idCard;
    private String phone;
    private String email;
    private String gender;
    private Integer status;
}
