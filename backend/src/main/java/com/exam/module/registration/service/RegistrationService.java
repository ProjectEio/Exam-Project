package com.exam.module.registration.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.BizException;
import com.exam.common.PageResult;
import com.exam.common.UserContext;
import com.exam.module.plan.entity.ExamPlan;
import com.exam.module.plan.mapper.ExamPlanMapper;
import com.exam.module.registration.dto.RegistrationQueryDTO;
import com.exam.module.registration.entity.Registration;
import com.exam.module.registration.mapper.RegistrationMapper;
import com.exam.shard.RegistrationShardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RegistrationService {

    @Autowired
    private RegistrationMapper regMapper;

    @Autowired
    private RegistrationShardRepository regRepo;

    @Autowired
    private ExamPlanMapper planMapper;

    public PageResult<Registration> page(RegistrationQueryDTO query) {
        return regRepo.page(query);
    }

    public List<Registration> myRegistrations() {
        return regMapper.listByStudent(UserContext.userId());
    }

    public Registration detail(Long id) {
        Registration r = regMapper.detailWithJoin(id);
        if (r == null) throw new BizException("报名记录不存在");
        return r;
    }

    @Transactional
    public Registration register(Long planId) {
        Long sid = UserContext.userId();
        ExamPlan plan = planMapper.selectById(planId);
        if (plan == null) throw new BizException("考试计划不存在");
        if (!"PUBLISHED".equals(plan.getStatus())) throw new BizException("该计划尚未开放报名");
        if (plan.getCapacity() != null && plan.getRegisteredCount() != null
                && plan.getRegisteredCount() >= plan.getCapacity()) {
            throw new BizException("报名人数已满");
        }

        // EXISTS + LIMIT 1：主键已是 UNIQUE(student_id, plan_id)，直接索引命中
        if (regMapper.existsByStudentAndPlan(sid, planId) != null) throw new BizException("您已报名该计划");

        // 报名期检查（如果配置了 register_start / register_end）
        String today = java.time.LocalDate.now().toString();
        if (cn.hutool.core.util.StrUtil.isNotBlank(plan.getRegisterStart())
                && today.compareTo(plan.getRegisterStart()) < 0) {
            throw new BizException("尚未开始报名 (开始时间: " + plan.getRegisterStart() + ")");
        }
        if (cn.hutool.core.util.StrUtil.isNotBlank(plan.getRegisterEnd())
                && today.compareTo(plan.getRegisterEnd()) > 0) {
            throw new BizException("报名已截止 (截止时间: " + plan.getRegisterEnd() + ")");
        }

        Registration r = new Registration();
        r.setStudentId(sid);
        r.setPlanId(planId);
        r.setRegistrationNo(buildRegNo(plan, sid));
        r.setPaymentStatus("UNPAID");
        r.setStatus("PENDING");
        r.setRegisterTime(LocalDateTime.now());
        regMapper.insert(r);
        regMapper.incrementRegistered(planId);
        return r;
    }

    @Transactional
    public void audit(Long id, String status, String remark) {
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BizException("审核状态非法");
        }
        Registration old = regMapper.selectById(id);
        if (old == null) throw new BizException("报名记录不存在");

        Registration r = new Registration();
        r.setId(id);
        r.setStatus(status);
        r.setAuditRemark(remark);
        if ("APPROVED".equals(status)) {
            r.setPaymentStatus("PAID");
            if (StrUtil.isBlank(old.getAdmissionTicketNo())) {
                ExamPlan p = planMapper.selectById(old.getPlanId());
                r.setAdmissionTicketNo(buildTicketNo(p, old.getStudentId()));
            }
        } else if ("REJECTED".equals(status)) {
            regMapper.decrementRegistered(old.getPlanId());
        }
        regMapper.updateById(r);
    }

    @Transactional
    public void cancel(Long id) {
        Registration r = regMapper.selectById(id);
        if (r == null) throw new BizException("报名记录不存在");
        if (UserContext.isStudent() && !r.getStudentId().equals(UserContext.userId())) {
            throw new BizException("无权取消他人报名");
        }
        regMapper.deleteById(id);
        regMapper.decrementRegistered(r.getPlanId());
    }

    private String buildRegNo(ExamPlan plan, Long studentId) {
        return "REG" + plan.getExamYear() + (plan.getExamTerm().equals("上") ? "01" : "02")
                + String.format("%04d", plan.getId())
                + String.format("%04d", studentId % 10000);
    }

    private String buildTicketNo(ExamPlan plan, Long studentId) {
        return "AT" + plan.getExamYear() + (plan.getExamTerm().equals("上") ? "01" : "02")
                + String.format("%04d", plan.getId())
                + String.format("%04d", studentId % 10000);
    }
}
