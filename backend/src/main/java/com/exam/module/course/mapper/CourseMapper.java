package com.exam.module.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.module.course.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    @Select("""
            SELECT c.* FROM sys_course c
            JOIN sys_major_course mc ON mc.course_id = c.id
            WHERE mc.major_id = #{majorId} AND c.deleted = 0
            ORDER BY mc.is_required DESC, c.id ASC
            """)
    List<Course> selectByMajorId(Long majorId);

    @Select("""
            SELECT course_type AS type, COUNT(*) AS count
            FROM sys_course
            WHERE deleted = 0
            GROUP BY course_type
            ORDER BY count DESC
            """)
    List<Map<String, Object>> countByType();
}
