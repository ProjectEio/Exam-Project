package com.exam.module.registration.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
@ColumnWidth(20)
public class RegistrationExcelVO {

    @ExcelProperty("报名编号")
    private String registrationNo;

    @ExcelProperty("姓名")
    private String studentName;

    @ExcelProperty("身份证号")
    private String studentIdCard;

    @ExcelProperty("考试计划")
    @ColumnWidth(35)
    private String planName;

    @ExcelProperty("考试科目")
    private String courseName;

    @ExcelProperty("考试日期")
    private String examDate;

    @ExcelProperty("审核状态")
    private String status;

    @ExcelProperty("缴费状态")
    private String paymentStatus;

    @ExcelProperty("准考证号")
    private String admissionTicketNo;
}
