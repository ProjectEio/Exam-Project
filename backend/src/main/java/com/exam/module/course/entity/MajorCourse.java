package com.exam.module.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_major_course")
public class MajorCourse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long majorId;
    private Long courseId;
    private Integer isRequired;
}
