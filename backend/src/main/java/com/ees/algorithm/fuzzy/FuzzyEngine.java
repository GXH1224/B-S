package com.ees.algorithm.fuzzy;

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
 * 模糊综合评判实现（方案 6.3）。
 *
 * <p>评语集、隶属函数、合成算子和等级量化规则全部参数化并随任务保存；
 * 输出完整综合评判向量，不只保存最大隶属度对应的等级。</p>
 *
 * <p>合成算子：M_PLUS(加权平均型 M(·,+)) / M_MIN_MAX(M(∧,∨)) /
 * M_PLUS_MAX(M(·,∨)) / M_MIN_PLUS(M(∧,⊕))</p>
 */
@Component
public class FuzzyEngine implements AlgorithmEngine {

    public static final String CODE = "FUZZY";

    @Override
    public String algorithmCode() {
        return CODE;
    }

    @Override
    public String algorithmVersion() {
        return "java-fuzzy-1.0.0";
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

        // ---- 评语集与等级量化 ----
        double[] gradeScores = ScoringSupport.toDoubleArray(request.paramList("gradeScores"),
                new double[]{95, 85, 70, 55});
        String[] gradeNames = ScoringSupport.toStringArray(request.paramList("gradeNames"),
                new String[]{"优", "良", "中", "差"});
        if (gradeScores.length != gradeNames.length) {
            return finishFailure(result, ErrorCode.ALGORITHM_PARAM_INVALID,
                    "评语集名称数量(" + gradeNames.length + ")与等级量化分数数量(" + gradeScores.length + ")不一致");
        }
        // 评语集必须按量化分数降序，保证隶属度插值方向正确
        Integer[] order = sortDesc(gradeScores);
        double[] gs = new double[order.length];
        String[] gn = new String[order.length];
        for (int i = 0; i < order.length; i++) {
            gs[i] = gradeScores[order[i]];
            gn[i] = gradeNames[order[i]];
        }

        String operator = request.paramString("operator", "M_PLUS").toUpperCase();

        List<AlgorithmRequest.IndicatorMeta> metas = request.getIndicators();
        int indicatorCount = metas.size();

        // ---- 权重 ----
        double[] weights = ScoringSupport.normalizeWeights(metas, null);
        List<?> wParam = request.paramList("weights");
        if (!wParam.isEmpty()) {
            double[] w = ScoringSupport.toDoubleArray(wParam, weights);
            if (w.length != indicatorCount && indicatorCount > 0) {
                // 参数的权重数量与指标数量不一致时，以指标体系权重为准
                result.addWarning(String.format(
                        "参数权重数量(%d)与指标数量(%d)不一致，已改用指标体系权重",
                        w.length, indicatorCount));
            } else {
                weights = w;
                double s = MathSupport.sum(weights);
                if (s > 0 && Math.abs(s - 1.0) > 1e-9) {
                    result.addWarning(String.format("输入权重总和为 %.6f，已归一化到 1", s));
                    weights = MathSupport.normalizeToSum(weights);
                }
            }
        }

        // ---- 直接从参数给定的隶属度矩阵（基准测试路径） ----
        List<List<Double>> givenMembership = toMatrix(request.param("membership"));
        if (givenMembership.isEmpty() && request.getRaw().get("membership") != null) {
            givenMembership = toMatrix(request.getRaw().get("membership"));
        }
        if (!givenMembership.isEmpty()) {
            if (givenMembership.size() != weights.length) {
                return finishFailure(result, ErrorCode.ALGORITHM_PARAM_INVALID,
                        String.format("隶属度矩阵行数(%d)与权重个数(%d)不一致", givenMembership.size(), weights.length));
            }
            for (int i = 0; i < givenMembership.size(); i++) {
                if (givenMembership.get(i).size() != gs.length) {
                    return finishFailure(result, ErrorCode.ALGORITHM_PARAM_INVALID,
                            String.format("隶属度矩阵第 %d 行元素个数(%d)与评语集个数(%d)不一致",
                                    i + 1, givenMembership.get(i).size(), gs.length));
                }
            }
            AlgorithmResult.ObjectResult or = composite("GLOBAL", "模糊综合评判（参数隶属度矩阵）",
                    givenMembership, weights, gs, gn, operator, metas);
            result.getObjectResults().add(or);
            result.setScore(or.getTotalScore());
            result.setLevel(or.getLevel());
            result.getExtra().put("operator", operator);
            result.getExtra().put("gradeNames", List.of(gn));
            result.getExtra().put("gradeScores", toList(gs));
            result.getExtra().put("weights", toList(weights));
            result.setSuccess(true);
            result.setFinishedAt(LocalDateTime.now());
            return result;
        }

        // ---- 由快照数据计算隶属度矩阵 ----
        List<AlgorithmRequest.ObjectData> objects = request.getObjects();
        if (objects == null || objects.isEmpty()) {
            return finishFailure(result, ErrorCode.ALGORITHM_PARAM_INVALID,
                    "模糊综合评判需要被评估对象数据（数据快照）或直接提供隶属度矩阵");
        }
        Map<String, ScoringSupport.Range> ranges = ScoringSupport.effectiveRanges(metas, objects);
        for (AlgorithmRequest.ObjectData obj : objects) {
            List<List<Double>> matrix = new ArrayList<>();
            for (AlgorithmRequest.IndicatorMeta m : metas) {
                Double raw = ScoringSupport.rawValue(obj, m.getCode());
                double score;
                if (raw == null) {
                    score = 0d;
                    result.addWarning("对象 " + obj.getObjectCode() + " 指标 " + m.getCode() + " 缺失，隶属度按最低等级处理");
                } else {
                    ScoringSupport.Range r = ranges.get(m.getCode());
                    score = MathSupport.normalize(raw, m.getDirection(), r.min, r.max,
                            m.getIntervalLower(), m.getIntervalUpper()) * 100d;
                }
                matrix.add(membershipRow(score, gs));
            }
            AlgorithmResult.ObjectResult or = composite(obj.getObjectCode(), obj.getObjectName(),
                    matrix, weights, gs, gn, operator, metas);
            result.getObjectResults().add(or);
        }
        rank(result.getObjectResults());
        if (!result.getObjectResults().isEmpty()) {
            AlgorithmResult.ObjectResult best = bestOf(result.getObjectResults());
            result.setScore(best.getTotalScore());
            result.setLevel(best.getLevel());
        }
        result.getExtra().put("operator", operator);
        result.getExtra().put("gradeNames", List.of(gn));
        result.getExtra().put("gradeScores", toList(gs));
        result.setSuccess(true);
        result.setFinishedAt(LocalDateTime.now());
        return result;
    }

    /** 由指标量化得分构造隶属度行（相邻等级线性插值，构成单位分解）。 */
    static List<Double> membershipRow(double score, double[] gradeScores) {
        int m = gradeScores.length;
        List<Double> row = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            row.add(0d);
        }
        if (m == 0) {
            return row;
        }
        if (m == 1) {
            row.set(0, 1d);
            return row;
        }
        if (score >= gradeScores[0]) {
            row.set(0, 1d);
            return row;
        }
        if (score <= gradeScores[m - 1]) {
            row.set(m - 1, 1d);
            return row;
        }
        for (int i = 0; i < m - 1; i++) {
            double hi = gradeScores[i];
            double lo = gradeScores[i + 1];
            if (score <= hi && score >= lo) {
                double span = hi - lo;
                double t = Math.abs(span) < 1e-15 ? 1d : (score - lo) / span;
                row.set(i, t);
                row.set(i + 1, 1d - t);
                return row;
            }
        }
        row.set(m - 1, 1d);
        return row;
    }

    private AlgorithmResult.ObjectResult composite(String objectCode, String objectName,
                                                   List<List<Double>> matrix, double[] weights,
                                                   double[] gradeScores, String[] gradeNames,
                                                   String operator,
                                                   List<AlgorithmRequest.IndicatorMeta> metas) {
        int m = gradeScores.length;
        double[] b = new double[m];
        switch (operator) {
            case "M_MIN_MAX" -> {
                for (int j = 0; j < m; j++) {
                    double mx = 0;
                    for (int i = 0; i < matrix.size(); i++) {
                        mx = Math.max(mx, Math.min(weights[i], matrix.get(i).get(j)));
                    }
                    b[j] = mx;
                }
            }
            case "M_PLUS_MAX" -> {
                for (int j = 0; j < m; j++) {
                    double mx = 0;
                    for (int i = 0; i < matrix.size(); i++) {
                        mx = Math.max(mx, weights[i] * matrix.get(i).get(j));
                    }
                    b[j] = mx;
                }
            }
            case "M_MIN_PLUS" -> {
                for (int j = 0; j < m; j++) {
                    double s = 0;
                    for (int i = 0; i < matrix.size(); i++) {
                        s += Math.min(weights[i], matrix.get(i).get(j));
                    }
                    b[j] = s;
                }
            }
            default -> {
                for (int j = 0; j < m; j++) {
                    double s = 0;
                    for (int i = 0; i < matrix.size(); i++) {
                        s += weights[i] * matrix.get(i).get(j);
                    }
                    b[j] = s;
                }
            }
        }
        // 归一化，保证向量各分量之和为 1（M(·,+) 已满足，其余算子需要）
        double sum = MathSupport.sum(b);
        double[] bn = Math.abs(sum) < 1e-15 ? b : MathSupport.normalizeToSum(b);

        int bestIdx = 0;
        for (int j = 1; j < m; j++) {
            if (bn[j] > bn[bestIdx]) {
                bestIdx = j;
            }
        }
        double score = 0;
        for (int j = 0; j < m; j++) {
            score += bn[j] * gradeScores[j];
        }

        AlgorithmResult.ObjectResult or = new AlgorithmResult.ObjectResult();
        or.setObjectCode(objectCode);
        or.setObjectName(objectName);
        or.setVector(toList(bn));
        or.setTotalScore(MathSupport.round(score, 6));
        or.setLevel(gradeNames[bestIdx]);
        or.getExtra().put("rawVector", toList(b));
        or.getExtra().put("maxMembership", MathSupport.round(bn[bestIdx], 8));
        or.getExtra().put("operator", operator);
        or.getExtra().put("gradeNames", List.of(gradeNames));
        or.getExtra().put("gradeScores", toList(gradeScores));
        for (int i = 0; i < weights.length; i++) {
            String code = i < metas.size() ? metas.get(i).getCode() : ("C" + (i + 1));
            or.getWeights().put(code, weights[i]);
            AlgorithmResult.DetailItem d = new AlgorithmResult.DetailItem();
            d.setIndicatorCode(code);
            d.setIndicatorName(i < metas.size() ? metas.get(i).getName() : code);
            d.setWeight(MathSupport.round(weights[i], 6));
            d.setNormalizedValue(MathSupport.round(membershipArgMaxScore(matrix, i, gradeScores), 6));
            d.setScore(MathSupport.round(membershipArgMaxScore(matrix, i, gradeScores), 6));
            d.setGrade(gradeNames[argMax(matrix.get(i))]);
            or.getDetails().add(d);
        }
        return or;
    }

    private double membershipArgMaxScore(List<List<Double>> matrix, int i, double[] gradeScores) {
        List<Double> row = matrix.get(i);
        int idx = argMax(row);
        return idx < gradeScores.length ? gradeScores[idx] : 0d;
    }

    private static int argMax(List<Double> row) {
        int idx = 0;
        for (int j = 1; j < row.size(); j++) {
            if (row.get(j) > row.get(idx)) {
                idx = j;
            }
        }
        return idx;
    }

    private void rank(List<AlgorithmResult.ObjectResult> list) {
        List<AlgorithmResult.ObjectResult> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> {
            double x = a.getTotalScore() == null ? 0 : a.getTotalScore().doubleValue();
            double y = b.getTotalScore() == null ? 0 : b.getTotalScore().doubleValue();
            return Double.compare(y, x);
        });
        for (int i = 0; i < sorted.size(); i++) {
            sorted.get(i).setRanking(i + 1);
        }
    }

    private AlgorithmResult.ObjectResult bestOf(List<AlgorithmResult.ObjectResult> list) {
        AlgorithmResult.ObjectResult best = list.get(0);
        for (AlgorithmResult.ObjectResult o : list) {
            double a = best.getTotalScore() == null ? 0 : best.getTotalScore().doubleValue();
            double b = o.getTotalScore() == null ? 0 : o.getTotalScore().doubleValue();
            if (b > a) {
                best = o;
            }
        }
        return best;
    }

    private static Integer[] sortDesc(double[] arr) {
        Integer[] idx = new Integer[arr.length];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(arr[b], arr[a]));
        return idx;
    }

    private static List<List<Double>> toMatrix(Object o) {
        List<List<Double>> out = new ArrayList<>();
        if (!(o instanceof List<?> rows)) {
            return out;
        }
        for (Object row : rows) {
            if (row instanceof List<?> cols) {
                List<Double> r = new ArrayList<>();
                for (Object c : cols) {
                    if (c instanceof Number n) {
                        r.add(n.doubleValue());
                    } else {
                        try {
                            r.add(Double.parseDouble(String.valueOf(c)));
                        } catch (NumberFormatException e) {
                            return new ArrayList<>();
                        }
                    }
                }
                out.add(r);
            } else {
                return new ArrayList<>();
            }
        }
        return out;
    }

    private static double[][] toPrimitive(List<List<Double>> m) {
        double[][] arr = new double[m.size()][];
        for (int i = 0; i < m.size(); i++) {
            arr[i] = new double[m.get(i).size()];
            for (int j = 0; j < arr[i].length; j++) {
                arr[i][j] = m.get(i).get(j);
            }
        }
        return arr;
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
}
