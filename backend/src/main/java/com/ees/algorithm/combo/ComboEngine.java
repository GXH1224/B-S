package com.ees.algorithm.combo;

import com.ees.algorithm.AlgorithmEngine;
import com.ees.algorithm.AlgorithmRegistry;
import com.ees.algorithm.AlgorithmRequest;
import com.ees.algorithm.AlgorithmResult;
import com.ees.algorithm.AlgorithmType;
import com.ees.common.ErrorCode;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组合评估流程（方案 6.5）。
 *
 * <p>典型用法：先用 AHP 计算指标权重，再把权重交给加权平均法或模糊综合评判。
 * 各阶段输入输出全部保存在任务结果的 extra 中，保证可复算。</p>
 *
 * <pre>
 * parameters.comboFlow = ["AHP", "WEIGHTED"]  或  ["AHP", "FUZZY"]
 * </pre>
 */
@Component
public class ComboEngine implements AlgorithmEngine {

    public static final String CODE = "COMBO";

    private final AlgorithmRegistry registry;

    public ComboEngine(@Lazy AlgorithmRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String algorithmCode() {
        return CODE;
    }

    @Override
    public String algorithmVersion() {
        return "java-combo-1.0.0";
    }

    @Override
    public AlgorithmResult evaluate(AlgorithmRequest request) {
        LocalDateTime start = LocalDateTime.now();
        List<String> flow = resolveFlow(request);
        if (flow.size() < 2) {
            AlgorithmResult fail = AlgorithmResult.failure(ErrorCode.ALGORITHM_PARAM_INVALID.name(),
                    "组合流程至少需要两个阶段，例如 [\"AHP\",\"WEIGHTED\"]");
            fail.setStartedAt(start);
            return fail;
        }

        Map<String, Object> stages = new LinkedHashMap<>();
        AlgorithmResult last = null;
        Map<String, Double> ahpWeights = null;

        for (int i = 0; i < flow.size(); i++) {
            String code = flow.get(i).toUpperCase();
            if (CODE.equals(code)) {
                AlgorithmResult fail = AlgorithmResult.failure(ErrorCode.ALGORITHM_PARAM_INVALID.name(),
                        "组合流程不能嵌套 COMBO");
                fail.setStartedAt(start);
                return fail;
            }
            AlgorithmEngine engine = registry.get(code);

            if (AlgorithmType.AHP.getCode().equals(code)) {
                AlgorithmResult ahpResult = engine.evaluate(request);
                stages.put("stage" + (i + 1) + "_" + code, ahpResult);
                if (!ahpResult.isSuccess()) {
                    return propagateFailure(ahpResult, stages, start);
                }
                ahpWeights = extractWeights(ahpResult);
                if (ahpWeights.isEmpty()) {
                    AlgorithmResult fail = AlgorithmResult.failure(ErrorCode.ALGORITHM_ERROR.name(),
                            "AHP 阶段未产生可用权重，无法继续组合流程");
                    fail.setStartedAt(start);
                    fail.setExtra(stages);
                    return fail;
                }
                // 把 AHP 权重注入指标元数据，供后续阶段使用
                for (AlgorithmRequest.IndicatorMeta m : request.getIndicators()) {
                    Double w = ahpWeights.get(m.getCode());
                    if (w != null) {
                        m.setWeight(w);
                    }
                }
                last = ahpResult;
            } else {
                AlgorithmResult r = engine.evaluate(request);
                stages.put("stage" + (i + 1) + "_" + code, r);
                if (!r.isSuccess()) {
                    return propagateFailure(r, stages, start);
                }
                last = r;
            }
        }

        if (last == null) {
            AlgorithmResult fail = AlgorithmResult.failure(ErrorCode.ALGORITHM_ERROR.name(), "组合流程未产生结果");
            fail.setStartedAt(start);
            return fail;
        }

        // 输出最后阶段的评估结果，同时保留全部阶段输入输出
        AlgorithmResult out = new AlgorithmResult();
        out.setTaskId(request.getTaskId());
        out.setAlgorithm(CODE);
        out.setAlgorithmVersion(algorithmVersion());
        out.setIndicatorVersion(request.getIndicatorVersion());
        out.setDataSnapshotId(request.getDataSnapshotId());
        out.setSuccess(true);
        out.setObjectResults(last.getObjectResults());
        out.setWarnings(last.getWarnings());
        out.setScore(last.getScore());
        out.setLevel(last.getLevel());
        out.setStartedAt(start);
        out.setFinishedAt(LocalDateTime.now());
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("comboFlow", flow);
        extra.put("stages", stages);
        if (ahpWeights != null) {
            extra.put("ahpWeights", ahpWeights);
        }
        extra.putAll(last.getExtra() == null ? Map.of() : last.getExtra());
        out.setExtra(extra);
        return out;
    }

    private AlgorithmResult propagateFailure(AlgorithmResult r, Map<String, Object> stages, LocalDateTime start) {
        AlgorithmResult fail = AlgorithmResult.failure(
                r.getErrorCode() == null ? ErrorCode.ALGORITHM_ERROR.name() : r.getErrorCode(),
                "组合流程阶段失败：" + r.getError());
        fail.setStartedAt(start);
        fail.setExtra(stages);
        return fail;
    }

    @SuppressWarnings("unchecked")
    private List<String> resolveFlow(AlgorithmRequest request) {
        List<String> flow = new ArrayList<>();
        Object v = request.param("comboFlow");
        if (v == null) {
            v = request.param("flow");
        }
        if (v instanceof List<?> l) {
            for (Object o : l) {
                flow.add(String.valueOf(o));
            }
        } else if (v instanceof String s && !s.isBlank()) {
            for (String p : s.split("[,>\\-]+")) {
                if (!p.isBlank()) {
                    flow.add(p.trim());
                }
            }
        }
        if (flow.isEmpty()) {
            flow.add("AHP");
            flow.add("WEIGHTED");
        }
        return flow;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> extractWeights(AlgorithmResult r) {
        Map<String, Double> out = new LinkedHashMap<>();
        Object cw = r.getExtra() == null ? null : r.getExtra().get("compositeWeights");
        if (cw instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                if (v instanceof Number n) {
                    out.put(String.valueOf(k), n.doubleValue());
                }
            });
        }
        if (out.isEmpty() && !r.getObjectResults().isEmpty()) {
            out.putAll(r.getObjectResults().get(0).getWeights());
        }
        return out;
    }
}
