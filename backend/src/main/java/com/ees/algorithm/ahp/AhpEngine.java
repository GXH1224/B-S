package com.ees.algorithm.ahp;

import com.ees.algorithm.AlgorithmEngine;
import com.ees.algorithm.AlgorithmRequest;
import com.ees.algorithm.AlgorithmResult;
import com.ees.algorithm.MathSupport;
import com.ees.algorithm.ScoringSupport;
import com.ees.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 层次分析法（AHP）实现（方案 6.2）。
 *
 * <p>支持两种输入形态：</p>
 * <pre>
 * 单层：{ "criteria": ["A","B","C"], "matrix": [[1,2,6],[0.5,1,3],[0.1667,0.3333,1]] }
 * 多层：{ "nodes": [ {"nodeCode":"ROOT","criteria":[...],"matrix":[[...]]},
 *                   {"nodeCode":"B1","criteria":[...],"matrix":[[...]]} ] }
 * </pre>
 *
 * <p>输出每层权重、总排序权重和一致性结果，不只输出最终得分。</p>
 */
@Component
public class AhpEngine implements AlgorithmEngine {

    public static final String CODE = "AHP";

    @Override
    public String algorithmCode() {
        return CODE;
    }

    @Override
    public String algorithmVersion() {
        return "java-ahp-1.0.0";
    }

    @Override
    public AlgorithmResult evaluate(AlgorithmRequest request) {
        LocalDateTime start = LocalDateTime.now();
        AlgorithmResult result = new AlgorithmResult();
        result.setTaskId(request.getTaskId());
        result.setAlgorithm(CODE);
        result.setAlgorithmVersion(algorithmVersion());
        result.setIndicatorVersion(request.getIndicatorVersion());
        result.setDataSnapshotId(request.getDataSnapshotId());
        result.setStartedAt(start);

        List<MatrixSpec> specs = collectMatrices(request);
        if (specs.isEmpty()) {
            return finishFailure(result, ErrorCode.ALGORITHM_PARAM_INVALID,
                    "AHP 需要判断矩阵输入：请提供 parameters.matrix/criteria 或 parameters.nodes");
        }

        List<Map<String, Object>> consistencyList = new ArrayList<>();
        boolean crFailed = false;
        List<String> warnings = new ArrayList<>();

        for (MatrixSpec spec : specs) {
            String invalid = MathSupport.validateJudgmentMatrix(spec.matrix);
            if (invalid != null) {
                return finishFailure(result, ErrorCode.AHP_MATRIX_INVALID,
                        "节点【" + spec.nodeName + "】" + invalid);
            }
            int n = spec.matrix.length;
            double[] w = MathSupport.principalEigenVector(spec.matrix, 1000, 1e-13);
            double[] wGeo = MathSupport.geometricMeanWeights(spec.matrix);
            double lambdaMax = MathSupport.lambdaMax(spec.matrix, w);
            double ci = n <= 2 ? 0d : (lambdaMax - n) / (n - 1);
            double ri = MathSupport.ri(n);
            double cr = ri <= 0 ? 0d : ci / ri;
            boolean passed = cr < request.getCrThreshold();

            spec.weights = w;
            spec.lambdaMax = lambdaMax;
            spec.ci = ci;
            spec.ri = ri;
            spec.cr = cr;
            spec.passed = passed;

            // 与几何平均法交叉校验，差异过大时告警
            double maxDiff = 0;
            for (int i = 0; i < n; i++) {
                maxDiff = Math.max(maxDiff, Math.abs(w[i] - wGeo[i]));
            }
            if (maxDiff > 0.05) {
                warnings.add(String.format("节点【%s】主特征向量与几何平均法权重差异 %.4f，建议复核判断矩阵",
                        spec.nodeName, maxDiff));
            }

            Map<String, Object> c = new LinkedHashMap<>();
            c.put("nodeCode", spec.nodeCode);
            c.put("nodeName", spec.nodeName);
            c.put("matrixSize", n);
            c.put("criteria", spec.criteria);
            c.put("matrix", spec.matrix);
            c.put("weights", toList(w));
            c.put("geometricMeanWeights", toList(wGeo));
            c.put("lambdaMax", MathSupport.round(lambdaMax, 8));
            c.put("ci", MathSupport.round(ci, 8));
            c.put("ri", MathSupport.round(ri, 8));
            c.put("cr", MathSupport.round(cr, 8));
            c.put("passed", passed);
            consistencyList.add(c);

            if (!passed) {
                crFailed = true;
                warnings.add(String.format("节点【%s】一致性比率 CR=%.4f ≥ %.4f，未通过一致性检验",
                        spec.nodeName, cr, request.getCrThreshold()));
            }
        }

        if (crFailed && !request.isAllowForceOnCrFail()) {
            AlgorithmResult fail = finishFailure(result, ErrorCode.AHP_CR_NOT_PASSED,
                    "一致性检验未通过（CR 超限），已阻止正式评估。请修正判断矩阵，或由管理员开启允许强制计算。");
            fail.setExtra(Map.of("consistency", consistencyList));
            return fail;
        }

        // 总排序权重：多层时沿层级路径相乘
        Map<String, Double> composite = compositeWeights(specs, request);
        if (composite.isEmpty()) {
            // 单层时判断矩阵的 criteria 即为指标编码
            MatrixSpec root = specs.get(0);
            for (int i = 0; i < root.criteria.size(); i++) {
                composite.put(root.criteria.get(i), root.weights[i]);
            }
        }
        // 权重归一化
        double total = composite.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total > 0 && Math.abs(total - 1.0) > 1e-9) {
            Map<String, Double> norm = new LinkedHashMap<>();
            composite.forEach((k, v) -> norm.put(k, v / total));
            composite = norm;
            warnings.add(String.format("总排序权重之和为 %.6f，已按和归一化到 1", total));
        }
        for (String w : warnings) {
            result.addWarning(w);
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("consistency", consistencyList);
        extra.put("compositeWeights", composite);
        extra.put("crThreshold", request.getCrThreshold());

        // 若存在被评估对象，则用 AHP 权重做加权综合得分；否则输出权重型结果
        List<AlgorithmRequest.ObjectData> objects = request.getObjects();
        List<AlgorithmRequest.IndicatorMeta> metas = request.getIndicators();
        if (objects != null && !objects.isEmpty()) {
            applyWeightsToMetas(metas, composite);
            Map<String, ScoringSupport.Range> ranges = ScoringSupport.effectiveRanges(metas, objects);
            double[] thresholds = ScoringSupport.toDoubleArray(request.paramList("gradeThresholds"),
                    ScoringSupport.defaultThresholds());
            String[] gradeNames = ScoringSupport.toStringArray(request.paramList("gradeNames"),
                    ScoringSupport.defaultGradeNames());

            for (AlgorithmRequest.ObjectData obj : objects) {
                AlgorithmResult.ObjectResult or = scoreObject(request, obj, metas, composite, ranges,
                        thresholds, gradeNames);
                result.getObjectResults().add(or);
            }
            rank(result.getObjectResults());
        } else {
            AlgorithmResult.ObjectResult or = new AlgorithmResult.ObjectResult();
            or.setObjectCode("GLOBAL");
            or.setObjectName("指标体系总排序权重");
            or.setWeights(composite);
            or.getExtra().put("mode", "WEIGHT_ONLY");
            for (Map.Entry<String, Double> e : composite.entrySet()) {
                AlgorithmResult.DetailItem d = new AlgorithmResult.DetailItem();
                d.setIndicatorCode(e.getKey());
                d.setIndicatorName(resolveName(metas, e.getKey()));
                d.setWeight(MathSupport.round(e.getValue(), request.getScoreScale()));
                or.getDetails().add(d);
            }
            result.getObjectResults().add(or);
            result.addWarning("本次为权重计算模式（无被评估对象数据），未产生综合得分");
        }

        if (!result.getObjectResults().isEmpty()) {
            AlgorithmResult.ObjectResult best = result.getObjectResults().get(0);
            result.setScore(best.getTotalScore());
            result.setLevel(best.getLevel());
        }
        result.setExtra(extra);
        result.setSuccess(true);
        result.setFinishedAt(LocalDateTime.now());
        return result;
    }

    private AlgorithmResult.ObjectResult scoreObject(AlgorithmRequest request,
                                                     AlgorithmRequest.ObjectData obj,
                                                     List<AlgorithmRequest.IndicatorMeta> metas,
                                                     Map<String, Double> composite,
                                                     Map<String, ScoringSupport.Range> ranges,
                                                     double[] thresholds,
                                                     String[] gradeNames) {
        AlgorithmResult.ObjectResult or = new AlgorithmResult.ObjectResult();
        or.setObjectCode(obj.getObjectCode());
        or.setObjectName(obj.getObjectName());
        or.setWeights(new LinkedHashMap<>(composite));
        double total = 0;
        for (AlgorithmRequest.IndicatorMeta m : metas) {
            Double raw = ScoringSupport.rawValue(obj, m.getCode());
            ScoringSupport.Range r = ranges.get(m.getCode());
            double norm = raw == null ? 0d
                    : MathSupport.normalize(raw, m.getDirection(), r.min, r.max,
                    m.getIntervalLower(), m.getIntervalUpper());
            double w = composite.getOrDefault(m.getCode(), 0d);
            double contrib = w * norm;
            total += contrib;

            AlgorithmResult.DetailItem d = new AlgorithmResult.DetailItem();
            d.setIndicatorCode(m.getCode());
            d.setIndicatorName(m.getName());
            d.setParentCode(m.getParentCode());
            d.setIndicatorLevel(m.getLevel());
            d.setUnit(m.getUnit());
            d.setDirection(m.getDirection());
            d.setRawValue(MathSupport.round(raw == null ? 0d : raw, request.getScoreScale()));
            d.setNormalizedValue(MathSupport.round(norm, request.getScoreScale()));
            d.setWeight(MathSupport.round(w, request.getScoreScale()));
            d.setContribution(MathSupport.round(contrib, request.getScoreScale()));
            d.setScore(MathSupport.round(norm * 100, request.getScoreScale()));
            d.setGrade(MathSupport.gradeOf(norm * 100, thresholds, gradeNames));
            or.getDetails().add(d);
            or.getContributions().put(m.getCode(), contrib);
            if (raw == null) {
                or.getWarnings().add("指标 " + m.getCode() + " 缺少数据，标准化值按 0 计");
            }
        }
        double score = total * 100d;
        or.setTotalScore(MathSupport.round(score, request.getScoreScale()));
        or.setLevel(MathSupport.gradeOf(score, thresholds, gradeNames));
        return or;
    }

    private void applyWeightsToMetas(List<AlgorithmRequest.IndicatorMeta> metas, Map<String, Double> composite) {
        for (AlgorithmRequest.IndicatorMeta m : metas) {
            Double w = composite.get(m.getCode());
            if (w != null) {
                m.setWeight(w);
            }
        }
    }

    private void rank(List<AlgorithmResult.ObjectResult> list) {
        List<AlgorithmResult.ObjectResult> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> {
            double x = a.getTotalScore() == null ? 0 : a.getTotalScore().doubleValue();
            double y = b.getTotalScore() == null ? 0 : b.getTotalScore().doubleValue();
            return Double.compare(y, x);
        });
        int same = 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                double prev = sorted.get(i - 1).getTotalScore() == null ? 0
                        : sorted.get(i - 1).getTotalScore().doubleValue();
                double cur = sorted.get(i).getTotalScore() == null ? 0
                        : sorted.get(i).getTotalScore().doubleValue();
                if (Math.abs(prev - cur) > 1e-9) {
                    same = i + 1;
                }
            }
            sorted.get(i).setRanking(same);
        }
    }

    /** 多层权重合成：ROOT 的 criteria 若为下层节点，则继续向下相乘。 */
    private Map<String, Double> compositeWeights(List<MatrixSpec> specs, AlgorithmRequest request) {
        Map<String, MatrixSpec> byNode = new LinkedHashMap<>();
        for (MatrixSpec s : specs) {
            byNode.put(s.nodeCode, s);
        }
        Map<String, Double> out = new LinkedHashMap<>();
        if (specs.size() == 1) {
            return out;
        }
        MatrixSpec root = byNode.getOrDefault("ROOT", specs.get(0));
        for (int i = 0; i < root.criteria.size(); i++) {
            String code = root.criteria.get(i);
            double w = root.weights[i];
            expand(code, w, byNode, out);
        }
        return out;
    }

    private void expand(String code, double weight, Map<String, MatrixSpec> byNode, Map<String, Double> out) {
        MatrixSpec child = byNode.get(code);
        if (child == null) {
            out.merge(code, weight, Double::sum);
            return;
        }
        for (int i = 0; i < child.criteria.size(); i++) {
            expand(child.criteria.get(i), weight * child.weights[i], byNode, out);
        }
    }

    private String resolveName(List<AlgorithmRequest.IndicatorMeta> metas, String code) {
        for (AlgorithmRequest.IndicatorMeta m : metas) {
            if (m.getCode().equals(code)) {
                return m.getName();
            }
        }
        return code;
    }

    @SuppressWarnings("unchecked")
    private List<MatrixSpec> collectMatrices(AlgorithmRequest request) {
        List<MatrixSpec> specs = new ArrayList<>();
        Object nodes = request.param("nodes");
        if (nodes instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    MatrixSpec spec = new MatrixSpec();
                    spec.nodeCode = str(((Map<String, Object>) m).get("nodeCode"), "ROOT");
                    spec.nodeName = str(((Map<String, Object>) m).get("nodeName"), spec.nodeCode);
                    spec.criteria = toStringList(((Map<String, Object>) m).get("criteria"));
                    spec.matrix = toMatrix(((Map<String, Object>) m).get("matrix"));
                    if (spec.matrix != null && spec.matrix.length > 0
                            && spec.criteria.size() == spec.matrix.length) {
                        specs.add(spec);
                    }
                }
            }
            if (!specs.isEmpty()) {
                return specs;
            }
        }
        double[][] matrix = toMatrix(request.param("matrix"));
        if (matrix != null && matrix.length > 0) {
            MatrixSpec spec = new MatrixSpec();
            spec.nodeCode = request.paramString("nodeCode", "ROOT");
            spec.nodeName = request.paramString("nodeName", "总目标");
            spec.criteria = toStringList(request.param("criteria"));
            spec.matrix = matrix;
            if (spec.criteria.isEmpty() || spec.criteria.size() != matrix.length) {
                List<String> auto = new ArrayList<>();
                for (int i = 0; i < matrix.length; i++) {
                    auto.add("C" + (i + 1));
                }
                spec.criteria = auto;
            }
            specs.add(spec);
        }
        // 也从 raw 中读取（基准样例直接调用）
        if (specs.isEmpty() && request.getRaw().get("matrix") != null) {
            double[][] m = toMatrix(request.getRaw().get("matrix"));
            if (m != null && m.length > 0) {
                MatrixSpec spec = new MatrixSpec();
                spec.nodeCode = "ROOT";
                spec.nodeName = "总目标";
                spec.matrix = m;
                spec.criteria = toStringList(request.getRaw().get("criteria"));
                if (spec.criteria.isEmpty()) {
                    List<String> auto = new ArrayList<>();
                    for (int i = 0; i < m.length; i++) {
                        auto.add("C" + (i + 1));
                    }
                    spec.criteria = auto;
                }
                specs.add(spec);
            }
        }
        return specs;
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static List<String> toStringList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object x : l) {
                out.add(String.valueOf(x));
            }
        }
        return out;
    }

    private static double[][] toMatrix(Object o) {
        if (!(o instanceof List<?> rows)) {
            return null;
        }
        double[][] m = new double[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            Object row = rows.get(i);
            if (row instanceof List<?> cols) {
                m[i] = new double[cols.size()];
                for (int j = 0; j < cols.size(); j++) {
                    Object cell = cols.get(j);
                    if (cell instanceof Number n) {
                        m[i][j] = n.doubleValue();
                    } else {
                        try {
                            m[i][j] = Double.parseDouble(String.valueOf(cell));
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }
                }
            } else {
                return null;
            }
        }
        return m;
    }

    private static List<Double> toList(double[] arr) {
        List<Double> l = new ArrayList<>();
        for (double v : arr) {
            l.add(v);
        }
        return l;
    }

    private AlgorithmResult finishFailure(AlgorithmResult result, ErrorCode code, String message) {
        result.setSuccess(false);
        result.setErrorCode(code.name());
        result.setError(message);
        result.setFinishedAt(LocalDateTime.now());
        return result;
    }

    /** 内部判断矩阵规格。 */
    private static class MatrixSpec {
        String nodeCode;
        String nodeName;
        List<String> criteria = new ArrayList<>();
        double[][] matrix;
        double[] weights;
        double lambdaMax;
        double ci;
        double ri;
        double cr;
        boolean passed;
    }
}
