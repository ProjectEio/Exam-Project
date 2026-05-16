package com.exam.module.registration.controller;

import com.exam.common.PageResult;
import com.exam.common.R;
import com.exam.common.RequireRole;
import com.exam.common.Role;
import com.exam.module.registration.dto.RegistrationQueryDTO;
import com.exam.module.registration.entity.Registration;
import com.exam.module.registration.service.RegistrationExcelService;
import com.exam.module.registration.service.RegistrationService;
import com.exam.module.registration.service.TicketPdfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "06-报名管理")
@RestController
@RequestMapping("/api/registrations")
public class RegistrationController {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private TicketPdfService ticketPdfService;

    @Autowired
    private RegistrationExcelService excelService;

    @Operation(summary = "分页查询(管理端)")
    @RequireRole(Role.ADMIN)
    @GetMapping
    public R<PageResult<Registration>> page(RegistrationQueryDTO query) {
        return R.ok(registrationService.page(query));
    }

    @Operation(summary = "我的报名")
    @GetMapping("/mine")
    public R<List<Registration>> mine() {
        return R.ok(registrationService.myRegistrations());
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public R<Registration> detail(@PathVariable Long id,
                                  @RequestParam(required = false) Long studentId) {
        return R.ok(registrationService.detail(id, studentId));
    }

    @Operation(summary = "考生报名")
    @PostMapping("/register/{planId}")
    public R<Registration> register(@PathVariable Long planId) {
        return R.ok(registrationService.register(planId), "报名成功，请等待审核");
    }

    @Operation(summary = "审核 (APPROVED/REJECTED)")
    @RequireRole(Role.ADMIN)
    @PutMapping("/{id}/audit")
    public R<Registration> audit(@PathVariable Long id,
                         @RequestParam(required = false) Long studentId,
                         @RequestParam String status,
                         @RequestParam(required = false) String remark) {
        Registration updated = registrationService.audit(id, studentId, status, remark);
        return R.ok(updated, "审核完成");
    }

    @Operation(summary = "取消/删除")
    @DeleteMapping("/{id}")
    public R<Void> cancel(@PathVariable Long id,
                          @RequestParam(required = false) Long studentId) {
        registrationService.cancel(id, studentId);
        return R.ok(null, "已取消");
    }

    @Operation(summary = "下载准考证 PDF")
    @GetMapping("/{id}/ticket")
    public void downloadTicket(@PathVariable Long id,
                               @RequestParam(required = false) Long studentId,
                               HttpServletResponse response) throws Exception {
        ticketPdfService.exportTicket(studentId == null ? id : registrationService.detail(id, studentId).getId(), response);
    }

    @Operation(summary = "导出报名 Excel")
    @RequireRole(Role.ADMIN)
    @GetMapping("/export")
    public void export(RegistrationQueryDTO query, HttpServletResponse response) throws Exception {
        excelService.export(query, response);
    }
}
