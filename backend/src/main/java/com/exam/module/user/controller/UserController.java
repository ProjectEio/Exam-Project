package com.exam.module.user.controller;

import com.exam.common.PageResult;
import com.exam.common.R;
import com.exam.common.RequireRole;
import com.exam.common.Role;
import com.exam.module.user.dto.UserQueryDTO;
import com.exam.module.user.dto.UserSaveDTO;
import com.exam.module.user.entity.User;
import com.exam.module.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "02-用户管理")
@RestController
@RequestMapping("/api/users")
@RequireRole(Role.ADMIN)
public class UserController {

    @Autowired
    private UserService userService;

    @Operation(summary = "分页查询")
    @RequireRole({Role.ADMIN, Role.TEACHER})
    @GetMapping
    public R<PageResult<User>> page(UserQueryDTO query) {
        return R.ok(userService.page(query));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public R<User> detail(@PathVariable Long id) {
        return R.ok(userService.detail(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    public R<Void> add(@RequestBody @Valid UserSaveDTO dto) {
        dto.setId(null);
        userService.save(dto);
        return R.ok(null, "新增成功");
    }

    @Operation(summary = "更新")
    @PutMapping
    public R<Void> update(@RequestBody @Valid UserSaveDTO dto) {
        userService.save(dto);
        return R.ok(null, "更新成功");
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok(null, "删除成功");
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/reset-password")
    public R<Void> reset(@PathVariable Long id, @RequestParam String newPassword) {
        userService.resetPassword(id, newPassword);
        return R.ok(null, "重置成功");
    }
}
