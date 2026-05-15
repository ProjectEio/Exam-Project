package com.exam.module.registration.service;

import com.alibaba.excel.EasyExcel;
import com.exam.common.PageResult;
import com.exam.module.registration.dto.RegistrationExcelVO;
import com.exam.module.registration.dto.RegistrationQueryDTO;
import com.exam.module.registration.entity.Registration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class RegistrationExcelService {

    @Autowired
    private RegistrationService registrationService;

    public void export(RegistrationQueryDTO query, HttpServletResponse response) throws Exception {
        if (query.getCurrent() == null) query.setCurrent(1L);
        if (query.getSize() == null || query.getSize() < 100000) query.setSize(100000L);
        PageResult<Registration> page = registrationService.page(query);
        List<Registration> list = page.getRecords();

        List<RegistrationExcelVO> vos = new ArrayList<>(list.size());
        for (Registration r : list) {
            RegistrationExcelVO v = new RegistrationExcelVO();
            v.setRegistrationNo(r.getRegistrationNo());
            v.setStudentName(r.getStudentName());
            v.setStudentIdCard(r.getStudentIdCard());
            v.setPlanName(r.getPlanName());
            v.setCourseName(r.getCourseName());
            v.setExamDate(r.getExamDate());
            v.setStatus(humanizeStatus(r.getStatus()));
            v.setPaymentStatus("PAID".equals(r.getPaymentStatus()) ? "已缴" : "未缴");
            v.setAdmissionTicketNo(r.getAdmissionTicketNo());
            vos.add(v);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String fileName = URLEncoder.encode("报名列表.xlsx", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);
        EasyExcel.write(response.getOutputStream(), RegistrationExcelVO.class)
                .sheet("报名列表").doWrite(vos);
    }

    private String humanizeStatus(String s) {
        if (s == null) return "";
        return switch (s) {
            case "PENDING" -> "待审核";
            case "APPROVED" -> "已通过";
            case "REJECTED" -> "已拒绝";
            default -> s;
        };
    }
}
