package com.exam.module.course.controller;

import com.exam.common.PageQuery;
import com.exam.common.PageResult;
import com.exam.common.R;
import com.exam.common.RequireRole;
import com.exam.common.Role;
import com.exam.module.course.entity.Course;
import com.exam.module.course.entity.MajorCourse;
import com.exam.module.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
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

/**
 * 课程管理 REST 控制器。
 * <p>
 * 提供课程的 CRUD、按专业查询课程、维护专业-课程关联关系等接口。
 * <p>
 * 权限规则：
 * <ul>
 *   <li>查询类（page/all/byMajor/detail/majorCourses/countByType）→ 任何登录用户</li>
 *   <li>写操作（add/update/delete/batchDelete/linkMajorCourses）→ 仅 {@link Role#ADMIN}</li>
 * </ul>
 *
 * @author exam-team
 */
@Tag(name = "04-课程管理", description = "课程 CRUD 与专业-课程关联维护")
@RestController
@RequestMapping("/api/courses")
@Validated
public class CourseController {

    @Autowired
    private CourseService courseService;

    @Operation(summary = "分页查询课程", description = "支持 keyword 模糊匹配 课程代码 / 课程名称")
    @GetMapping
    public R<PageResult<Course>> page(PageQuery query) {
        return R.ok(courseService.page(query));
    }

    @Operation(summary = "全部课程", description = "用于下拉框等场景，不分页")
    @GetMapping("/all")
    public R<List<Course>> all() {
        return R.ok(courseService.all());
    }

    @Operation(summary = "查询专业下的课程", description = "通过 sys_major_course 关联表查询；is_required=1 优先排序")
    @GetMapping("/by-major/{majorId}")
    public R<List<Course>> byMajor(
            @Parameter(description = "专业 ID", required = true) @PathVariable @NotNull Long majorId) {
        return R.ok(courseService.byMajor(majorId));
    }

    @Operation(summary = "课程详情")
    @GetMapping("/{id}")
    public R<Course> detail(@Parameter(description = "课程 ID") @PathVariable @NotNull Long id) {
        return R.ok(courseService.detail(id));
    }

    @Operation(summary = "新增课程", description = "课程代码唯一，重复将返回业务异常")
    @RequireRole(Role.ADMIN)
    @PostMapping
    public R<Void> add(@Valid @RequestBody Course course) {
        course.setId(null);
        courseService.save(course);
        return R.ok(null, "新增成功");
    }

    @Operation(summary = "更新课程")
    @RequireRole(Role.ADMIN)
    @PutMapping
    public R<Void> update(@Valid @RequestBody Course course) {
        if (course.getId() == null) {
            return R.fail(400, "更新时 id 不能为空");
        }
        courseService.save(course);
        return R.ok(null, "更新成功");
    }

    @Operation(summary = "删除课程", description = "逻辑删除（标记 deleted=1）")
    @RequireRole(Role.ADMIN)
    @DeleteMapping("/{id}")
    public R<Void> delete(@Parameter(description = "课程 ID") @PathVariable @NotNull Long id) {
        courseService.delete(id);
        return R.ok(null, "删除成功");
    }

    @Operation(summary = "批量删除", description = "逗号分隔的 ID 列表，例如 1,2,3")
    @RequireRole(Role.ADMIN)
    @DeleteMapping("/batch")
    public R<Integer> batchDelete(
            @Parameter(description = "ID 列表") @RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.fail(400, "ids 不能为空");
        }
        int n = courseService.batchDelete(ids);
        return R.ok(n, "已删除 " + n + " 条");
    }

    @Operation(summary = "按课程类型统计", description = "返回 [{type:'公共课', count: 3}, ...]，可用于图表")
    @GetMapping("/count-by-type")
    public R<List<java.util.Map<String, Object>>> countByType() {
        return R.ok(courseService.countByType());
    }

    @Operation(summary = "查询专业关联课程列表", description = "返回 sys_major_course 中该专业的全部关联记录")
    @GetMapping("/links/{majorId}")
    public R<List<MajorCourse>> majorCourses(
            @Parameter(description = "专业 ID") @PathVariable @NotNull Long majorId) {
        return R.ok(courseService.majorCourses(majorId));
    }

    @Operation(summary = "保存专业-课程关系", description = "全量替换：先清空该专业现有关联，再批量写入新的 courseIds")
    @RequireRole(Role.ADMIN)
    @PostMapping("/links/{majorId}")
    public R<Void> linkMajorCourses(
            @Parameter(description = "专业 ID") @PathVariable @NotNull Long majorId,
            @Parameter(description = "要关联的课程 ID 列表，传空数组表示清空全部关联")
            @RequestBody List<Long> courseIds) {
        courseService.linkMajorCourses(majorId, courseIds);
        return R.ok(null, "保存成功");
    }
}
