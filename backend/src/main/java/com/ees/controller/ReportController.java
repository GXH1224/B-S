package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.PageResult;
import com.ees.domain.ReportRecord;
import com.ees.security.SecurityUtils;
import com.ees.service.ReportService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 报告与导出接口。导出内容带项目、任务、版本和生成时间。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('report:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<ReportRecord>> search(@RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) String reportType,
                                                        @RequestParam(required = false) Long taskId,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(reportService.search(projectId, reportType, taskId, page, size));
    }

    /** POST /api/reports 按任务生成报告（EXCEL / WORD / PDF） */
    @PostMapping
    @PreAuthorize("hasAuthority('report:create') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> generate(@RequestBody Map<String, Object> body) {
        Long taskId = asLong(body.get("taskId"));
        Long resultId = asLong(body.get("resultId"));
        String type = body.get("type") == null ? "EXCEL" : String.valueOf(body.get("type"));
        ReportRecord record = reportService.generate(taskId, resultId, type, SecurityUtils.currentUsername());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportId", record.getId());
        m.put("reportCode", record.getReportCode());
        m.put("fileName", record.getFileName());
        m.put("reportType", record.getReportType());
        m.put("fileSize", record.getFileSize());
        m.put("downloadUrl", "/api/reports/" + record.getId() + "/download");
        return ApiResponse.ok(m, "报告生成成功");
    }

    /**
     * GET /api/reports/{id}/download 下载报告
     *
     * <p>除报告模块自身的 {@code report:download} 外，也允许持有 {@code result:export} 的账号下载：
     * 「评估结果」页的导出按钮走的是「先 POST /api/evaluation-results/{id}/exports 生成报告、
     * 再下载刚生成的那份文件」两步，而该按钮由 {@code result:export} 授权。
     * 报告导出模块（列表 / 生成）已收紧为仅系统管理员，此处放行不会让评估人员获得
     * 浏览或生成报告的能力，只保证「自己能导出的结果」这条链路不被 403 打断。</p>
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('report:download') or hasAuthority('result:export') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        File file = reportService.fileOf(id);
        reportService.markDownloaded(id);
        String name = file.getName();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .body(new FileSystemResource(file));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('report:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<ReportRecord> get(@PathVariable Long id) {
        return ApiResponse.ok(reportService.get(id));
    }

    private static Long asLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
