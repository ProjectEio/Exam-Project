package com.exam.module.statistics.mapper;

import com.exam.module.statistics.dto.ChartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface StatisticsMapper {

    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0")
    Long countUser();

    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND role = 'STUDENT'")
    Long countStudent();

    @Select("SELECT COUNT(*) FROM sys_major WHERE deleted = 0")
    Long countMajor();

    @Select("SELECT COUNT(*) FROM sys_course WHERE deleted = 0")
    Long countCourse();

    @Select("SELECT COUNT(*) FROM sys_exam_plan WHERE deleted = 0")
    Long countPlan();

    @Select("SELECT COUNT(*) FROM sys_exam_plan WHERE deleted = 0 AND status = 'PUBLISHED'")
    Long countPublishedPlan();

    @Select("SELECT COUNT(*) FROM sys_registration WHERE deleted = 0")
    Long countRegistration();

    @Select("SELECT COUNT(*) FROM sys_registration WHERE deleted = 0 AND status = 'APPROVED'")
    Long countApproved();

    @Select("SELECT COUNT(*) FROM sys_score WHERE deleted = 0")
    Long countScore();

    @Select("SELECT COUNT(*) FROM sys_score WHERE deleted = 0 AND status = 'PASS'")
    Long countPassScore();

    @Select("""
                        SELECT (CAST(p.exam_year AS TEXT) || '-' || p.exam_term) AS label,
                                   COUNT(r.id) AS value
                        FROM sys_exam_plan p
                        LEFT JOIN sys_registration r ON r.plan_id = p.id AND r.deleted = 0
                        WHERE p.deleted = 0
                        GROUP BY p.exam_year, p.exam_term
                        ORDER BY p.exam_year ASC,
                                         CASE p.exam_term
                                                 WHEN '上' THEN 1
                                                 WHEN '下' THEN 2
                                                 ELSE 9
                                         END ASC
            """)
    List<ChartItem> registrationTrend();

    @Select("""
            SELECT c.course_name AS label,
                   ROUND(SUM(CASE WHEN s.status='PASS' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 1) AS value
            FROM sys_score s
            JOIN sys_course c ON c.id = s.course_id
            WHERE s.deleted = 0
            GROUP BY c.id, c.course_name
            ORDER BY value DESC
            LIMIT 10
            """)
    List<ChartItem> coursePassRate();

    @Select("""
            SELECT m.major_name AS label, COUNT(p.id) AS value
            FROM sys_major m
            LEFT JOIN sys_exam_plan p ON p.major_id = m.id AND p.deleted = 0
            WHERE m.deleted = 0
            GROUP BY m.id, m.major_name
            """)
    List<ChartItem> majorDistribution();

    @Select("""
            SELECT status AS label, COUNT(*) AS value
            FROM sys_score
            WHERE deleted = 0
            GROUP BY status
            """)
    List<Map<String, Object>> scoreStatusDist();
}
