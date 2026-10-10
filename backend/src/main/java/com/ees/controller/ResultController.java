package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.PageResult;
import com.ees.domain.EvaluationResult;
import com.ees.domain.ResultDetail;
import com.ees.security.SecurityUtils;
import com.ees.service.ReportService;
import com.ees.service.ResultService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评估结果接口（方案 7.1 三类展示、7.2 八类图表）。
 */
@RestController
@RequestMapping("/api/evaluation-results")
public class ResultController {

    private final ResultService resultService;
    private final ReportService reportService;

    public ResultController(ResultService resultService, ReportService reportService) {
        this.resultService = resultService;
        this.reportService = reportService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('result:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<EvaluationResult>> search(@RequestParam(required = false) Long projectId,
                                                            @RequestParam(required = false) Long taskId,
                                                            @RequestParam(required = false) String algorithm,
                                                            @RequestParam(required = false) String level,
                                                            @RequestParam(required = false) String publishStatus,
                                                            @RequestParam(required = false) String objectCode,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(resultService.search(projectId, taskId, algorithm, level, publishStatus,
                objectCode, page, size));
    }

    /** 按任务返回全部对象结果（排名条形图 / 对比表数据源） */
    @GetMapping("/by-task/{taskId}")
    @PreAuthorize("hasAuthority('result:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<EvaluationResult>> byTask(@PathVariable Long taskId) {
        return ApiResponse.ok(resultService.byTask(taskId));
    }

    /** GET /api/evaluation-results/{id} 查看完整结果（文本 + 表格 + 图形） */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('result:view') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> fullView(@PathVariable Long id,
                                                     @RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(resultService.fullView(id, projectId));
    }

    /** 展示形式一：文本结论 */
    @GetMapping("/{id}/text")
    @PreAuthorize("hasAuthority('result:view') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> textView(@PathVariable Long id) {
        return ApiResponse.ok(resultService.textView(id));
    }

    /** 展示形式二：表格明细（支持排序、筛选、分页） */
    @GetMapping("/{id}/details")
    @PreAuthorize("hasAuthority('result:view') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<ResultDetail>> details(@PathVariable Long id,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String sortBy,
                                                         @RequestParam(defaultValue = "asc") String order,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(resultService.details(id, keyword, sortBy, order, page, size));
    }

    /** 展示形式三：图形数据。chartType 为 bar/line/pie/radar/scatter/heatmap/boxplot/rankBar/all */
    @GetMapping("/charts")
    @PreAuthorize("hasAuthority('result:view') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> charts(@RequestParam(required = false) Long taskId,
                                                   @RequestParam(required = false) Long resultId,
                                                   @RequestParam(required = false) Long projectId,
                                                   @RequestParam(defaultValue = "all") String chartType) {
        Map<String, Object> all = resultService.buildAllCharts(taskId, resultId, projectId);
        String type = chartType == null ? "all" : chartType;
        if ("all".equalsIgnoreCase(type)) {
            return ApiResponse.ok(all);
        }
        Object one = all.get(type);
        if (one == null) {
            return ApiResponse.fail("不支持的图形类型：" + chartType
                    + "，支持 bar/line/pie/radar/scatter/heatmap/boxplot/rankBar/all");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(type, one);
        return ApiResponse.ok(m);
    }

    @GetMapping("/charts/{chartType}")
    @PreAuthorize("hasAuthority('result:view') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> chart(@PathVariable String chartType,
                                                  @RequestParam(required = false) Long taskId,
                                                  @RequestParam(required = false) Long resultId,
                                                  @RequestParam(required = false) Long projectId) {
        Map<String, Object> all = resultService.buildAllCharts(taskId, resultId, projectId);
        Object one = all.get(chartType);
        if (one == null) {
            return ApiResponse.fail("不支持的图形类型：" + chartType);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(chartType, one);
        return ApiResponse.ok(m);
    }

    /** 横向对比 */
    @GetMapping("/compare")
    @PreAuthorize("hasAuthority('result:compare') or hasAuthority('result:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> compare(@RequestParam(required = false) Long projectId,
                                                    @RequestParam(required = false) Long taskId) {
        return ApiResponse.ok(resultService.compare(projectId, taskId));
    }

    /** 结果复核 */
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAuthority('result:review') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<EvaluationResult> review(@PathVariable Long id,
                                                @RequestBody Map<String, String> body) {
        return ApiResponse.ok(resultService.review(id, body.get("action"), body.get("comment"),
                SecurityUtils.currentUsername()), "复核操作完成");
    }

    /** 结果发布 */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('result:publish') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<EvaluationResult> publish(@PathVariable Long id) {
        return ApiResponse.ok(resultService.publish(id, SecurityUtils.currentUsername()), "结果已发布");
    }

    /** 结果归档 */
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('result:publish') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<EvaluationResult> archive(@PathVariable Long id) {
        return ApiResponse.ok(resultService.archive(id, SecurityUtils.currentUsername()), "结果已归档");
    }

    /** POST /api/evaluation-results/{id}/exports 生成报告或表格文件 */
    @PostMapping("/{id}/exports")
    @PreAuthorize("hasAuthority('result:export') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> exportResult(@PathVariable Long id,
                                                         @RequestBody Map<String, String> body) {
        EvaluationResult r = resultService.get(id);
        String type = body.getOrDefault("type", "EXCEL");
        var record = reportService.generate(r.getTaskId(), id, type, SecurityUtils.currentUsername());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportId", record.getId());
        m.put("reportCode", record.getReportCode());
        m.put("fileName", record.getFileName());
        m.put("reportType", record.getReportType());
        m.put("fileSize", record.getFileSize());
        m.put("downloadUrl", "/api/reports/" + record.getId() + "/download");
        return ApiResponse.ok(m, "导出文件生成成功");
    }
}
