package com.exam.module.major.controller;

import com.exam.common.PageQuery;
import com.exam.common.PageResult;
import com.exam.common.R;
import com.exam.common.RequireRole;
import com.exam.common.Role;
import com.exam.module.major.entity.Major;
import com.exam.module.major.service.MajorService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "03-专业管理")
@RestController
@RequestMapping("/api/majors")
public class MajorController {

    @Autowired
    private MajorService majorService;

    @Operation(summary = "分页查询")
    @GetMapping
    public R<PageResult<Major>> page(PageQuery query) {
        return R.ok(majorService.page(query));
    }

    @Operation(summary = "获取所有启用专业 (用于下拉)")
    @GetMapping("/all")
    public R<List<Major>> all() {
        return R.ok(majorService.all());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<Major> detail(@PathVariable Long id) {
        return R.ok(majorService.detail(id));
    }

    @Operation(summary = "新增")
    @RequireRole(Role.ADMIN)
    @PostMapping
    public R<Void> add(@Valid @RequestBody Major major) {
        major.setId(null);
        majorService.save(major);
        return R.ok(null, "新增成功");
    }

    @Operation(summary = "更新")
    @RequireRole(Role.ADMIN)
    @PutMapping
    public R<Void> update(@Valid @RequestBody Major major) {
        majorService.save(major);
        return R.ok(null, "更新成功");
    }

    @Operation(summary = "删除")
    @RequireRole(Role.ADMIN)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        majorService.delete(id);
        return R.ok(null, "删除成功");
    }
}
