package com.exam.module.score.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

@Data
@ColumnWidth(20)
public class ScoreImportDTO {
    @ExcelProperty("学生用户名")
    private String username;

    @ExcelProperty("课程代码")
    private String courseCode;

    @ExcelProperty("考试年份")
    private Integer examYear;

    @ExcelProperty("考试学期")
    private String examTerm;

    @ExcelProperty("成绩")
    private Double score;

    @ExcelProperty("考试日期")
    private String examDate;
}
