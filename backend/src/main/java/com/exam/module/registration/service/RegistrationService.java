package com.exam.module.registration.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.exam.common.BizException;
import com.exam.common.PageResult;
import com.exam.common.UserContext;
import com.exam.module.course.entity.Course;
import com.exam.module.course.mapper.CourseMapper;
import com.exam.module.plan.entity.ExamPlan;
import com.exam.module.plan.mapper.ExamPlanMapper;
import com.exam.module.registration.dto.RegistrationQueryDTO;
import com.exam.module.registration.entity.Registration;
import com.exam.module.registration.mapper.RegistrationMapper;
import com.exam.module.user.entity.User;
import com.exam.shard.RegistrationShardRepository;
import com.exam.shard.UserShardRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RegistrationService {

    @Autowired private RegistrationMapper         regMapper;
    @Autowired private RegistrationShardRepository regRepo;
    @Autowired private ExamPlanMapper             planMapper;
    @Autowired private CourseMapper               courseMapper;
    @Autowired private UserShardRepository        userRepo;

    // ── 分页（admin 后台）─────────────────────────────────

    public PageResult<Registration> page(RegistrationQueryDTO query) {
        PageResult<Registration> result = regRepo.page(query);
        enrich(result.getRecords());
        return result;
    }

    // ── 详情（admin / 学生）──────────────────────────────

    public Registration detail(Long id) {
        // 优先查主库（新报名写在主库），再 fan-out 分片
        Registration r = regMapper.detailWithJoin(id);
        if (r == null) {
            r = regRepo.findById(id);
            if (r == null) throw new BizException("报名记录不存在");
            enrich(Collections.singletonList(r));
        }
        return r;
    }

    // ── 学生自己的报名列表 ─────────────────────────────────

    public List<Registration> myRegistrations() {
        return regMapper.listByStudent(UserContext.userId());
    }

    // ── 审核 ──────────────────────────────────────────────

    @Transactional
    public void audit(Long id, String status, String remark) {
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BizException("审核状态非法");
        }
        // 先查主库，再查分片
        Registration old = regMapper.selectById(id);
        boolean fromShard = (old == null);
        if (fromShard) {
            old = regRepo.findById(id);
            if (old == null) throw new BizException("报名记录不存在");
        }

        String newPayment = "APPROVED".equals(status) ? "PAID" : old.getPaymentStatus();
        String ticketNo   = old.getAdmissionTicketNo();
        if ("APPROVED".equals(status) && StrUtil.isBlank(ticketNo)) {
            ExamPlan p = planMapper.selectById(old.getPlanId());
            if (p != null) ticketNo = buildTicketNo(p, old.getStudentId());
        }
        if ("REJECTED".equals(status)) {
            planMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ExamPlan>()
                    .setSql("registered_count = MAX(registered_count - 1, 0)")
                    .eq(ExamPlan::getId, old.getPlanId()));
        }

        if (fromShard) {
            regRepo.updateStatus(id, status, remark, ticketNo, newPayment);
        } else {
            Registration upd = new Registration();
            upd.setId(id);
            upd.setStatus(status);
            upd.setAuditRemark(remark);
            upd.setAdmissionTicketNo(ticketNo);
            upd.setPaymentStatus(newPayment);
            regMapper.updateById(upd);
        }
    }

    // ── 取消 ──────────────────────────────────────────────

    @Transactional
    public void cancel(Long id) {
        Registration r = regMapper.selectById(id);
        boolean fromShard = (r == null);
        if (fromShard) {
            r = regRepo.findById(id);
            if (r == null) throw new BizException("报名记录不存在");
        }
        if (UserContext.isStudent() && !r.getStudentId().equals(UserContext.userId())) {
            throw new BizException("无权取消他人报名");
        }

        if (fromShard) {
            regRepo.softDelete(id);
        } else {
            regMapper.deleteById(id);
        }
        planMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ExamPlan>()
                .setSql("registered_count = MAX(registered_count - 1, 0)")
                .eq(ExamPlan::getId, r.getPlanId()));
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

        if (regMapper.existsByStudentAndPlan(sid, planId) != null) throw new BizException("您已报名该计划");

        String today = java.time.LocalDate.now().toString();
        if (StrUtil.isNotBlank(plan.getRegisterStart())
                && today.compareTo(plan.getRegisterStart()) < 0) {
            throw new BizException("尚未开始报名 (开始时间: " + plan.getRegisterStart() + ")");
        }
        if (StrUtil.isNotBlank(plan.getRegisterEnd())
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

    // ── 内部：丰富 join 字段 ───────────────────────────────

    public void enrich(List<Registration> list) {
        if (list == null || list.isEmpty()) return;

        // 计划信息（主库）
        Set<Long> planIds = list.stream()
                .map(Registration::getPlanId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ExamPlan> planMap = planIds.isEmpty() ? Collections.emptyMap() :
                planMapper.selectBatchIds(planIds).stream()
                        .collect(Collectors.toMap(ExamPlan::getId, p -> p));

        // 课程信息（主库）
        Set<Long> courseIds = planMap.values().stream()
                .map(ExamPlan::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> courseNameMap = courseIds.isEmpty() ? Collections.emptyMap() :
                courseMapper.selectBatchIds(courseIds).stream()
                        .collect(Collectors.toMap(Course::getId, Course::getCourseName));

        // 学生姓名（用户分片）
        Set<Long> studentIds = list.stream()
                .map(Registration::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> studentNameMap = new HashMap<>();
        for (Long sid : studentIds) {
            User u = userRepo.findById(sid);
            if (u != null && u.getRealName() != null) studentNameMap.put(sid, u.getRealName());
        }

        // 填充
        for (Registration r : list) {
            if (r.getStudentId() != null) r.setStudentName(studentNameMap.get(r.getStudentId()));
            ExamPlan plan = planMap.get(r.getPlanId());
            if (plan != null) {
                r.setPlanName(plan.getPlanName());
                r.setPlanCode(plan.getPlanCode());
                r.setExamDate(plan.getExamDate());
                r.setExamLocation(plan.getLocation());
                r.setStartTime(plan.getStartTime());
                r.setEndTime(plan.getEndTime());
                r.setCourseName(courseNameMap.get(plan.getCourseId()));
            }
        }
    }

    // ── 私有工具 ──────────────────────────────────────────

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
