package com.exam.module.registration.service;

import com.exam.common.BizException;
import com.exam.module.registration.entity.Registration;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class TicketPdfService {

    @Autowired
    private RegistrationService registrationService;

    public void exportTicket(Long registrationId, HttpServletResponse response) throws Exception {
        // 先查主库（新报名），再 fan-out 分片（历史报名）
        Registration r = registrationService.detail(registrationId);
        if (r == null) throw new BizException("报名记录不存在");
        if (!"APPROVED".equals(r.getStatus())) throw new BizException("仅审核通过的报名可下载准考证");

        BaseFont bfChinese = BaseFont.createFont("STSongStd-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        Font titleFont = new Font(bfChinese, 22, Font.BOLD);
        Font subtitleFont = new Font(bfChinese, 14, Font.BOLD);
        Font normalFont = new Font(bfChinese, 12, Font.NORMAL);
        Font tipFont = new Font(bfChinese, 10, Font.ITALIC, BaseColor.GRAY);

        response.setContentType("application/pdf");
        String fileName = URLEncoder.encode("准考证_" + r.getRegistrationNo() + ".pdf", StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);

        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        try (OutputStream out = response.getOutputStream()) {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Paragraph title = new Paragraph("省考试院自学考试 准考证", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            doc.add(title);

            Paragraph subtitle = new Paragraph("Admission Ticket", subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(30);
            doc.add(subtitle);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(85);
            table.setWidths(new float[]{1f, 2.5f});
            table.setSpacingBefore(10);
            table.setSpacingAfter(20);

            addRow(table, "准考证号", r.getAdmissionTicketNo(), normalFont);
            addRow(table, "报名编号", r.getRegistrationNo(), normalFont);
            addRow(table, "姓 名",   r.getStudentName(), normalFont);
            addRow(table, "身份证号", r.getStudentIdCard(), normalFont);
            addRow(table, "考试名称", r.getPlanName(), normalFont);
            addRow(table, "考试科目", r.getCourseName(), normalFont);
            addRow(table, "考试日期", r.getExamDate(), normalFont);
            addRow(table, "考试时间", (r.getStartTime() == null ? "" : r.getStartTime()) + " — " + (r.getEndTime() == null ? "" : r.getEndTime()), normalFont);
            addRow(table, "考试地点", r.getExamLocation(), normalFont);

            doc.add(table);

            Paragraph notice = new Paragraph("注意事项：\n  1. 考前30分钟到达考场，凭本证及有效身份证件入场；\n  2. 严禁携带任何通讯工具进入考场；\n  3. 请妥善保管本证，遗失自行承担责任。", tipFont);
            notice.setSpacingBefore(20);
            doc.add(notice);

            Paragraph stamp = new Paragraph("\n\n省考试院 (公章)", normalFont);
            stamp.setAlignment(Element.ALIGN_RIGHT);
            doc.add(stamp);

            doc.close();
        }
    }

    private void addRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell c1 = new PdfPCell(new Phrase(label, font));
        c1.setBackgroundColor(new BaseColor(240, 245, 255));
        c1.setPadding(8);
        c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(value == null ? "" : value, font));
        c2.setPadding(8);
        table.addCell(c2);
    }
}
