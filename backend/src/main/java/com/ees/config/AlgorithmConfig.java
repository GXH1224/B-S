package com.ees.config;

import com.ees.algorithm.AlgorithmEngine;
import com.ees.algorithm.AlgorithmRegistry;
import com.ees.algorithm.AlgorithmType;
import com.ees.algorithm.cpp.CppEngineAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

/**
 * 算法引擎装配：根据 ees.algorithm.engine 选择内置 Java 实现或 C++ 独立算法服务。
 *
 * <p>默认 JAVA_NATIVE，保证离线环境开箱可用；
 * 甲方提供 C++ 组件并通过基准测试后，切换为 CPP_ENGINE 即可，上层无需改动。</p>
 */
@Configuration
public class AlgorithmConfig {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmConfig.class);

    private final EesProperties properties;

    public AlgorithmConfig(EesProperties properties) {
        this.properties = properties;
    }

    @Bean
    @Primary
    public AlgorithmRegistry algorithmRegistry(List<AlgorithmEngine> javaEngines) {
        String mode = properties.getAlgorithm().getEngine();
        boolean useCpp = "CPP_ENGINE".equalsIgnoreCase(mode);
        log.info("算法引擎模式: {} (内置实现版本 {})", useCpp ? "CPP_ENGINE (C++ 独立算法服务)" : "JAVA_NATIVE",
                properties.getAlgorithm().getVersion());

        List<AlgorithmEngine> engines = new ArrayList<>(javaEngines);
        if (useCpp) {
            // AHP 与 FUZZY 优先使用甲方已有 C++ 实现；WEIGHTED 仍用内置实现兜底
            engines.add(new CppEngineAdapter(AlgorithmType.AHP.getCode(), properties));
            engines.add(new CppEngineAdapter(AlgorithmType.FUZZY.getCode(), properties));
        }
        return new AlgorithmRegistry(engines);
    }
}
