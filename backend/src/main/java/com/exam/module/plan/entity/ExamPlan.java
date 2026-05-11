package com.exam.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_exam_plan")
public class ExamPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "计划代码不能为空")
    private String planCode;

    @NotBlank(message = "计划名称不能为空")
    private String planName;

    @NotNull(message = "考试年份不能为空")
    private Integer examYear;

    @NotBlank(message = "考试学期不能为空 (上/下)")
    private String examTerm;

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private Long majorId;
    private String examDate;
    private String startTime;
    private String endTime;
    private String location;

    @NotNull(message = "容量不能为空")
    private Integer capacity;

    private Integer registeredCount;
    private String registerStart;
    private String registerEnd;
    private String status; // DRAFT/PUBLISHED/FINISHED
    private String remark;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 联表展示字段
    @TableField(exist = false)
    private String courseName;
    @TableField(exist = false)
    private String majorName;
}
