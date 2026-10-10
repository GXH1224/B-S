package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.JsonUtils;
import com.ees.common.PageResult;
import com.ees.domain.AlgorithmBaseline;
import com.ees.domain.DataQualityRule;
import com.ees.domain.SysAuditLog;
import com.ees.domain.SysConfig;
import com.ees.repository.DataQualityRuleRepository;
import com.ees.security.SecurityUtils;
import com.ees.service.AuditService;
import com.ees.service.BaselineService;
import com.ees.service.ConfigService;
import com.ees.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 驾驶舱、算法基准、审计日志、系统参数与数据质量规则接口。
 */
@RestController
@RequestMapping("/api")
public class SystemController {

    private final DashboardService dashboardService;
    private final BaselineService baselineService;
    private final AuditService auditService;
    private final ConfigService configService;
    private final DataQualityRuleRepository qualityRuleRepository;

    public SystemController(DashboardService dashboardService, BaselineService baselineService,
                            AuditService auditService, ConfigService configService,
                            DataQualityRuleRepository qualityRuleRepository) {
        this.dashboardService = dashboardService;
        this.baselineService = baselineService;
        this.auditService = auditService;
        this.configService = configService;
        this.qualityRuleRepository = qualityRuleRepository;
    }

    // ------------------------------------------------------------ 综合驾驶舱

    @GetMapping("/dashboard/overview")
    @PreAuthorize("hasAuthority('dashboard:view') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> overview(
            @RequestParam(required = false) Long projectId,
            // 统计区间（含首尾两天），只影响「任务趋势」与「评判等级分布」两张图；都为空时默认近 7 天
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(dashboardService.overview(projectId, from, to));
    }

    // ------------------------------------------------------------ 算法基准

    @GetMapping("/baselines")
    @PreAuthorize("hasAuthority('baseline:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<AlgorithmBaseline>> baselines() {
        return ApiResponse.ok(baselineService.list());
    }

    @PostMapping("/baselines/{id}/run")
    @PreAuthorize("hasAuthority('baseline:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> runBaseline(@PathVariable Long id) {
        Map<String, Object> r = baselineService.run(id);
        return ApiResponse.ok(r, Boolean.TRUE.equals(r.get("passed")) ? "基准测试通过" : "基准测试未通过");
    }

    @PostMapping("/baselines/run-all")
    @PreAuthorize("hasAuthority('baseline:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<Map<String, Object>>> runAllBaselines() {
        List<Map<String, Object>> list = baselineService.runAll();
        long passed = list.stream().filter(m -> Boolean.TRUE.equals(m.get("passed"))).count();
        return ApiResponse.ok(list, "基准测试完成：通过 " + passed + " / " + list.size());
    }

    // ------------------------------------------------------------ 审计日志

    /** GET /api/audit-logs 查询审计日志 */
    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('system:audit:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<SysAuditLog>> auditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(auditService.search(username, module, success, from, to, page, size));
    }

    @PostMapping("/audit-logs/purge")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> purgeAudit(@RequestBody Map<String, Object> body) {
        int days = body.get("retentionDays") instanceof Number n ? n.intValue()
                : configService.getInt("audit.retention.days", 365);
        int removed = auditService.purge(days);
        return ApiResponse.ok(Map.of("removed", removed, "retentionDays", days), "清理完成");
    }

    // ------------------------------------------------------------ 系统参数

    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('system:config:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Map<String, Object>>> configs() {
        return ApiResponse.ok(configService.grouped());
    }

    @PutMapping("/configs/{key}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<SysConfig> updateConfig(@PathVariable String key,
                                               @RequestBody Map<String, String> body) {
        return ApiResponse.ok(configService.update(key, body.get("value"),
                SecurityUtils.currentUsername()), "参数已更新");
    }

    @PutMapping("/configs")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> updateConfigs(@RequestBody Map<String, String> body) {
        configService.updateBatch(body, SecurityUtils.currentUsername());
        return ApiResponse.ok();
    }

    // ------------------------------------------------------------ 数据质量规则

    @GetMapping("/quality-rules")
    public ApiResponse<List<DataQualityRule>> qualityRules(@RequestParam(required = false) Long projectId) {
        return ApiResponse.ok(projectId == null ? qualityRuleRepository.findByEnabledTrueOrderByIdAsc()
                : qualityRuleRepository.findByProjectIdIsNullOrProjectIdOrderByIdAsc(projectId));
    }

    @PutMapping("/quality-rules/{id}")
    @PreAuthorize("hasAuthority('data:rule:manage') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<DataQualityRule> updateQualityRule(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body) {
        DataQualityRule rule = qualityRuleRepository.findById(id).orElse(null);
        if (rule == null) {
            return ApiResponse.fail("质量规则不存在");
        }
        if (body.get("severity") != null) {
            rule.setSeverity(String.valueOf(body.get("severity")));
        }
        if (body.get("enabled") != null) {
            rule.setEnabled(Boolean.valueOf(String.valueOf(body.get("enabled"))));
        }
        if (body.get("description") != null) {
            rule.setDescription(String.valueOf(body.get("description")));
        }
        return ApiResponse.ok(qualityRuleRepository.save(rule), "规则已更新");
    }

    /** 审计日志详情（用于弹窗展示请求摘要） */
    @GetMapping("/audit-logs/{id}")
    @PreAuthorize("hasAuthority('system:audit:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> auditDetail(@PathVariable Long id) {
        SysAuditLog log = auditService.get(id);
        if (log == null) {
            return ApiResponse.fail("审计记录不存在");
        }
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("log", log);
        m.put("detailMap", JsonUtils.toMap(log.getDetail()));
        return ApiResponse.ok(m);
    }
}
