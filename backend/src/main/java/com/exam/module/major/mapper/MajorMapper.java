package com.exam.module.major.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exam.module.major.entity.Major;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface MajorMapper extends BaseMapper<Major> {

    /** 专业代码唯一性检查 — LIMIT 1 短路 */
    @Select("SELECT 1 FROM sys_major WHERE major_code = #{majorCode} AND deleted = 0 LIMIT 1")
    Integer existsByMajorCode(String majorCode);
}
