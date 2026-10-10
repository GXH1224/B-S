package com.ees.algorithm;

/**
 * 支持的评估算法（方案 2.3 R3：不少于三种）。
 */
public enum AlgorithmType {

    /** 层次分析法：计算层级指标权重并进行一致性检验 */
    AHP("层次分析法", "AHP"),

    /** 模糊综合评判：基于权重和隶属度矩阵输出评判等级 */
    FUZZY("模糊综合评判", "FUZZY"),

    /** 加权平均法：对标准化指标进行综合评分和排序 */
    WEIGHTED("加权平均法", "WEIGHTED"),

    /** 组合流程：AHP 计算权重后驱动 WEIGHTED 或 FUZZY */
    COMBO("组合评估流程", "COMBO");

    private final String displayName;
    private final String code;

    AlgorithmType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    public static AlgorithmType of(String code) {
        if (code == null) {
            return null;
        }
        for (AlgorithmType t : values()) {
            if (t.code.equalsIgnoreCase(code) || t.name().equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}
