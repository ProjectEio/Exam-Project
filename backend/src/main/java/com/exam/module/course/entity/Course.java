package com.exam.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_course")
public class Course {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "课程代码不能为空")
    @Size(max = 50)
    private String courseCode;

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 100)
    private String courseName;

    private Integer credit;

    @NotBlank(message = "课程类型不能为空")
    private String courseType;

    private String description;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
