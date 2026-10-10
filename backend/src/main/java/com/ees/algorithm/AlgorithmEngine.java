package com.ees.algorithm;

/**
 * 统一算法引擎接口。
 *
 * <p>后端通过本接口调用算法实现。当前内置 Java 实现（JAVA_NATIVE），
 * 同时保留 C++ 独立算法服务的适配位（CPP_ENGINE）；两者遵循同一输入输出协议
 * （附录 B），因此上层任务编排无需改动。</p>
 */
public interface AlgorithmEngine {

    /** 算法编码：AHP / FUZZY / WEIGHTED */
    String algorithmCode();

    /** 算法版本，必须可追溯，不能只使用前端显示名称 */
    String algorithmVersion();

    /** 执行评估。实现必须返回统一结构，不得抛出未包装异常。 */
    AlgorithmResult evaluate(AlgorithmRequest request);
}
