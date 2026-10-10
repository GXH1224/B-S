package com.ees.controller;

import com.ees.common.ApiResponse;
import com.ees.common.PageResult;
import com.ees.domain.DataSourceEntity;
import com.ees.security.SecurityUtils;
import com.ees.service.DataSourceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 外部数据源（甲方业务库）只读接入接口。
 */
@RestController
@RequestMapping("/api/data-sources")
public class DataSourceController {

    private final DataSourceService dataSourceService;

    public DataSourceController(DataSourceService dataSourceService) {
        this.dataSourceService = dataSourceService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('data:source:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<PageResult<DataSourceEntity>> search(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String dbType,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(dataSourceService.search(keyword, dbType, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('data:source:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<DataSourceEntity> get(@PathVariable Long id) {
        return ApiResponse.ok(dataSourceService.getMasked(id));
    }

    /** 支持接入的数据库类型 */
    @GetMapping("/db-types")
    public ApiResponse<List<Map<String, String>>> dbTypes() {
        List<Map<String, String>> out = new java.util.ArrayList<>();
        String[][] types = {
                {"MYSQL", "MySQL", "com.mysql.cj.jdbc.Driver",
                        "jdbc:mysql://host:3306/db?useSSL=false&serverTimezone=Asia/Shanghai"},
                {"POSTGRESQL", "PostgreSQL", "org.postgresql.Driver", "jdbc:postgresql://host:5432/db"},
                {"ORACLE", "Oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@host:1521:ORCL"},
                {"SQLSERVER", "SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                        "jdbc:sqlserver://host:1433;databaseName=db"},
                {"DM", "达梦 DM", "dm.jdbc.driver.DmDriver", "jdbc:dm://host:5236"},
                {"KINGBASE", "人大金仓 KingbaseES", "com.kingbase8.Driver", "jdbc:kingbase8://host:54321/db"},
                {"JDBC", "通用 JDBC", "", "jdbc:..."}
        };
        for (String[] t : types) {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put("dbType", t[0]);
            m.put("label", t[1]);
            m.put("driverClass", t[2]);
            m.put("urlExample", t[3]);
            out.add(m);
        }
        return ApiResponse.ok(out);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('data:source:create') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<DataSourceEntity> create(@RequestBody DataSourceEntity entity) {
        entity.setId(null);
        DataSourceEntity saved = dataSourceService.save(entity, SecurityUtils.currentUsername());
        saved.setDbPassword("******");
        return ApiResponse.ok(saved, "数据源创建成功");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('data:source:update') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<DataSourceEntity> update(@PathVariable Long id, @RequestBody DataSourceEntity entity) {
        entity.setId(id);
        DataSourceEntity saved = dataSourceService.save(entity, SecurityUtils.currentUsername());
        saved.setDbPassword("******");
        return ApiResponse.ok(saved, "数据源更新成功");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('data:source:update') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dataSourceService.delete(id, SecurityUtils.currentUsername());
        return ApiResponse.ok();
    }

    /** POST /api/data-sources/{id}/test 连接测试 */
    @PostMapping("/{id}/test")
    @PreAuthorize("hasAuthority('data:source:test') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id) {
        Map<String, Object> r = dataSourceService.testConnection(id);
        return ApiResponse.ok(r, Boolean.TRUE.equals(r.get("success")) ? "连接成功" : "连接失败");
    }

    /** GET /api/data-sources/{id}/tables 表清单 */
    @GetMapping("/{id}/tables")
    @PreAuthorize("hasAuthority('data:source:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<String>> tables(@PathVariable Long id,
                                            @RequestParam(required = false) String schema) {
        return ApiResponse.ok(dataSourceService.listTables(id, schema));
    }

    /** GET /api/data-sources/{id}/fields?table=xxx 字段预览 */
    @GetMapping("/{id}/fields")
    @PreAuthorize("hasAuthority('data:source:list') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<List<Map<String, Object>>> fields(@PathVariable Long id,
                                                        @RequestParam String table) {
        return ApiResponse.ok(dataSourceService.previewFields(id, table));
    }

    /** POST /api/data-sources/{id}/extract 按受限配置抽取数据 */
    @PostMapping("/{id}/extract")
    @PreAuthorize("hasAuthority('data:source:extract') or hasRole('SYSTEM_ADMIN')")
    public ApiResponse<Map<String, Object>> extract(@PathVariable Long id,
                                                    @RequestBody Map<String, Object> body) {
        String sql = body.get("querySql") == null ? null : String.valueOf(body.get("querySql"));
        Long projectId = asLong(body.get("projectId"));
        Long targetVersionId = asLong(body.get("targetVersionId"));
        return ApiResponse.ok(dataSourceService.extract(id, sql, projectId, targetVersionId,
                SecurityUtils.currentUsername()), "数据抽取完成");
    }

    /** POST /api/data-sources/validate-sql SQL 只读校验（页面即时校验） */
    @PostMapping("/validate-sql")
    public ApiResponse<Map<String, Object>> validateSql(@RequestBody Map<String, String> body) {
        String sql = body.get("querySql");
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        try {
            String normalized = dataSourceService.validateReadOnlySql(sql);
            m.put("valid", true);
            m.put("normalizedSql", normalized);
            m.put("message", "SQL 校验通过（只读）");
        } catch (Exception e) {
            m.put("valid", false);
            m.put("message", e.getMessage());
        }
        return ApiResponse.ok(m);
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
