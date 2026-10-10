package com.ees.controller;

import com.ees.algorithm.AlgorithmRegistry;
import com.ees.common.ApiResponse;
import com.ees.config.EesProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查与系统信息（离线部署时用于运维检查关键组件状态）。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final EesProperties properties;
    private final AlgorithmRegistry algorithmRegistry;
    private final javax.sql.DataSource dataSource;

    public HealthController(EesProperties properties, AlgorithmRegistry algorithmRegistry,
                            javax.sql.DataSource dataSource) {
        this.properties = properties;
        this.algorithmRegistry = algorithmRegistry;
        this.dataSource = dataSource;
    }

    /** GET /api/health 健康检查 */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> components = new LinkedHashMap<>();
        boolean dbOk;
        String dbMessage;
        long begin = System.currentTimeMillis();
        try (Connection conn = dataSource.getConnection()) {
            dbOk = conn.isValid(3);
            dbMessage = conn.getMetaData().getDatabaseProductName() + " "
                    + conn.getMetaData().getDatabaseProductVersion();
        } catch (Exception e) {
            dbOk = false;
            dbMessage = e.getMessage();
        }
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("status", dbOk ? "UP" : "DOWN");
        db.put("detail", dbMessage);
        db.put("costMs", System.currentTimeMillis() - begin);
        components.put("database", db);

        Map<String, Object> algo = new LinkedHashMap<>();
        algo.put("status", "UP");
        algo.put("mode", properties.getAlgorithm().getEngine());
        algo.put("engines", algorithmRegistry.supported());
        components.put("algorithm", algo);

        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("status", "UP");
        storage.put("root", properties.getStorage().getRoot());
        components.put("storage", storage);

        boolean allUp = components.values().stream()
                .allMatch(v -> "UP".equals(((Map<?, ?>) v).get("status")));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", allUp ? "UP" : "DEGRADED");
        m.put("application", "efficiency-evaluation-system");
        m.put("offline", true);
        m.put("time", LocalDateTime.now().toString());
        m.put("components", components);
        return ApiResponse.ok(m);
    }

    /** GET /api/info 系统信息（运维只读探针；返回名称/版本/架构/离线标志/算法清单，不含敏感信息） */
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "效能评估系统");
        m.put("version", "1.0.0");
        m.put("architecture", "前后端分离 B/S 架构（Vue 3 + Spring Boot）");
        m.put("offline", true);
        m.put("algorithms", algorithmRegistry.supported());
        m.put("algorithmMode", properties.getAlgorithm().getEngine());
        m.put("serverTime", LocalDateTime.now().toString());
        return ApiResponse.ok(m);
    }
}
