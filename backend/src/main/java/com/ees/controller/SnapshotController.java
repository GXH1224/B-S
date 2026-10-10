package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.PageResult;
import com.ees.domain.DataSnapshot;
import com.ees.security.SecurityUtils;
import com.ees.service.SnapshotService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据快照接口（方案 5.2）。快照在任务执行后保持不变。
 */
@RestController
@RequestMapping("/api/snapshots")
public class SnapshotController {

    private final SnapshotService snapshotService;

    public SnapshotController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('data:snapshot:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<DataSnapshot>> search(@RequestParam(required = false) Long projectId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(snapshotService.search(projectId, status, keyword, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('data:snapshot:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<DataSnapshot> get(@PathVariable Long id) {
        return ApiResponse.ok(snapshotService.get(id));
    }

    @GetMapping("/{id}/overview")
    @PreAuthorize("hasAuthority('data:snapshot:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> overview(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "20") int maxObjects) {
        return ApiResponse.ok(snapshotService.overview(id, maxObjects));
    }

    @GetMapping("/{id}/values")
    @PreAuthorize("hasAuthority('data:snapshot:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<com.ees.domain.SnapshotValue>> values(@PathVariable Long id) {
        return ApiResponse.ok(snapshotService.values(id));
    }

    /** POST /api/snapshots 由数据批次生成评估数据快照 */
    @PostMapping
    @PreAuthorize("hasAuthority('data:snapshot:create') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long batchId = asLong(body.get("batchId"));
        Long versionId = asLong(body.get("indicatorVersionId"));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        Map<String, Object> r = snapshotService.createFromBatch(batchId, versionId,
                SecurityUtils.currentUsername(), remark);
        return ApiResponse.ok(r, "数据快照生成成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('data:snapshot:create') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        snapshotService.delete(id);
        return ApiResponse.ok();
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
