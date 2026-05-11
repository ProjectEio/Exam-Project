package com.exam.module.plan.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BizException;
import com.exam.common.PageResult;
import com.exam.module.plan.dto.PlanQueryDTO;
import com.exam.module.plan.entity.ExamPlan;
import com.exam.module.plan.mapper.ExamPlanMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExamPlanService {

    @Autowired
    private ExamPlanMapper planMapper;

    public PageResult<ExamPlan> page(PlanQueryDTO query) {
        Page<ExamPlan> page = new Page<>(query.getCurrent(), query.getSize());
        return PageResult.of(planMapper.pageWithJoin(page, query));
    }

    public List<ExamPlan> published(PlanQueryDTO query) {
        return planMapper.listPublished(query);
    }

    public ExamPlan detail(Long id) {
        ExamPlan p = planMapper.selectById(id);
        if (p == null) throw new BizException("考试计划不存在");
        return p;
    }

    public void save(ExamPlan plan) {
        if (plan.getStatus() == null) plan.setStatus("DRAFT");
        if (plan.getRegisteredCount() == null) plan.setRegisteredCount(0);
        if (plan.getId() == null) {
            planMapper.insert(plan);
        } else {
            planMapper.updateById(plan);
        }
    }

    public void delete(Long id) {
        planMapper.deleteById(id);
    }

    public void changeStatus(Long id, String status) {
        if (!java.util.List.of("DRAFT", "PUBLISHED", "FINISHED").contains(status)) {
            throw new BizException("非法状态");
        }
        ExamPlan p = new ExamPlan();
        p.setId(id);
        p.setStatus(status);
        planMapper.updateById(p);
    }
}
