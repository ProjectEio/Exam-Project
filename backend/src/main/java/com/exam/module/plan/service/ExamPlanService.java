package com.exam.module.plan.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.cache.MemoryCacheManager;
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

    private static final String PAGE_KEY_PREFIX = "plan:page:";
    private static final String PUBLISHED_KEY_PREFIX = "plan:published:";

    @Autowired
    private ExamPlanMapper planMapper;

    @Autowired
    private MemoryCacheManager cacheManager;

    public PageResult<ExamPlan> page(PlanQueryDTO query) {
        String cacheKey = PAGE_KEY_PREFIX + query.getCurrent() + ":" + query.getSize() + ":"
                + query.getStatus() + ":" + query.getExamYear() + ":" + query.getExamTerm()
                + ":" + query.getMajorId() + ":" + query.getKeyword();
        PageResult<ExamPlan> hit = cacheManager.get(MemoryCacheManager.PAGE_CACHE, cacheKey);
        if (hit != null) return hit;

        Page<ExamPlan> page = new Page<>(query.getCurrent(), query.getSize());
        PageResult<ExamPlan> result = PageResult.of(planMapper.pageWithJoin(page, query));
        cacheManager.put(MemoryCacheManager.PAGE_CACHE, cacheKey, result);
        return result;
    }

    public List<ExamPlan> published(PlanQueryDTO query) {
        String cacheKey = PUBLISHED_KEY_PREFIX + query.getStatus() + ":" + query.getExamYear() + ":"
                + query.getExamTerm() + ":" + query.getMajorId() + ":" + query.getKeyword();
        List<ExamPlan> hit = cacheManager.get(MemoryCacheManager.PLAN_CACHE, cacheKey);
        if (hit != null) return hit;
        List<ExamPlan> result = planMapper.listPublished(query);
        cacheManager.put(MemoryCacheManager.PLAN_CACHE, cacheKey, result);
        return result;
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
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
        cacheManager.invalidateAll(MemoryCacheManager.PLAN_CACHE);
    }

    public void delete(Long id) {
        planMapper.deleteById(id);
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
        cacheManager.invalidateAll(MemoryCacheManager.PLAN_CACHE);
    }

    public void changeStatus(Long id, String status) {
        if (!java.util.List.of("DRAFT", "PUBLISHED", "FINISHED").contains(status)) {
            throw new BizException("非法状态");
        }
        ExamPlan p = new ExamPlan();
        p.setId(id);
        p.setStatus(status);
        planMapper.updateById(p);
        cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
        cacheManager.invalidateAll(MemoryCacheManager.PLAN_CACHE);
    }
}
