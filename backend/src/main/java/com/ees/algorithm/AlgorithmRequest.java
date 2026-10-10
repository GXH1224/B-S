package com.ees.algorithm;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一算法输入模型（对应附录 B.1 统一请求结构）。
 */
@Getter
@Setter
public class AlgorithmRequest {

    /** 统一请求结构字段 */
    private String taskId;
    private String taskCode;
    private String algorithm;
    private String algorithmVersion;
    private String indicatorVersion;
    private String dataSnapshotId;
    private Map<String, Object> parameters = new LinkedHashMap<>();
    private Map<String, Object> inputRef = new LinkedHashMap<>();

    /** 运行时补充字段（由后端服务装配，算法组件不访问数据库） */
    private List<IndicatorMeta> indicators = new ArrayList<>();
    private List<ObjectData> objects = new ArrayList<>();
    private Map<String, Object> raw = new LinkedHashMap<>();

    /** 一致性阈值、精度等运行参数 */
    private double crThreshold = 0.1;
    private int scoreScale = 4;
    private boolean allowForceOnCrFail = false;

    public Object param(String key) {
        return parameters == null ? null : parameters.get(key);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> paramMap(String key) {
        Object v = param(key);
        if (v instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public List<Object> paramList(String key) {
        Object v = param(key);
        if (v instanceof List<?> l) {
            return (List<Object>) l;
        }
        return new ArrayList<>();
    }

    public String paramString(String key, String def) {
        Object v = param(key);
        return v == null ? def : String.valueOf(v);
    }

    public double paramDouble(String key, double def) {
        Object v = param(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return def;
            }
        }
        return def;
    }

    public boolean paramBool(String key, boolean def) {
        Object v = param(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    /** 叶子指标元数据。 */
    @Getter
    @Setter
    public static class IndicatorMeta {
        private String code;
        private String name;
        private String parentCode;
        private Integer level = 1;
        private String unit;
        /** POSITIVE / NEGATIVE / INTERVAL */
        private String direction = "POSITIVE";
        private Double weight;
        private Double minValue;
        private Double maxValue;
        private Double intervalLower;
        private Double intervalUpper;
        private Double optimalValue;
    }

    /** 单个被评估对象的数据。 */
    @Getter
    @Setter
    public static class ObjectData {
        private String objectCode;
        private String objectName;
        /** indicatorCode -> 原始值 */
        private Map<String, Double> values = new LinkedHashMap<>();
        /** indicatorCode -> 是否缺失 */
        private List<String> missingIndicators = new ArrayList<>();
    }

    /** 便捷方法：构造算法输入向量。 */
    public double[] weightVector(List<IndicatorMeta> metas) {
        double[] w = new double[metas.size()];
        for (int i = 0; i < metas.size(); i++) {
            Double v = metas.get(i).getWeight();
            w[i] = v == null ? 0d : v;
        }
        return w;
    }

    public static Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
