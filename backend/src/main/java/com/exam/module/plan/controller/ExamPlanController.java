package com.exam.module.plan.controller;

import com.exam.common.PageResult;
import com.exam.common.R;
import com.exam.common.RequireRole;
import com.exam.common.Role;
import com.exam.module.plan.dto.PlanQueryDTO;
import com.exam.module.plan.entity.ExamPlan;
import com.exam.module.plan.service.ExamPlanService;
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

import java.util.List;

@Tag(name = "05-考试计划")
@RestController
@RequestMapping("/api/exam-plans")
public class ExamPlanController {

    @Autowired
    private ExamPlanService planService;

    @Operation(summary = "分页查询(管理端)")
    @RequireRole({Role.ADMIN, Role.TEACHER})
    @GetMapping
    public R<PageResult<ExamPlan>> page(PlanQueryDTO query) {
        return R.ok(planService.page(query));
    }

    @Operation(summary = "已发布列表(考生端)")
    @GetMapping("/published")
    public R<List<ExamPlan>> published(PlanQueryDTO query) {
        return R.ok(planService.published(query));
    }

    @Operation(summary = "详情")
    @RequireRole({Role.ADMIN, Role.TEACHER})
    @GetMapping("/{id}")
    public R<ExamPlan> detail(@PathVariable Long id) {
        return R.ok(planService.detail(id));
    }

    @Operation(summary = "新增")
    @RequireRole(Role.ADMIN)
    @PostMapping
    public R<Void> add(@Valid @RequestBody ExamPlan plan) {
        plan.setId(null);
        planService.save(plan);
        return R.ok(null, "新增成功");
    }

    @Operation(summary = "更新")
    @RequireRole(Role.ADMIN)
    @PutMapping
    public R<Void> update(@Valid @RequestBody ExamPlan plan) {
        planService.save(plan);
        return R.ok(null, "更新成功");
    }

    @Operation(summary = "删除")
    @RequireRole(Role.ADMIN)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return R.ok(null, "删除成功");
    }

    @Operation(summary = "改变状态")
    @RequireRole(Role.ADMIN)
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        planService.changeStatus(id, status);
        return R.ok(null, "状态已更新");
    }
}
