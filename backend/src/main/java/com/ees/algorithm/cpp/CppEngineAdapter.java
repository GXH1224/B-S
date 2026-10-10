package com.ees.algorithm.cpp;

import com.ees.algorithm.AlgorithmEngine;
import com.ees.algorithm.AlgorithmRequest;
import com.ees.algorithm.AlgorithmResult;
import com.ees.common.ErrorCode;
import com.ees.common.JsonUtils;
import com.ees.config.EesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * C++ 独立算法服务适配器（方案 3.3 / 6.1）。
 *
 * <p>把系统统一数据结构转换为 C++ 算法输入，并把输出转换回统一结果结构。
 * C++ 组件作为独立进程/容器运行，通过内网 REST 调用，负责隔离进程崩溃与内存问题，
 * 保留原算法精度与编译链。后端不访问算法内部实现。</p>
 *
 * <p>启用方式：ees.algorithm.engine = CPP_ENGINE</p>
 *
 * <p>协议：POST {cppEngineUrl}/api/algorithm/evaluate
 * 请求体为附录 B.1 统一请求结构，响应体为附录 B.2 统一响应结构。</p>
 */
public class CppEngineAdapter implements AlgorithmEngine {

    private static final Logger log = LoggerFactory.getLogger(CppEngineAdapter.class);

    private final String algorithmCode;
    private final EesProperties properties;
    private final HttpClient httpClient;

    public CppEngineAdapter(String algorithmCode, EesProperties properties) {
        this.algorithmCode = algorithmCode;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String algorithmCode() {
        return algorithmCode;
    }

    @Override
    public String algorithmVersion() {
        return properties.getAlgorithm().getVersion();
    }

    @Override
    public AlgorithmResult evaluate(AlgorithmRequest request) {
        LocalDateTime start = LocalDateTime.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", request.getTaskId());
        payload.put("algorithm", algorithmCode);
        payload.put("algorithmVersion", algorithmVersion());
        payload.put("indicatorVersion", request.getIndicatorVersion());
        payload.put("dataSnapshotId", request.getDataSnapshotId());
        payload.put("parameters", request.getParameters());
        payload.put("inputRef", Map.of("snapshotId",
                request.getDataSnapshotId() == null ? "" : request.getDataSnapshotId()));
        // 大批量输入通过快照编号或受控文件引用传递，不在接口中发送超大 JSON
        payload.put("indicators", request.getIndicators());
        payload.put("objects", request.getObjects());

        String url = properties.getAlgorithm().getCppEngineUrl() + "/api/algorithm/evaluate";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(properties.getAlgorithm().getCppEngineTimeoutSeconds()))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtils.toJson(payload)))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return AlgorithmResult.failure(ErrorCode.ALGORITHM_ENGINE_UNAVAILABLE.name(),
                        "C++ 算法服务返回状态码 " + resp.statusCode());
            }
            AlgorithmResult result = JsonUtils.parse(resp.body(), AlgorithmResult.class);
            if (result == null) {
                return AlgorithmResult.failure(ErrorCode.ALGORITHM_ERROR.name(),
                        "C++ 算法服务返回内容无法解析");
            }
            result.setStartedAt(start);
            result.setFinishedAt(LocalDateTime.now());
            return result;
        } catch (Exception e) {
            log.error("调用 C++ 算法服务失败: {}", e.getMessage());
            return AlgorithmResult.failure(ErrorCode.ALGORITHM_ENGINE_UNAVAILABLE.name(),
                    "C++ 算法服务不可用: " + e.getMessage());
        }
    }
}
