package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.PageResult;
import com.ees.domain.DataBatch;
import com.ees.domain.RawDataRecord;
import com.ees.security.SecurityUtils;
import com.ees.service.DataBatchService;
import com.ees.util.ExcelTemplateWriter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据接入接口（文件导入 + 数据库抽取结果管理）。
 */
@RestController
@RequestMapping("/api/data-batches")
public class DataBatchController {

    private final DataBatchService dataBatchService;

    public DataBatchController(DataBatchService dataBatchService) {
        this.dataBatchService = dataBatchService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('data:batch:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<DataBatch>> search(@RequestParam(required = false) Long projectId,
                                                     @RequestParam(required = false) String sourceType,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(dataBatchService.search(projectId, sourceType, status, keyword, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('data:batch:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<DataBatch> get(@PathVariable Long id) {
        return ApiResponse.ok(dataBatchService.get(id));
    }

    /** POST /api/data-batches/import 上传文件并生成数据批次 */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('data:batch:import') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) Long targetVersionId) {
        Map<String, Object> r = dataBatchService.importFile(file, projectId, targetVersionId,
                SecurityUtils.currentUsername());
        return ApiResponse.ok(r, Boolean.TRUE.equals(r.get("blocking")) ? "导入完成，存在阻断级错误" : "导入成功");
    }

    @GetMapping("/{id}/records")
    @PreAuthorize("hasAuthority('data:batch:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<RawDataRecord>> records(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(dataBatchService.records(id, limit));
    }

    @GetMapping("/{id}/errors")
    @PreAuthorize("hasAuthority('data:batch:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<Map<String, Object>>> errors(@PathVariable Long id) {
        return ApiResponse.ok(dataBatchService.errorRows(id));
    }

    @GetMapping("/{id}/quality-report")
    @PreAuthorize("hasAuthority('data:batch:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> qualityReport(@PathVariable Long id) {
        return ApiResponse.ok(dataBatchService.qualityReport(id));
    }

    @PostMapping("/{id}/revalidate")
    @PreAuthorize("hasAuthority('data:batch:validate') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<DataBatch> revalidate(@PathVariable Long id) {
        return ApiResponse.ok(dataBatchService.revalidate(id), "校验完成");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('data:batch:delete') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dataBatchService.delete(id, SecurityUtils.currentUsername());
        return ApiResponse.ok();
    }

    /** GET /api/data-batches/template 下载标准数据导入模板 */
    @GetMapping("/template")
    public ResponseEntity<Resource> template() {
        byte[] bytes = ExcelTemplateWriter.dataTemplate();
        return download(bytes, "试验数据导入模板.xlsx");
    }

    /** GET /api/data-batches/{id}/errors/export 下载错误明细 */
    @GetMapping("/{id}/errors/export")
    @PreAuthorize("hasAuthority('data:batch:list') or hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<Resource> exportErrors(@PathVariable Long id) {
        List<Map<String, Object>> errors = dataBatchService.errorRows(id);
        String[] headers = {"行号", "项目编码", "对象编码", "数据时间", "指标编码", "指标值", "单位", "错误说明"};
        List<Map<String, String>> rows = new ArrayList<>();
        for (Map<String, Object> e : errors) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("行号", str(e.get("rowNo")));
            m.put("项目编码", str(e.get("projectCode")));
            m.put("对象编码", str(e.get("objectCode")));
            m.put("数据时间", str(e.get("dataTime")));
            m.put("指标编码", str(e.get("indicatorCode")));
            m.put("指标值", str(e.get("indicatorValue")));
            m.put("单位", str(e.get("unit")));
            m.put("错误说明", str(e.get("errorMsg")));
            rows.add(m);
        }
        DataBatch batch = dataBatchService.get(id);
        byte[] bytes = ExcelTemplateWriter.errorDetail(rows, headers);
        return download(bytes, "错误明细_" + batch.getBatchCode() + ".xlsx");
    }

    private ResponseEntity<Resource> download(byte[] bytes, String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
