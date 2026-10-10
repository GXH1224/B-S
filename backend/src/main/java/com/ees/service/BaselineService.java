package com.ees.service;

import com.ees.algorithm.AlgorithmRegistry;
import com.ees.algorithm.AlgorithmRequest;
import com.ees.algorithm.AlgorithmResult;
import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.common.JsonUtils;
import com.ees.domain.AlgorithmBaseline;
import com.ees.repository.AlgorithmBaselineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 算法基准测试服务（方案 6.6）。
 *
 * <p>使用甲方提供的标准样例验证算法封装和统一接口转换结果。
 * 浮点结果按双方确认的绝对误差或相对误差进行判断，所有差异都有测试记录。</p>
 */
@Service
public class BaselineService {

    private static final Logger log = LoggerFactory.getLogger(BaselineService.class);

    private final AlgorithmBaselineRepository repository;
    private final AlgorithmRegistry registry;

    public BaselineService(AlgorithmBaselineRepository repository, AlgorithmRegistry registry) {
        this.repository = repository;
        this.registry = registry;
    }

    @Transactional(readOnly = true)
    public List<AlgorithmBaseline> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public AlgorithmBaseline get(Long id) {
        return repository.findById(id).orElseThrow(() -> BizException.notFound("基准样例不存在"));
    }

    /** 运行单个基准样例并与预期结果比对。 */
    @Transactional
    public Map<String, Object> run(Long id) {
        AlgorithmBaseline baseline = get(id);
        AlgorithmRequest request = new AlgorithmRequest();
        request.setTaskId("BASELINE-" + baseline.getBaselineCode());
        request.setAlgorithm(baseline.getAlgorithm());
        Map<String, Object> input = JsonUtils.toMap(baseline.getInputPayload());
        request.setParameters(input);
        request.setRaw(input);
        request.setCrThreshold(0.1);
        request.setScoreScale(8);

        AlgorithmResult result = registry.get(baseline.getAlgorithm()).evaluate(request);
        Map<String, Object> expected = JsonUtils.toMap(baseline.getExpectedPayload());
        double absTol = baseline.getAbsTolerance() == null ? 0.0001 : baseline.getAbsTolerance().doubleValue();
        double relTol = baseline.getRelTolerance() == null ? 0.0001 : baseline.getRelTolerance().doubleValue();

        List<Map<String, Object>> comparisons = new ArrayList<>();
        boolean allPassed = result.isSuccess();

        if (!result.isSuccess()) {
            comparisons.add(comparison("algorithm", null, null, false,
                    "算法执行失败：" + result.getError()));
        } else {
            Map<String, Object> actual = flatten(result);
            for (Map.Entry<String, Object> e : expected.entrySet()) {
                Object exp = e.getValue();
                Object act = actual.get(e.getKey());
                boolean passed;
                String note = null;
                if (exp instanceof Number en && act instanceof Number an) {
                    double diff = Math.abs(en.doubleValue() - an.doubleValue());
                    double denom = Math.max(Math.abs(en.doubleValue()), 1e-12);
                    double rel = diff / denom;
                    passed = diff <= absTol || rel <= relTol;
                    note = String.format("差值 %.8f，相对误差 %.8f（容差 abs=%.8f rel=%.8f）",
                            diff, rel, absTol, relTol);
                } else if (exp instanceof List<?> el && act instanceof List<?> al) {
                    passed = compareList(el, al, absTol, relTol);
                    note = passed ? "向量逐项比对通过" : "向量存在超差项";
                } else {
                    passed = exp == null ? act == null : exp.toString().equals(String.valueOf(act));
                    note = passed ? "一致" : ("预期 " + exp + "，实际 " + act);
                }
                if (!passed) {
                    allPassed = false;
                }
                comparisons.add(comparison(e.getKey(), exp, act, passed, note));
            }
        }

        String summary = allPassed ? "PASS" : "FAIL";
        baseline.setLastRunAt(LocalDateTime.now());
        baseline.setLastRunResult(summary + " | " + comparisons.stream()
                .filter(c -> !Boolean.TRUE.equals(c.get("passed")))
                .map(c -> c.get("field") + ": " + c.get("note"))
                .reduce((a, b) -> a + "; " + b).orElse("全部通过"));
        if (baseline.getLastRunResult().length() > 1900) {
            baseline.setLastRunResult(baseline.getLastRunResult().substring(0, 1900));
        }
        repository.save(baseline);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("baselineCode", baseline.getBaselineCode());
        out.put("baselineName", baseline.getBaselineName());
        out.put("algorithm", baseline.getAlgorithm());
        out.put("algorithmVersion", registry.get(baseline.getAlgorithm()).algorithmVersion());
        out.put("passed", allPassed);
        out.put("result", summary);
        out.put("absTolerance", absTol);
        out.put("relTolerance", relTol);
        out.put("comparisons", comparisons);
        out.put("actualRaw", result);
        out.put("expected", expected);
        out.put("runAt", LocalDateTime.now().toString());
        log.info("基准样例 {} 运行结果 {}", baseline.getBaselineCode(), summary);
        return out;
    }

    @Transactional
    public List<Map<String, Object>> runAll() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AlgorithmBaseline b : repository.findByEnabledTrueOrderByIdAsc()) {
            try {
                out.add(run(b.getId()));
            } catch (Exception e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("baselineCode", b.getBaselineCode());
                err.put("passed", false);
                err.put("result", "ERROR");
                err.put("error", e.getMessage());
                out.add(err);
            }
        }
        return out;
    }

    /** 把算法输出压平成 key -> value，用于与预期结果比对。 */
    private Map<String, Object> flatten(AlgorithmResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", r.getScore());
        m.put("level", r.getLevel());
        Map<String, Object> extra = r.getExtra();
        if (extra != null) {
            Object consistency = extra.get("consistency");
            if (consistency instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> c0) {
                c0.forEach((k, v) -> m.put(String.valueOf(k), v));
            }
            extra.forEach((k, v) -> m.putIfAbsent(k, v));
        }
        if (!r.getObjectResults().isEmpty()) {
            AlgorithmResult.ObjectResult o = r.getObjectResults().get(0);
            m.putIfAbsent("vector", o.getVector());
            m.putIfAbsent("weights", o.getWeights());
            m.putIfAbsent("totalScore", o.getTotalScore());
            if (o.getExtra() != null) {
                o.getExtra().forEach(m::putIfAbsent);
            }
            if (o.getDetails() != null && !o.getDetails().isEmpty()) {
                List<Object> normalized = new ArrayList<>();
                for (AlgorithmResult.DetailItem d : o.getDetails()) {
                    normalized.add(d.getNormalizedValue());
                }
                m.putIfAbsent("normalized", normalized);
            }
        }
        return m;
    }

    private boolean compareList(List<?> expected, List<?> actual, double absTol, double relTol) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            Object e = expected.get(i);
            Object a = actual.get(i);
            if (e instanceof Number en && a instanceof Number an) {
                double diff = Math.abs(en.doubleValue() - an.doubleValue());
                double rel = diff / Math.max(Math.abs(en.doubleValue()), 1e-12);
                if (diff > absTol && rel > relTol) {
                    return false;
                }
            } else if (e == null ? a != null : !e.toString().equals(String.valueOf(a))) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> comparison(String field, Object expected, Object actual,
                                           boolean passed, String note) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("field", field);
        m.put("expected", expected);
        m.put("actual", actual);
        m.put("passed", passed);
        m.put("note", note);
        return m;
    }
}
