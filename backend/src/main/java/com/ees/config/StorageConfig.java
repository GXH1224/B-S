package com.ees.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 存储目录初始化：离线部署时所有上传、快照、报告、日志落在本地目录，不依赖外部服务。
 */
@Configuration
public class StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageConfig.class);

    private final EesProperties properties;

    public StorageConfig(EesProperties properties) {
        this.properties = properties;
    }

    @Bean
    public ApplicationRunner storageDirectoryInitializer() {
        return args -> {
            EesProperties.Storage s = properties.getStorage();
            List<String> dirs = List.of(
                    s.getRoot(), s.getUploadDir(), s.getSnapshotDir(),
                    s.getReportDir(), s.getTempDir(), s.getRoot() + "/logs");
            for (String d : dirs) {
                Path p = Paths.get(d).toAbsolutePath().normalize();
                try {
                    Files.createDirectories(p);
                } catch (IOException e) {
                    log.error("无法创建存储目录 {}: {}", p, e.getMessage());
                }
            }
            log.info("存储根目录: {}", Paths.get(s.getRoot()).toAbsolutePath().normalize());
        };
    }

    public Path resolve(String dir) {
        return Paths.get(dir).toAbsolutePath().normalize();
    }
}
