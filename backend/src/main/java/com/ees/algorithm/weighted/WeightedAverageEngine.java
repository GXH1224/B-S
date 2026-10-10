package com.ees.algorithm.weighted;

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
 * 加权平均法实现（方案 6.4）。
 *
 * <p>支持正向、负向和区间型指标标准化；权重总和不为 1 时按配置归一化，
 * 归一化前后权重均记录；输出每项指标的标准化值、权重、贡献值和最终得分。</p>
 */
@Component
public class WeightedAverageEngine implements AlgorithmEngine {

    public static final String CODE = "WEIGHTED";

    @Override
    public String algorithmCode() {
        return CODE;
    }

    @Override
    public String algorithmVersion() {
        return "java-weighted-1.0.0";
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

        List<AlgorithmRequest.IndicatorMeta> metas = request.getIndicators();
        double[] weights = ScoringSupport.normalizeWeights(metas, null);
        double rawSum = 0;
        for (AlgorithmRequest.IndicatorMeta m : metas) {
            rawSum += m.getWeight() == null ? 0d : m.getWeight();
        }
        if (Math.abs(rawSum - 1.0) > 1e-9 && rawSum > 0) {
            result.addWarning(String.format("权重总和为 %.6f（不等于 1），已按配置归一化后参与计算", rawSum));
        }

        double[] thresholds = ScoringSupport.toDoubleArray(request.paramList("gradeThresholds"),
                ScoringSupport.defaultThresholds());
        String[] gradeNames = ScoringSupport.toStringArray(request.paramList("gradeNames"),
                ScoringSupport.defaultGradeNames());

        List<AlgorithmRequest.ObjectData> objects = request.getObjects();

        // ---- 参数直算模式（基准测试 / 单对象快速计算） ----
        if ((objects == null || objects.isEmpty())) {
            List<?> valuesParam = request.paramList("values");
            if (valuesParam.isEmpty() && request.getRaw().get("values") != null) {
                valuesParam = (List<?>) request.getRaw().get("values");
            }
            if (valuesParam.isEmpty()) {
                return finishFailure(result, ErrorCode.ALGORITHM_PARAM_INVALID,
                        "加权平均法需要被评估对象数据（数据快照），或直接提供 parameters.values");
            }
            List<?> dirParam = request.paramList("directions");
            if (dirParam.isEmpty() && request.getRaw().get("directions") != null) {
                dirParam = (List<?>) request.getRaw().get("directions");
            }
            List<?> minParam = request.paramList("minValues");
            if (minParam.isEmpty() && request.getRaw().get("minValues") != null) {
                minParam = (List<?>) request.getRaw().get("minValues");
            }
            List<?> maxParam = request.paramList("maxValues");
            if (maxParam.isEmpty() && request.getRaw().get("maxValues") != null) {
                maxParam = (List<?>) request.getRaw().get("maxValues");
            }
            // 权重：优先使用 parameters.weights（直接参数模式 / 标准样例），
            // 否则回退到指标体系版本中定义的权重
            List<?> weightParam = request.paramList("weights");
            if (weightParam.isEmpty() && request.getRaw().get("weights") != null) {
                weightParam = (List<?>) request.getRaw().get("weights");
            }
            double[] paramWeights = weightParam.isEmpty() ? new double[0]
                    : ScoringSupport.toDoubleArray(weightParam, new double[0]);
            if (paramWeights.length > 0) {
                double s = MathSupport.sum(paramWeights);
                if (s > 0 && Math.abs(s - 1.0) > 1e-9) {
                    result.addWarning(String.format("输入权重总和为 %.6f（不等于 1），已按配置归一化", s));
                    paramWeights = MathSupport.normalizeToSum(paramWeights);
                } else if (s <= 0) {
                    paramWeights = new double[0];
                }
            }
            AlgorithmRequest.ObjectData obj = new AlgorithmRequest.ObjectData();
            obj.setObjectCode(request.paramString("objectCode", "GLOBAL"));
            obj.setObjectName(request.paramString("objectName", "综合评分"));
            List<AlgorithmRequest.IndicatorMeta> localMetas = new ArrayList<>();
            for (int i = 0; i < valuesParam.size(); i++) {
                AlgorithmRequest.IndicatorMeta m = new AlgorithmRequest.IndicatorMeta();
                String code = "C" + (i + 1);
                if (i < metas.size()) {
                    code = metas.get(i).getCode();
                    m.setName(metas.get(i).getName());
                    m.setUnit(metas.get(i).getUnit());
                    m.setParentCode(metas.get(i).getParentCode());
                    m.setLevel(metas.get(i).getLevel());
                } else {
                    m.setName(code);
                }
                m.setCode(code);
                m.setDirection(dirParam.size() > i ? String.valueOf(dirParam.get(i)) : "POSITIVE");
                if (minParam.size() > i && minParam.get(i) instanceof Number n) {
                    m.setMinValue(n.doubleValue());
                }
                if (maxParam.size() > i && maxParam.get(i) instanceof Number n) {
                    m.setMaxValue(n.doubleValue());
                }
                // 参数权重优先；无参数权重时取指标体系权重
                if (i < paramWeights.length) {
                    m.setWeight(paramWeights[i]);
                } else if (i < weights.length) {
                    m.setWeight(weights[i]);
                } else {
                    m.setWeight(null);
                }
                localMetas.add(m);
                double v = toDouble(valuesParam.get(i));
                obj.getValues().put(code, v);
            }
            metas = localMetas;
            objects = List.of(obj);
            weights = paramWeights.length == localMetas.size() ? paramWeights
                    : ScoringSupport.normalizeWeights(localMetas, null);
            request.setIndicators(localMetas);
        }

        Map<String, ScoringSupport.Range> ranges = ScoringSupport.effectiveRanges(metas, objects);

        for (AlgorithmRequest.ObjectData obj : objects) {
            AlgorithmResult.ObjectResult or = new AlgorithmResult.ObjectResult();
            or.setObjectCode(obj.getObjectCode());
            or.setObjectName(obj.getObjectName());
            double total = 0;
            for (int i = 0; i < metas.size(); i++) {
                AlgorithmRequest.IndicatorMeta m = metas.get(i);
                Double raw = ScoringSupport.rawValue(obj, m.getCode());
                ScoringSupport.Range r = ranges.get(m.getCode());
                boolean missing = raw == null;
                double norm = missing ? 0d : MathSupport.normalize(raw, m.getDirection(),
                        r.min, r.max, m.getIntervalLower(), m.getIntervalUpper());
                double w = i < weights.length ? weights[i] : 0d;
                double contrib = w * norm;
                total += contrib;

                AlgorithmResult.DetailItem d = new AlgorithmResult.DetailItem();
                d.setIndicatorCode(m.getCode());
                d.setIndicatorName(m.getName());
                d.setParentCode(m.getParentCode());
                d.setIndicatorLevel(m.getLevel());
                d.setUnit(m.getUnit());
                d.setDirection(m.getDirection());
                d.setRawValue(MathSupport.round(missing ? 0d : raw, request.getScoreScale()));
                d.setNormalizedValue(MathSupport.round(norm, request.getScoreScale()));
                d.setWeight(MathSupport.round(w, request.getScoreScale()));
                d.setContribution(MathSupport.round(contrib, request.getScoreScale()));
                d.setScore(MathSupport.round(norm * 100d, request.getScoreScale()));
                d.setGrade(MathSupport.gradeOf(norm * 100d, thresholds, gradeNames));
                if (missing) {
                    d.setRemark("数据缺失，标准化值按 0 计入");
                    or.getWarnings().add("指标 " + m.getCode() + " 缺少数据");
                    result.addWarning("对象 " + obj.getObjectCode() + " 指标 " + m.getCode() + " 缺少数据");
                }
                if (r.derivedFromData && m.getMinValue() == null) {
                    d.setRemark((d.getRemark() == null ? "" : d.getRemark() + "；")
                            + String.format("标准化区间由本批数据推断 [%.4f, %.4f]", r.min, r.max));
                }
                or.getDetails().add(d);
                or.getWeights().put(m.getCode(), w);
                or.getContributions().put(m.getCode(), contrib);
            }
            double score = total * 100d;
            or.setTotalScore(MathSupport.round(score, request.getScoreScale()));
            or.setLevel(MathSupport.gradeOf(score, thresholds, gradeNames));
            result.getObjectResults().add(or);
        }

        rank(result.getObjectResults());
        AlgorithmResult.ObjectResult best = bestOf(result.getObjectResults());
        if (best != null) {
            result.setScore(best.getTotalScore());
            result.setLevel(best.getLevel());
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("normalizedWeights", toList(weights));
        extra.put("rawWeightSum", MathSupport.round(rawSum, 8));
        extra.put("gradeNames", List.of(gradeNames));
        extra.put("gradeThresholds", toList(thresholds));
        extra.put("directions", metas.stream().map(AlgorithmRequest.IndicatorMeta::getDirection).toList());
        result.setExtra(extra);
        result.setSuccess(true);
        result.setFinishedAt(LocalDateTime.now());
        return result;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return Double.NaN;
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

    private AlgorithmResult.ObjectResult bestOf(List<AlgorithmResult.ObjectResult> list) {
        AlgorithmResult.ObjectResult best = null;
        for (AlgorithmResult.ObjectResult o : list) {
            if (best == null) {
                best = o;
                continue;
            }
            double a = best.getTotalScore() == null ? 0 : best.getTotalScore().doubleValue();
            double b = o.getTotalScore() == null ? 0 : o.getTotalScore().doubleValue();
            if (b > a) {
                best = o;
            }
        }
        return best;
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
