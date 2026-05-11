package com.exam.module.score.controller;

import com.exam.common.PageResult;
import com.exam.common.R;
import com.exam.common.RequireRole;
import com.exam.common.Role;
import com.exam.module.score.dto.ScoreQueryDTO;
import com.exam.module.score.entity.Score;
import com.exam.module.score.service.ScoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "07-成绩管理")
@RestController
@RequestMapping("/api/scores")
public class ScoreController {

    @Autowired
    private ScoreService scoreService;

    @Operation(summary = "分页查询(管理端/教师端)")
    @GetMapping
    @RequireRole({Role.ADMIN, Role.TEACHER})
    public R<PageResult<Score>> page(ScoreQueryDTO query) {
        return R.ok(scoreService.page(query));
    }

    @Operation(summary = "我的成绩(考生端)")
    @GetMapping("/mine")
    public R<List<Score>> mine() {
        return R.ok(scoreService.myScores());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<Score> detail(@PathVariable Long id) {
        return R.ok(scoreService.detail(id));
    }

    @Operation(summary = "录入成绩")
    @PostMapping
    @RequireRole({Role.ADMIN, Role.TEACHER})
    public R<Void> add(@RequestBody Score score) {
        score.setId(null);
        scoreService.save(score);
        return R.ok(null, "录入成功");
    }

    @Operation(summary = "更新成绩")
    @PutMapping
    @RequireRole({Role.ADMIN, Role.TEACHER})
    public R<Void> update(@RequestBody Score score) {
        scoreService.save(score);
        return R.ok(null, "更新成功");
    }

    @Operation(summary = "删除成绩")
    @DeleteMapping("/{id}")
    @RequireRole({Role.ADMIN, Role.TEACHER})
    public R<Void> delete(@PathVariable Long id) {
        scoreService.delete(id);
        return R.ok(null, "删除成功");
    }

    @Operation(summary = "Excel 批量导入")
    @PostMapping("/import")
    @RequireRole({Role.ADMIN, Role.TEACHER})
    public R<ScoreService.ImportResult> importExcel(@RequestParam("file") MultipartFile file) throws Exception {
        return R.ok(scoreService.importExcel(file), "导入完成");
    }
}
