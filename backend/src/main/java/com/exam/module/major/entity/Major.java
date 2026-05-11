package com.exam.module.major.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_major")
public class Major {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "专业代码不能为空")
    @Size(max = 50)
    private String majorCode;

    @NotBlank(message = "专业名称不能为空")
    @Size(max = 100)
    private String majorName;

    @NotBlank(message = "层次不能为空")
    private String level;

    private Integer totalCredits;
    private String description;
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
