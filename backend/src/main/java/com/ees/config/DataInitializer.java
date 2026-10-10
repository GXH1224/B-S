package com.ees.config;

import com.ees.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * 启动数据初始化：创建默认账号（仅当用户表为空时执行，不覆盖已有数据）。
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AuthService authService;

    public DataInitializer(AuthService authService) {
        this.authService = authService;
    }

    @Bean
    @Order(100)
    public ApplicationRunner defaultUserInitializer() {
        return args -> {
            try {
                authService.initDefaultUsers();
            } catch (Exception e) {
                log.error("初始化默认账号失败: {}", e.getMessage());
            }
        };
    }
}
