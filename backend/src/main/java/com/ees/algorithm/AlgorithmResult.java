package com.ees.algorithm;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一算法输出模型（对应附录 B.2 统一响应结构）。
 */
@Getter
@Setter
public class AlgorithmResult {

    private boolean success = true;
    private String taskId;
    private String algorithm;
    private String algorithmVersion;
    private String indicatorVersion;
    private String dataSnapshotId;

    /** 单对象场景的便捷字段，多对象时为最优对象的得分 */
    private BigDecimal score;
    private String level;

    private List<ObjectResult> objectResults = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();
    private Map<String, Object> extra = new LinkedHashMap<>();

    private String errorCode;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    public void addWarning(String w) {
        if (w != null && !w.isBlank() && !warnings.contains(w)) {
            this.warnings.add(w);
        }
    }

    public static AlgorithmResult failure(String code, String message) {
        AlgorithmResult r = new AlgorithmResult();
        r.setSuccess(false);
        r.setErrorCode(code);
        r.setError(message);
        r.setFinishedAt(LocalDateTime.now());
        return r;
    }

    /** 单个对象的评估结果。 */
    @Getter
    @Setter
    public static class ObjectResult {
        private String objectCode;
        private String objectName;
        private BigDecimal totalScore;
        private String level;
        /** 综合评判向量（模糊综合评判必须完整保存） */
        private List<Double> vector = new ArrayList<>();
        /** indicatorCode -> 权重 */
        private Map<String, Double> weights = new LinkedHashMap<>();
        /** indicatorCode -> 贡献值 */
        private Map<String, Double> contributions = new LinkedHashMap<>();
        private List<DetailItem> details = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
        private Map<String, Object> extra = new LinkedHashMap<>();
        private Integer ranking;
    }

    /** 指标明细项。 */
    @Getter
    @Setter
    public static class DetailItem {
        private String indicatorCode;
        private String indicatorName;
        private String parentCode;
        private Integer indicatorLevel = 1;
        private String unit;
        private String direction;
        private BigDecimal rawValue;
        private BigDecimal normalizedValue;
        private BigDecimal weight;
        private BigDecimal contribution;
        private BigDecimal score;
        private String grade;
        private Integer rankNo;
        private String remark;
    }
}
