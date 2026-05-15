package com.exam.module.score.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.exam.module.score.dto.ScoreQueryDTO;
import com.exam.module.score.entity.Score;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScoreMapper extends BaseMapper<Score> {
    IPage<Score> pageWithJoin(IPage<Score> page, @Param("q") ScoreQueryDTO query);
    List<Score> listByStudent(@Param("studentId") Long studentId);

    /**
     * 成绩重复检查 — EXISTS + LIMIT 1，不 COUNT 全表
     * 返回 1 表示已存在，null 表示不存在
     */
    @org.apache.ibatis.annotations.Select(
        "SELECT 1 FROM sys_score WHERE student_id=#{studentId} AND course_id=#{courseId} " +
        "AND exam_year=#{examYear} AND exam_term=#{examTerm} AND deleted=0 LIMIT 1")
    Integer existsByStudentCourseYearTerm(
        @Param("studentId") Long studentId,
        @Param("courseId")  Long courseId,
        @Param("examYear")  Integer examYear,
        @Param("examTerm")  String examTerm);
}
