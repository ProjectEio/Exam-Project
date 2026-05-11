package com.exam.module.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "考生注册请求")
public class RegisterDTO {

    @NotBlank
    @Size(min = 4, max = 20)
    @Schema(description = "用户名(4-20位)")
    private String username;

    @NotBlank
    @Size(min = 6, max = 30)
    @Schema(description = "密码(6-30位)")
    private String password;

    @NotBlank
    @Schema(description = "真实姓名")
    private String realName;

    @Schema(description = "身份证号")
    private String idCard;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "性别")
    private String gender;
}
