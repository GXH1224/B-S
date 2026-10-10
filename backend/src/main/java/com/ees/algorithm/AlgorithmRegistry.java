package com.ees.algorithm;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 算法注册中心：按编码查找算法引擎。
 *
 * <p>由 {@link com.ees.config.AlgorithmConfig} 装配，不在类上标注 @Component，
 * 以便按 ees.algorithm.engine 切换实现。</p>
 */
public class AlgorithmRegistry {

    private final Map<String, AlgorithmEngine> engines = new LinkedHashMap<>();

    public AlgorithmRegistry(List<AlgorithmEngine> list) {
        for (AlgorithmEngine e : list) {
            engines.put(e.algorithmCode().toUpperCase(), e);
        }
    }

    public AlgorithmEngine get(String code) {
        AlgorithmEngine e = engines.get(code == null ? "" : code.toUpperCase());
        if (e == null) {
            throw new BizException(ErrorCode.ALGORITHM_NOT_SUPPORTED,
                    "不支持的评估算法：" + code + "，当前支持 " + engines.keySet());
        }
        return e;
    }

    public boolean supports(String code) {
        return engines.containsKey(code == null ? "" : code.toUpperCase());
    }

    public Map<String, String> supported() {
        Map<String, String> m = new LinkedHashMap<>();
        engines.forEach((k, v) -> m.put(k, v.algorithmVersion()));
        return m;
    }
}
