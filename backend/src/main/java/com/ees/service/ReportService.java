package com.ees.service;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.JsonUtils;
import com.ees.common.PageResult;
import com.ees.config.EesProperties;
import com.ees.domain.EvaluationResult;
import com.ees.domain.EvaluationTask;
import com.ees.domain.Project;
import com.ees.domain.ReportRecord;
import com.ees.repository.ReportRecordRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告导出服务（方案 4.6 / 7.1）。
 *
 * <p>支持 EXCEL / WORD / PDF 三种格式，导出内容带项目、任务、版本和生成时间。
 * 全部依赖本地库，运行时不访问互联网，满足离线部署要求。</p>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReportRecordRepository reportRepository;
    private final ResultService resultService;
    private final ProjectService projectService;
    private final EesProperties properties;
    private final com.ees.repository.EvaluationTaskRepository taskRepository;
    private final CodeService codeService;

    public ReportService(ReportRecordRepository reportRepository, ResultService resultService,
                         ProjectService projectService, EesProperties properties,
                         com.ees.repository.EvaluationTaskRepository taskRepository,
                         CodeService codeService) {
        this.reportRepository = reportRepository;
        this.resultService = resultService;
        this.projectService = projectService;
        this.properties = properties;
        this.taskRepository = taskRepository;
        this.codeService = codeService;
    }

    @Transactional(readOnly = true)
    public PageResult<ReportRecord> search(Long projectId, String reportType, Long taskId, int page, int size) {
        Page<ReportRecord> p = reportRepository.search(projectId, blankToNull(reportType), taskId,
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "id")));
        return PageResult.of(p.getContent(), p.getTotalElements(), page, size);
    }

    @Transactional(readOnly = true)
    public ReportRecord get(Long id) {
        return reportRepository.findById(id).orElseThrow(() -> BizException.notFound("导出记录不存在"));
    }

    @Transactional(readOnly = true)
    public File fileOf(Long id) {
        ReportRecord r = get(id);
        if (!"READY".equals(r.getStatus()) || r.getFilePath() == null) {
            throw new BizException(ErrorCode.BUSINESS_ERROR, "导出文件尚未生成完成或已失败");
        }
        File f = new File(r.getFilePath());
        if (!f.exists()) {
            throw new BizException(ErrorCode.NOT_FOUND, "导出文件不存在，可能已被清理");
        }
        return f;
    }

    @Transactional
    public void markDownloaded(Long id) {
        ReportRecord r = get(id);
        r.setDownloadCount((r.getDownloadCount() == null ? 0 : r.getDownloadCount()) + 1);
        reportRepository.save(r);
    }

    /**
     * 生成报告。
     *
     * @param type EXCEL / WORD / PDF
     */
    @Transactional
    public ReportRecord generate(Long taskId, Long resultId, String type, String operator) {
        String t = type == null ? "EXCEL" : type.toUpperCase();
        if (!List.of("EXCEL", "WORD", "PDF", "JSON").contains(t)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不支持的导出格式：" + type);
        }
        EvaluationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> BizException.notFound("评估任务不存在"));
        Project project = projectService.get(task.getProjectId());

        ReportRecord record = new ReportRecord();
        record.setReportCode(codeService.next("RPT", "report_record", "report_code"));
        record.setReportName(task.getTaskName() + " 评估报告");
        record.setTaskId(taskId);
        record.setResultId(resultId);
        record.setProjectId(project.getId());
        record.setReportType(t);
        record.setStatus("GENERATING");
        record.setCreatedBy(operator);
        record.setCreatedAt(LocalDateTime.now());
        reportRepository.save(record);

        try {
            Map<String, Object> payload = resultService.exportPayload(taskId, resultId);
            Path dir = Paths.get(properties.getStorage().getReportDir(),
                    LocalDateTime.now().toLocalDate().toString()).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String ext = switch (t) {
                case "EXCEL" -> "xlsx";
                case "WORD" -> "docx";
                case "PDF" -> "pdf";
                default -> "json";
            };
            String fileName = record.getReportCode() + "_" + sanitize(task.getTaskCode()) + "." + ext;
            Path target = dir.resolve(fileName);

            switch (t) {
                case "EXCEL" -> writeExcel(payload, target.toFile());
                case "WORD" -> writeWord(payload, target.toFile());
                case "PDF" -> writePdf(payload, target.toFile());
                default -> Files.writeString(target, JsonUtils.toPrettyJson(payload));
            }

            record.setFileName(fileName);
            record.setFilePath(target.toString());
            record.setFileSize(Files.size(target));
            record.setStatus("READY");
            reportRepository.save(record);
            log.info("生成报告 {} ({}) 大小 {} 字节", record.getReportCode(), t, record.getFileSize());
        } catch (Exception e) {
            log.error("生成报告失败", e);
            record.setStatus("FAILED");
            record.setErrorMsg(truncate(e.getMessage(), 900));
            reportRepository.save(record);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "生成报告失败：" + e.getMessage(), e);
        }
        return record;
    }

    // ------------------------------------------------------------------ Excel

    private void writeExcel(Map<String, Object> payload, File file) throws IOException {
        EvaluationTask task = (EvaluationTask) payload.get("task");
        Project project = (Project) payload.get("project");
        @SuppressWarnings("unchecked")
        List<EvaluationResult> results = (List<EvaluationResult>) payload.get("results");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detailRows = (List<Map<String, Object>>) payload.get("detailRows");

        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb);

            // Sheet 1: 报告信息
            Sheet info = wb.createSheet("报告信息");
            int r = 0;
            info.createRow(r++).createCell(0).setCellValue("效能评估报告");
            String[][] meta = {
                    {"项目编码", nvl(project.getProjectCode())},
                    {"项目名称", nvl(project.getProjectName())},
                    {"任务编号", nvl(task.getTaskCode())},
                    {"任务名称", nvl(task.getTaskName())},
                    {"评估算法", ResultService.algorithmLabel(task.getAlgorithm())},
                    {"算法版本", nvl(task.getAlgorithmVersion())},
                    {"指标体系版本", nvl(task.getIndicatorVersionCode())},
                    {"数据快照", nvl(task.getSnapshotCode())},
                    {"任务状态", nvl(task.getStatus())},
                    {"提交人", nvl(task.getSubmitBy())},
                    {"开始时间", task.getStartedAt() == null ? "" : task.getStartedAt().format(DTF)},
                    {"结束时间", task.getFinishedAt() == null ? "" : task.getFinishedAt().format(DTF)},
                    {"耗时(ms)", task.getDurationMs() == null ? "" : String.valueOf(task.getDurationMs())},
                    {"对象数量", String.valueOf(results == null ? 0 : results.size())},
                    {"报告生成时间", LocalDateTime.now().format(DTF)},
                    {"生成单位", properties.getReport().getCompany()}
            };
            for (String[] kv : meta) {
                Row row = info.createRow(r++);
                row.createCell(0).setCellValue(kv[0]);
                row.createCell(1).setCellValue(kv[1]);
            }
            info.setColumnWidth(0, 5000);
            info.setColumnWidth(1, 9000);

            // Sheet 2: 对象综合结果
            Sheet sum = wb.createSheet("对象综合结果");
            String[] sumHead = {"排名", "对象编码", "对象名称", "综合得分", "评判等级", "审核状态", "发布状态"};
            Row sh = sum.createRow(0);
            for (int i = 0; i < sumHead.length; i++) {
                sh.createCell(i).setCellValue(sumHead[i]);
                sh.getCell(i).setCellStyle(header);
            }
            int sr = 1;
            if (results != null) {
                for (EvaluationResult res : results) {
                    Row row = sum.createRow(sr++);
                    row.createCell(0).setCellValue(res.getRanking() == null ? 0 : res.getRanking());
                    row.createCell(1).setCellValue(nvl(res.getObjectCode()));
                    row.createCell(2).setCellValue(nvl(res.getObjectName()));
                    row.createCell(3).setCellValue(res.getTotalScore() == null ? 0d
                            : res.getTotalScore().doubleValue());
                    row.createCell(4).setCellValue(nvl(res.getLevel()));
                    row.createCell(5).setCellValue(nvl(res.getReviewed()));
                    row.createCell(6).setCellValue(nvl(res.getPublishStatus()));
                }
            }
            for (int i = 0; i < sumHead.length; i++) {
                sum.setColumnWidth(i, i == 2 ? 6000 : 3600);
            }

            // Sheet 3: 指标明细
            Sheet det = wb.createSheet("指标明细");
            String[] detHead = {"对象编码", "对象名称", "综合得分", "等级", "排名", "指标编码", "指标名称",
                    "单位", "方向", "原始值", "标准化值", "权重", "贡献值", "得分", "单指标等级"};
            Row dh = det.createRow(0);
            for (int i = 0; i < detHead.length; i++) {
                dh.createCell(i).setCellValue(detHead[i]);
                dh.getCell(i).setCellStyle(header);
            }
            int dr = 1;
            if (detailRows != null) {
                for (Map<String, Object> m : detailRows) {
                    Row row = det.createRow(dr++);
                    int c = 0;
                    row.createCell(c++).setCellValue(str(m.get("objectCode")));
                    row.createCell(c++).setCellValue(str(m.get("objectName")));
                    row.createCell(c++).setCellValue(num(m.get("totalScore")));
                    row.createCell(c++).setCellValue(str(m.get("level")));
                    row.createCell(c++).setCellValue(num(m.get("ranking")));
                    row.createCell(c++).setCellValue(str(m.get("indicatorCode")));
                    row.createCell(c++).setCellValue(str(m.get("indicatorName")));
                    row.createCell(c++).setCellValue(str(m.get("unit")));
                    row.createCell(c++).setCellValue(directionLabel(str(m.get("direction"))));
                    row.createCell(c++).setCellValue(num(m.get("rawValue")));
                    row.createCell(c++).setCellValue(num(m.get("normalizedValue")));
                    row.createCell(c++).setCellValue(num(m.get("weight")));
                    row.createCell(c++).setCellValue(num(m.get("contribution")));
                    row.createCell(c++).setCellValue(num(m.get("score")));
                    row.createCell(c).setCellValue(str(m.get("grade")));
                }
            }
            for (int i = 0; i < detHead.length; i++) {
                det.setColumnWidth(i, i == 6 ? 6000 : 3400);
            }
            det.createFreezePane(0, 1);

            try (FileOutputStream out = new FileOutputStream(file)) {
                wb.write(out);
            }
        }
    }

    // ------------------------------------------------------------------ Word

    private void writeWord(Map<String, Object> payload, File file) throws IOException {
        EvaluationTask task = (EvaluationTask) payload.get("task");
        Project project = (Project) payload.get("project");
        @SuppressWarnings("unchecked")
        List<EvaluationResult> results = (List<EvaluationResult>) payload.get("results");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detailRows = (List<Map<String, Object>>) payload.get("detailRows");

        try (XWPFDocument doc = new XWPFDocument()) {
            title(doc, "效能评估报告", 22);
            para(doc, "项目：" + nvl(project.getProjectCode()) + "  " + nvl(project.getProjectName()),
                    ParagraphAlignment.CENTER, false);
            para(doc, "生成时间：" + LocalDateTime.now().format(DTF) + "    生成单位："
                    + properties.getReport().getCompany(), ParagraphAlignment.CENTER, false);

            heading(doc, "一、任务信息");
            XWPFTable info = doc.createTable(7, 4);
            setCell(info, 0, 0, "任务编号");
            setCell(info, 0, 1, nvl(task.getTaskCode()));
            setCell(info, 0, 2, "任务名称");
            setCell(info, 0, 3, nvl(task.getTaskName()));
            setCell(info, 1, 0, "评估算法");
            setCell(info, 1, 1, ResultService.algorithmLabel(task.getAlgorithm()));
            setCell(info, 1, 2, "算法版本");
            setCell(info, 1, 3, nvl(task.getAlgorithmVersion()));
            setCell(info, 2, 0, "指标体系版本");
            setCell(info, 2, 1, nvl(task.getIndicatorVersionCode()));
            setCell(info, 2, 2, "数据快照");
            setCell(info, 2, 3, nvl(task.getSnapshotCode()));
            setCell(info, 3, 0, "提交人");
            setCell(info, 3, 1, nvl(task.getSubmitBy()));
            setCell(info, 3, 2, "任务状态");
            setCell(info, 3, 3, nvl(task.getStatus()));
            setCell(info, 4, 0, "开始时间");
            setCell(info, 4, 1, task.getStartedAt() == null ? "" : task.getStartedAt().format(DTF));
            setCell(info, 4, 2, "结束时间");
            setCell(info, 4, 3, task.getFinishedAt() == null ? "" : task.getFinishedAt().format(DTF));
            setCell(info, 5, 0, "被评估对象数");
            setCell(info, 5, 1, String.valueOf(results == null ? 0 : results.size()));
            setCell(info, 5, 2, "耗时(ms)");
            setCell(info, 5, 3, task.getDurationMs() == null ? "" : String.valueOf(task.getDurationMs()));
            setCell(info, 6, 0, "输入摘要");
            setCell(info, 6, 1, nvl(task.getInputSummary()));
            setCell(info, 6, 2, "参数");
            setCell(info, 6, 3, nvl(task.getParameters()));

            heading(doc, "二、综合评估结果");
            if (results != null && !results.isEmpty()) {
                XWPFTable table = doc.createTable(results.size() + 1, 5);
                String[] head = {"排名", "对象编码", "对象名称", "综合得分", "评判等级"};
                for (int i = 0; i < head.length; i++) {
                    setCell(table, 0, i, head[i]);
                }
                int r = 1;
                for (EvaluationResult res : results) {
                    setCell(table, r, 0, String.valueOf(res.getRanking() == null ? "" : res.getRanking()));
                    setCell(table, r, 1, nvl(res.getObjectCode()));
                    setCell(table, r, 2, nvl(res.getObjectName()));
                    setCell(table, r, 3, res.getTotalScore() == null ? "" : res.getTotalScore().toPlainString());
                    setCell(table, r, 4, nvl(res.getLevel()));
                    r++;
                }
            } else {
                para(doc, "本次任务未产生评估结果。", ParagraphAlignment.LEFT, false);
            }

            heading(doc, "三、指标明细");
            if (detailRows != null && !detailRows.isEmpty()) {
                XWPFTable table = doc.createTable(detailRows.size() + 1, 8);
                String[] head = {"对象", "指标编码", "指标名称", "原始值", "标准化值", "权重", "贡献值", "得分"};
                for (int i = 0; i < head.length; i++) {
                    setCell(table, 0, i, head[i]);
                }
                int r = 1;
                for (Map<String, Object> m : detailRows) {
                    setCell(table, r, 0, str(m.get("objectCode")));
                    setCell(table, r, 1, str(m.get("indicatorCode")));
                    setCell(table, r, 2, str(m.get("indicatorName")));
                    setCell(table, r, 3, str(m.get("rawValue")));
                    setCell(table, r, 4, str(m.get("normalizedValue")));
                    setCell(table, r, 5, str(m.get("weight")));
                    setCell(table, r, 6, str(m.get("contribution")));
                    setCell(table, r, 7, str(m.get("score")));
                    r++;
                }
            } else {
                para(doc, "无指标明细数据。", ParagraphAlignment.LEFT, false);
            }

            heading(doc, "四、可追溯信息");
            para(doc, "本报告由系统按任务执行时的数据快照、指标体系版本、算法版本与参数自动生成，"
                    + "原始数据与标准化数据分别保存，历史结果不因源数据变化而改变。", ParagraphAlignment.LEFT, false);
            para(doc, "数据快照：" + nvl(task.getSnapshotCode()) + "；指标体系版本："
                    + nvl(task.getIndicatorVersionCode()) + "；算法版本：" + nvl(task.getAlgorithmVersion()),
                    ParagraphAlignment.LEFT, false);

            try (FileOutputStream out = new FileOutputStream(file)) {
                doc.write(out);
            }
        }
    }

    // ------------------------------------------------------------------ PDF

    private void writePdf(Map<String, Object> payload, File file) throws IOException {
        EvaluationTask task = (EvaluationTask) payload.get("task");
        Project project = (Project) payload.get("project");
        @SuppressWarnings("unchecked")
        List<EvaluationResult> results = (List<EvaluationResult>) payload.get("results");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detailRows = (List<Map<String, Object>>) payload.get("detailRows");

        BaseFont baseFont = resolveCjkFont();
        Font titleFont = new Font(baseFont, 18, Font.BOLD);
        Font h2 = new Font(baseFont, 13, Font.BOLD);
        Font normal = new Font(baseFont, 10, Font.NORMAL);

        Document document = new Document(PageSize.A4, 48, 48, 56, 48);
        try {
            PdfWriter.getInstance(document, new FileOutputStream(file));
            document.open();
            Paragraph t = new Paragraph("效能评估报告", titleFont);
            t.setAlignment(Element.ALIGN_CENTER);
            document.add(t);
            Paragraph sub = new Paragraph("项目：" + nvl(project.getProjectCode()) + "  "
                    + nvl(project.getProjectName()) + "\n生成时间：" + LocalDateTime.now().format(DTF)
                    + "    生成单位：" + properties.getReport().getCompany(), normal);
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(14f);
            document.add(sub);

            document.add(new Paragraph("一、任务信息", h2));
            PdfPTable info = new PdfPTable(2);
            info.setWidthPercentage(100);
            info.setWidths(new float[]{1f, 2.2f});
            addPdfRow(info, "任务编号", nvl(task.getTaskCode()), normal);
            addPdfRow(info, "任务名称", nvl(task.getTaskName()), normal);
            addPdfRow(info, "评估算法", ResultService.algorithmLabel(task.getAlgorithm()), normal);
            addPdfRow(info, "算法版本", nvl(task.getAlgorithmVersion()), normal);
            addPdfRow(info, "指标体系版本", nvl(task.getIndicatorVersionCode()), normal);
            addPdfRow(info, "数据快照", nvl(task.getSnapshotCode()), normal);
            addPdfRow(info, "任务状态", nvl(task.getStatus()), normal);
            addPdfRow(info, "提交人", nvl(task.getSubmitBy()), normal);
            addPdfRow(info, "开始时间", task.getStartedAt() == null ? "" : task.getStartedAt().format(DTF), normal);
            addPdfRow(info, "结束时间", task.getFinishedAt() == null ? "" : task.getFinishedAt().format(DTF), normal);
            addPdfRow(info, "被评估对象数", String.valueOf(results == null ? 0 : results.size()), normal);
            document.add(info);

            document.add(new Paragraph(" ", normal));
            document.add(new Paragraph("二、综合评估结果", h2));
            if (results != null && !results.isEmpty()) {
                PdfPTable table = new PdfPTable(5);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{0.7f, 1.4f, 2.2f, 1.2f, 1f});
                for (String h : new String[]{"排名", "对象编码", "对象名称", "综合得分", "评判等级"}) {
                    addPdfHeader(table, h, normal);
                }
                for (EvaluationResult res : results) {
                    addPdfCell(table, String.valueOf(res.getRanking() == null ? "" : res.getRanking()), normal);
                    addPdfCell(table, nvl(res.getObjectCode()), normal);
                    addPdfCell(table, nvl(res.getObjectName()), normal);
                    addPdfCell(table, res.getTotalScore() == null ? "" : res.getTotalScore().toPlainString(), normal);
                    addPdfCell(table, nvl(res.getLevel()), normal);
                }
                document.add(table);
            } else {
                document.add(new Paragraph("本次任务未产生评估结果。", normal));
            }

            document.add(new Paragraph(" ", normal));
            document.add(new Paragraph("三、指标明细", h2));
            if (detailRows != null && !detailRows.isEmpty()) {
                PdfPTable table = new PdfPTable(7);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{1.2f, 1.6f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f});
                for (String h : new String[]{"对象", "指标", "原始值", "标准化", "权重", "贡献", "得分"}) {
                    addPdfHeader(table, h, normal);
                }
                int limit = Math.min(detailRows.size(), 800);
                for (int i = 0; i < limit; i++) {
                    Map<String, Object> m = detailRows.get(i);
                    addPdfCell(table, str(m.get("objectCode")), normal);
                    addPdfCell(table, str(m.get("indicatorName")), normal);
                    addPdfCell(table, str(m.get("rawValue")), normal);
                    addPdfCell(table, str(m.get("normalizedValue")), normal);
                    addPdfCell(table, str(m.get("weight")), normal);
                    addPdfCell(table, str(m.get("contribution")), normal);
                    addPdfCell(table, str(m.get("score")), normal);
                }
                document.add(table);
                if (detailRows.size() > limit) {
                    document.add(new Paragraph("（指标明细超过 " + limit + " 行，完整数据请导出 Excel 报表）", normal));
                }
            } else {
                document.add(new Paragraph("无指标明细数据。", normal));
            }

            document.add(new Paragraph(" ", normal));
            document.add(new Paragraph("四、可追溯信息", h2));
            document.add(new Paragraph("数据快照：" + nvl(task.getSnapshotCode())
                    + "；指标体系版本：" + nvl(task.getIndicatorVersionCode())
                    + "；算法版本：" + nvl(task.getAlgorithmVersion()), normal));
            document.add(new Paragraph("本报告由系统按任务执行时的数据快照、指标体系版本、算法版本与参数自动生成，"
                    + "原始数据与标准化数据分别保存，历史结果不因源数据变化而改变。", normal));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "PDF 生成失败：" + e.getMessage(), e);
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }

    /**
     * 解析可用的中文字体：配置路径 → 系统字体 → openpdf-fonts-extra 内置 CJK 字体。
     */
    private BaseFont resolveCjkFont() {
        List<String> candidates = new ArrayList<>();
        if (properties.getReport().getPdfFontPath() != null
                && !properties.getReport().getPdfFontPath().isBlank()) {
            candidates.add(properties.getReport().getPdfFontPath());
        }
        if (properties.getReport().getPdfFontSearchPaths() != null) {
            candidates.addAll(properties.getReport().getPdfFontSearchPaths());
        }
        for (String p : candidates) {
            try {
                if (p != null && new File(p).exists()) {
                    if (p.toLowerCase().endsWith(".ttc")) {
                        return BaseFont.createFont(p + ",0", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    }
                    return BaseFont.createFont(p, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                }
            } catch (Exception e) {
                log.debug("字体 {} 不可用: {}", p, e.getMessage());
            }
        }
        // 使用 openpdf-fonts-extra 提供的中文 CMap（不嵌入，体积小）
        try {
            return BaseFont.createFont(properties.getReport().getPdfFontName(),
                    properties.getReport().getPdfFontEncoding(), BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "未找到可用的中文字体，请在 ees.report.pdf-font-path 中指定 .ttf/.ttc 字体文件路径");
        }
    }

    private static void addPdfHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(new java.awt.Color(230, 236, 245));
        cell.setPadding(4f);
        table.addCell(cell);
    }

    private static void addPdfCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(4f);
        table.addCell(cell);
    }

    private static void addPdfRow(PdfPTable table, String key, String value, Font font) {
        PdfPCell k = new PdfPCell(new Phrase(key, font));
        k.setPadding(4f);
        k.setBackgroundColor(new java.awt.Color(244, 246, 250));
        table.addCell(k);
        PdfPCell v = new PdfPCell(new Phrase(value == null ? "" : value, font));
        v.setPadding(4f);
        table.addCell(v);
    }

    private static void title(XWPFDocument doc, String text, int size) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(size);
    }

    private static void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(13);
    }

    private static void para(XWPFDocument doc, String text, ParagraphAlignment align, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(align);
        XWPFRun run = p.createRun();
        run.setText(text == null ? "" : text);
        run.setBold(bold);
        run.setFontSize(10);
    }

    private static void setCell(XWPFTable table, int row, int col, String value) {
        if (table.getRow(row) == null || table.getRow(row).getCell(col) == null) {
            return;
        }
        var cell = table.getRow(row).getCell(col);
        String text = value == null ? "" : value;
        if (text.length() > 900) {
            text = text.substring(0, 900) + "...";
        }
        cell.setText(text);
    }

    private static CellStyle headerStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private static String directionLabel(String d) {
        if (d == null) {
            return "";
        }
        return switch (d.toUpperCase()) {
            case "POSITIVE" -> "正向";
            case "NEGATIVE" -> "负向";
            case "INTERVAL" -> "区间型";
            default -> d;
        };
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String str(Object o) {
        if (o == null) {
            return "";
        }
        return String.valueOf(o);
    }

    private static double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o == null) {
            return 0d;
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static String sanitize(String s) {
        return s == null ? "report" : s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
