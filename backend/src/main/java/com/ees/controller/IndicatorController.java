package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.PageResult;
import com.ees.domain.Indicator;
import com.ees.domain.IndicatorVersion;
import com.ees.security.SecurityUtils;
import com.ees.service.IndicatorService;
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
import java.util.List;
import java.util.Map;

/**
 * 指标体系接入接口（方案 4.3）。系统负责导入、校验、版本化和使用，不负责设计指标体系。
 */
@RestController
@RequestMapping("/api")
public class IndicatorController {

    private final IndicatorService indicatorService;

    public IndicatorController(IndicatorService indicatorService) {
        this.indicatorService = indicatorService;
    }

    @GetMapping("/indicator-versions")
    @PreAuthorize("hasAuthority('indicator:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<IndicatorVersion>> search(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) Long projectId,
                                                           @RequestParam(defaultValue = "1") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(indicatorService.search(keyword, status, projectId, page, size));
    }

    @GetMapping("/indicator-versions/options")
    public ApiResponse<List<Map<String, Object>>> options(@RequestParam(required = false) Long projectId) {
        PageResult<IndicatorVersion> p = indicatorService.search(null, null, projectId, 1, 500);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (IndicatorVersion v : p.getRecords()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", v.getId());
            m.put("versionCode", v.getVersionCode());
            m.put("versionName", v.getVersionName());
            m.put("status", v.getStatus());
            m.put("indicatorCount", v.getIndicatorCount());
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/indicator-versions/{id}")
    @PreAuthorize("hasAuthority('indicator:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<IndicatorVersion> get(@PathVariable Long id) {
        return ApiResponse.ok(indicatorService.get(id));
    }

    /** GET /api/indicator-versions/{id}/indicators 指标列表 */
    @GetMapping("/indicator-versions/{id}/indicators")
    @PreAuthorize("hasAuthority('indicator:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<Indicator>> indicators(@PathVariable Long id) {
        return ApiResponse.ok(indicatorService.indicators(id));
    }

    /** GET /api/indicator-versions/{id}/tree 指标树 */
    @GetMapping("/indicator-versions/{id}/tree")
    @PreAuthorize("hasAuthority('indicator:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<Map<String, Object>>> tree(@PathVariable Long id) {
        return ApiResponse.ok(indicatorService.indicatorTree(id));
    }

    /** POST /api/indicator-versions/import 导入并校验指标体系版本 */
    @PostMapping(value = "/indicator-versions/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('indicator:import') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(required = false) Long projectId,
                                                      @RequestParam(required = false) String versionCode,
                                                      @RequestParam(required = false) String versionName,
                                                      @RequestParam(required = false) String description) {
        Map<String, Object> r = indicatorService.importFromFile(file, projectId, versionCode, versionName,
                description, SecurityUtils.currentUsername());
        return ApiResponse.ok(r, Boolean.TRUE.equals(r.get("success")) ? "导入成功" : "导入完成，存在错误行");
    }

    @PostMapping("/indicator-versions/{id}/revalidate")
    @PreAuthorize("hasAuthority('indicator:import') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> revalidate(@PathVariable Long id) {
        return ApiResponse.ok(indicatorService.revalidate(id), "校验完成");
    }

    /** POST /api/indicator-versions/{id}/publish 发布版本 */
    @PostMapping("/indicator-versions/{id}/publish")
    @PreAuthorize("hasAuthority('indicator:import') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<IndicatorVersion> publish(@PathVariable Long id) {
        return ApiResponse.ok(indicatorService.publish(id, SecurityUtils.currentUsername()), "版本已发布");
    }

    @PostMapping("/indicator-versions/{id}/archive")
    @PreAuthorize("hasAuthority('indicator:import') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        indicatorService.archive(id, SecurityUtils.currentUsername());
        return ApiResponse.ok();
    }

    @DeleteMapping("/indicator-versions/{id}")
    @PreAuthorize("hasAuthority('indicator:import') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        indicatorService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/indicator-versions/import-batches")
    @PreAuthorize("hasAuthority('indicator:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<Map<String, Object>>> importBatches(@RequestParam(required = false) Long versionId) {
        return ApiResponse.ok(indicatorService.importBatches(versionId));
    }

    /** GET /api/indicator-versions/template 下载指标体系导入模板 */
    @GetMapping("/indicator-versions/template")
    public ResponseEntity<Resource> template() {
        byte[] bytes = ExcelTemplateWriter.indicatorTemplate();
        String name = "指标体系导入模板.xlsx";
        return download(bytes, name, MediaType.APPLICATION_OCTET_STREAM);
    }

    private ResponseEntity<Resource> download(byte[] bytes, String name, MediaType type) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentType(type)
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
