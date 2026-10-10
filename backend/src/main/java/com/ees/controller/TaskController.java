package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.PageResult;
import com.ees.domain.EvaluationTask;
import com.ees.security.SecurityUtils;
import com.ees.service.TaskService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 评估任务接口（方案 4.5 / 8.1 接口约定）。
 */
@RestController
@RequestMapping("/api/evaluation-tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('task:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<EvaluationTask>> search(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String algorithm,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(taskService.search(projectId, algorithm, status, keyword, from, to, page, size));
    }

    @GetMapping("/recent")
    public ApiResponse<List<EvaluationTask>> recent(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(taskService.recent(limit));
    }

    /** POST /api/evaluation-tasks 创建评估任务 */
    @PostMapping
    @PreAuthorize("hasAuthority('task:create') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = taskService.create(body, SecurityUtils.currentUsername(),
                SecurityUtils.currentUserId());
        return ApiResponse.ok(r, "任务已提交，正在异步执行");
    }

    /** GET /api/evaluation-tasks/{id} 查询任务状态和结果摘要 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(taskService.detail(id));
    }

    @GetMapping("/{id}/status")
    @PreAuthorize("hasAuthority('task:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> status(@PathVariable Long id) {
        EvaluationTask t = taskService.get(id);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("taskId", t.getId());
        m.put("taskCode", t.getTaskCode());
        m.put("status", t.getStatus());
        m.put("progress", t.getProgress());
        m.put("currentStep", t.getCurrentStep());
        m.put("errorCode", t.getErrorCode());
        m.put("errorMsg", t.getErrorMsg());
        m.put("resultCount", t.getResultCount());
        m.put("durationMs", t.getDurationMs());
        m.put("startedAt", t.getStartedAt());
        m.put("finishedAt", t.getFinishedAt());
        return ApiResponse.ok(m);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('task:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<com.ees.domain.TaskStatusHistory>> history(@PathVariable Long id) {
        return ApiResponse.ok(taskService.history(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('task:cancel') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> cancel(@PathVariable Long id) {
        taskService.cancel(id, SecurityUtils.currentUsername(), SecurityUtils.currentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('task:retry') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> retry(@PathVariable Long id) {
        return ApiResponse.ok(taskService.retry(id, SecurityUtils.currentUsername(),
                SecurityUtils.currentUserId()), "已生成新的执行实例");
    }
}
