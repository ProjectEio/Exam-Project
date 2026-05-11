package com.exam.module.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.exam.module.plan.dto.PlanQueryDTO;
import com.exam.module.plan.entity.ExamPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExamPlanMapper extends BaseMapper<ExamPlan> {
    IPage<ExamPlan> pageWithJoin(IPage<ExamPlan> page, @Param("q") PlanQueryDTO query);
    List<ExamPlan> listPublished(@Param("q") PlanQueryDTO query);
}
