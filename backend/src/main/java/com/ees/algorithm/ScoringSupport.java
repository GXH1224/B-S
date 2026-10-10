package com.ees.algorithm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评分支撑：指标标准化与权重归一化（方案 6.4）。
 */
public final class ScoringSupport {

    private ScoringSupport() {
    }

    /** 单个指标的标准化上下界。 */
    public static class Range {
        public double min;
        public double max;
        public boolean derivedFromData;
    }

    /**
     * 计算每个指标的标准化区间：优先使用指标体系定义，否则取观测数据的最小最大值。
     */
    public static Map<String, Range> effectiveRanges(List<AlgorithmRequest.IndicatorMeta> metas,
                                                     List<AlgorithmRequest.ObjectData> objects) {
        Map<String, Range> map = new LinkedHashMap<>();
        for (AlgorithmRequest.IndicatorMeta m : metas) {
            Range r = new Range();
            if (m.getMinValue() != null && m.getMaxValue() != null) {
                r.min = m.getMinValue();
                r.max = m.getMaxValue();
                r.derivedFromData = false;
            } else {
                double lo = Double.MAX_VALUE;
                double hi = -Double.MAX_VALUE;
                boolean found = false;
                for (AlgorithmRequest.ObjectData o : objects) {
                    Double v = o.getValues().get(m.getCode());
                    if (v != null && !Double.isNaN(v)) {
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                        found = true;
                    }
                }
                if (found) {
                    r.min = m.getMinValue() != null ? m.getMinValue() : lo;
                    r.max = m.getMaxValue() != null ? m.getMaxValue() : hi;
                } else {
                    r.min = 0d;
                    r.max = 1d;
                }
                r.derivedFromData = true;
            }
            map.put(m.getCode(), r);
        }
        return map;
    }

    /**
     * 权重归一化：权重总和不为 1 时按配置归一化，并记录归一化前后权重。
     *
     * @return 归一化后的权重数组；同时把原始权重写入 rawWeights
     */
    public static double[] normalizeWeights(List<AlgorithmRequest.IndicatorMeta> metas,
                                            Map<String, Double> rawWeights) {
        int n = metas.size();
        double[] w = new double[n];
        boolean allNull = true;
        for (int i = 0; i < n; i++) {
            AlgorithmRequest.IndicatorMeta m = metas.get(i);
            Double v = m.getWeight();
            if (v == null) {
                v = rawWeights == null ? null : rawWeights.get(m.getCode());
            }
            w[i] = v == null ? 0d : v;
            if (v != null && v != 0d) {
                allNull = false;
            }
        }
        if (allNull) {
            // 全部缺省时退化为等权
            for (int i = 0; i < n; i++) {
                w[i] = n == 0 ? 0d : 1.0 / n;
            }
            return w;
        }
        double s = MathSupport.sum(w);
        if (Math.abs(s) < 1e-15) {
            for (int i = 0; i < n; i++) {
                w[i] = n == 0 ? 0d : 1.0 / n;
            }
            return w;
        }
        if (Math.abs(s - 1.0) > 1e-9) {
            for (int i = 0; i < n; i++) {
                w[i] = w[i] / s;
            }
        }
        return w;
    }

    /** 某个指标在某个对象上的原始值。 */
    public static Double rawValue(AlgorithmRequest.ObjectData o, String indicatorCode) {
        if (o == null) {
            return null;
        }
        return o.getValues().get(indicatorCode);
    }

    /** 生成等级阈值与名称，默认 优/良/中/差 = 90/80/60/0。 */
    public static double[] defaultThresholds() {
        return new double[]{90d, 80d, 60d, 0d};
    }

    public static String[] defaultGradeNames() {
        return new String[]{"优", "良", "中", "差"};
    }

    public static double[] toDoubleArray(List<?> list, double[] def) {
        if (list == null || list.isEmpty()) {
            return def;
        }
        List<Double> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Number n) {
                out.add(n.doubleValue());
            } else if (o instanceof String s) {
                try {
                    out.add(Double.parseDouble(s));
                } catch (NumberFormatException ignored) {
                    // 跳过非法值
                }
            }
        }
        if (out.isEmpty()) {
            return def;
        }
        double[] arr = new double[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    public static String[] toStringArray(List<?> list, String[] def) {
        if (list == null || list.isEmpty()) {
            return def;
        }
        String[] arr = new String[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = String.valueOf(list.get(i));
        }
        return arr;
    }
}
