package com.exam.module.registration.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_registration")
public class Registration {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;
    private Long planId;
    private String registrationNo;
    private String admissionTicketNo;
    private String paymentStatus; // PAID/UNPAID
    private String status;        // PENDING/APPROVED/REJECTED
    private String auditRemark;
    private LocalDateTime registerTime;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 联表
    @TableField(exist = false)
    private String studentName;
    @TableField(exist = false)
    private String studentIdCard;
    @TableField(exist = false)
    private String planName;
    @TableField(exist = false)
    private String planCode;
    @TableField(exist = false)
    private String courseName;
    @TableField(exist = false)
    private String examDate;
    @TableField(exist = false)
    private String examLocation;
    @TableField(exist = false)
    private String startTime;
    @TableField(exist = false)
    private String endTime;
}
