package com.exam.module.registration.service;

import cn.hutool.core.util.StrUtil;
import com.exam.common.BizException;
import com.exam.common.PageResult;
import com.exam.common.UserContext;
import com.exam.module.course.entity.Course;
import com.exam.module.course.mapper.CourseMapper;
import com.exam.module.plan.entity.ExamPlan;
import com.exam.module.plan.mapper.ExamPlanMapper;
import com.exam.module.registration.dto.RegistrationQueryDTO;
import com.exam.module.registration.entity.Registration;
import com.exam.module.statistics.service.StatisticsService;
import com.exam.module.user.entity.User;
import com.exam.shard.RegistrationShardRepository;
import com.exam.shard.UserShardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RegistrationService {

    @Autowired private RegistrationShardRepository regRepo;
    @Autowired private ExamPlanMapper             planMapper;
    @Autowired private CourseMapper               courseMapper;
    @Autowired private StatisticsService          statService;
    @Autowired private UserShardRepository        userRepo;

    // ── 分页（admin 后台）─────────────────────────────────

    public PageResult<Registration> page(RegistrationQueryDTO query) {
        return regRepo.page(query);
    }

    // ── 详情（admin / 学生）──────────────────────────────

    public Registration detail(Long id) {
        Registration r = regRepo.findById(id);
        if (r == null) throw new BizException("报名记录不存在");
        return r;
    }

    // ── 学生自己的报名列表 ─────────────────────────────────

    public List<Registration> myRegistrations() {
        return regRepo.listByStudent(UserContext.userId());
    }

    // ── 审核 ──────────────────────────────────────────────

    @Transactional
    public void audit(Long id, String status, String remark) {
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BizException("审核状态非法");
        }
        Registration old = regRepo.findById(id);
        if (old == null) throw new BizException("报名记录不存在");

        String newPayment = "APPROVED".equals(status) ? "PAID" : old.getPaymentStatus();
        String ticketNo   = old.getAdmissionTicketNo();
        if ("APPROVED".equals(status) && StrUtil.isBlank(ticketNo)) {
            ExamPlan plan = planMapper.selectById(old.getPlanId());
            if (plan == null) throw new BizException("考试计划不存在");
            ticketNo = buildTicketNo(plan, old.getStudentId());
        }
        if ("REJECTED".equals(status)) {
            planMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ExamPlan>()
                    .setSql("registered_count = MAX(registered_count - 1, 0)")
                    .eq(ExamPlan::getId, old.getPlanId()));
        }
        regRepo.updateStatus(id, status, remark, ticketNo, newPayment);
    }

    // ── 取消 ──────────────────────────────────────────────

    @Transactional
    public void cancel(Long id) {
        Registration r = regRepo.findById(id);
        if (r == null) throw new BizException("报名记录不存在");
        if (UserContext.isStudent() && !r.getStudentId().equals(UserContext.userId())) {
            throw new BizException("无权取消他人报名");
        }

        regRepo.softDelete(id);
        planMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ExamPlan>()
                .setSql("registered_count = MAX(registered_count - 1, 0)")
                .eq(ExamPlan::getId, r.getPlanId()));
        statService.refreshRegistrationTrendCache();
    }

    // ── 报名（学生自助）───────────────────────────────────

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

        if (regRepo.existsByStudentAndPlan(sid, planId)) throw new BizException("您已报名该计划");

        String today = java.time.LocalDate.now().toString();
        if (StrUtil.isNotBlank(plan.getRegisterStart())
                && today.compareTo(plan.getRegisterStart()) < 0) {
            throw new BizException("尚未开始报名 (开始时间: " + plan.getRegisterStart() + ")");
        }
        if (StrUtil.isNotBlank(plan.getRegisterEnd())
                && today.compareTo(plan.getRegisterEnd()) > 0) {
            throw new BizException("报名已截止 (截止时间: " + plan.getRegisterEnd() + ")");
        }

        User student = userRepo.findById(sid);
        if (student == null) throw new BizException("用户不存在");
        Course course = courseMapper.selectById(plan.getCourseId());
        if (course == null) throw new BizException("课程不存在");

        Registration r = new Registration();
        r.setStudentId(sid);
        r.setPlanId(planId);
        r.setRegistrationNo(buildRegNo(plan, sid));
        r.setAdmissionTicketNo(buildTicketNo(plan, sid));
        r.setPaymentStatus("UNPAID");
        r.setStatus("PENDING");
        r.setRegisterTime(LocalDateTime.now());
        r.setStudentName(student.getRealName());
        r.setStudentIdCard(student.getIdCard());
        r.setPlanName(plan.getPlanName());
        r.setPlanCode(plan.getPlanCode());
        r.setCourseName(course.getCourseName());
        r.setExamDate(plan.getExamDate());
        r.setExamLocation(plan.getLocation());
        r.setStartTime(plan.getStartTime());
        r.setEndTime(plan.getEndTime());
        regRepo.insert(r);
        planMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ExamPlan>()
                .setSql("registered_count = registered_count + 1")
                .eq(ExamPlan::getId, planId));
        statService.refreshRegistrationTrendCache();
        return r;
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
