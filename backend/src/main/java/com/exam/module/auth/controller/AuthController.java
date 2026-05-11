package com.exam.module.auth.controller;

import com.exam.common.R;
import com.exam.module.auth.dto.LoginDTO;
import com.exam.module.auth.dto.LoginVO;
import com.exam.module.auth.dto.RegisterDTO;
import com.exam.module.auth.service.AuthService;
import com.exam.module.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01-认证管理")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody @Valid LoginDTO dto) {
        return R.ok(authService.login(dto), "登录成功");
    }

    @Operation(summary = "考生注册")
    @PostMapping("/register")
    public R<Void> register(@RequestBody @Valid RegisterDTO dto) {
        authService.register(dto);
        return R.ok(null, "注册成功");
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/info")
    public R<User> info() {
        return R.ok(authService.currentUser());
    }
}
