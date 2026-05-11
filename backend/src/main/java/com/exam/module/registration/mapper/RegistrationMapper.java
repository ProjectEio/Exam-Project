package com.exam.module.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.exam.module.registration.dto.RegistrationQueryDTO;
import com.exam.module.registration.entity.Registration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RegistrationMapper extends BaseMapper<Registration> {

    IPage<Registration> pageWithJoin(IPage<Registration> page, @Param("q") RegistrationQueryDTO query);

    List<Registration> listByStudent(@Param("studentId") Long studentId);

    @Select("""
            SELECT r.*, u.real_name AS studentName, u.id_card AS studentIdCard,
                   p.plan_name AS planName, p.plan_code AS planCode,
                   c.course_name AS courseName,
                   p.exam_date AS examDate, p.location AS examLocation,
                   p.start_time AS startTime, p.end_time AS endTime
            FROM sys_registration r
            LEFT JOIN sys_user u ON u.id = r.student_id
            LEFT JOIN sys_exam_plan p ON p.id = r.plan_id
            LEFT JOIN sys_course c ON c.id = p.course_id
            WHERE r.id = #{id}
            """)
    Registration detailWithJoin(Long id);

    @Update("UPDATE sys_exam_plan SET registered_count = registered_count + 1 WHERE id = #{planId}")
    int incrementRegistered(Long planId);

    @Update("UPDATE sys_exam_plan SET registered_count = MAX(registered_count - 1, 0) WHERE id = #{planId}")
    int decrementRegistered(Long planId);
}
