package com.ees.config;

import com.ees.security.JwtSecretProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * 启动信息横幅：把页面地址、默认账号与安全自检结果打印到控制台。
 *
 * <p>IDEA 的控制台会自动把 http:// 链接变成可点击链接，点一下即可跳转到系统页面。</p>
 */
@Configuration
public class StartupBanner {

    private static final Logger log = LoggerFactory.getLogger(StartupBanner.class);

    private final Environment environment;
    private final JwtSecretProvider jwtSecretProvider;

    @Value("${server.port:8080}")
    private int port;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    public StartupBanner(Environment environment, JwtSecretProvider jwtSecretProvider) {
        this.environment = environment;
        this.jwtSecretProvider = jwtSecretProvider;
    }

    @Bean
    @Order(200)
    public ApplicationRunner startupBannerRunner() {
        return args -> {
            String base = "http://127.0.0.1:" + port + normalizeContextPath();
            String profiles = String.join(",", environment.getActiveProfiles());
            boolean frontendServed = frontendAvailable();

            log.info("");
            log.info("============================================================");
            log.info("  效能评估系统 已启动");
            log.info("------------------------------------------------------------");
            log.info("  页面地址   : {}/", base);
            log.info("  接口地址   : {}/api", base);
            log.info("  健康检查   : {}/api/health", base);
            // 技术方案 9.1 要求"日志不得打印密码和敏感配置"：初始口令只在 dev 档显示，
            // prod 档只提示账号名，避免生产日志里出现口令。
            if (isDevProfile()) {
                log.info("  默认账号   : admin / admin123（仅开发档显示，首次登录后请立即修改）");
            } else {
                log.info("  默认账号   : admin（初始口令见交付文档，首次登录后请立即修改）");
            }
            log.info("------------------------------------------------------------");
            if (frontendServed) {
                log.info("  前端资源   : 已由后端托管，直接点上面的页面地址即可");
            } else {
                log.info("  前端资源   : 未找到 frontend/dist");
                log.info("               请先执行 npm run build，或改跑前端 npm run dev 后访问 5173");
            }
            log.info("  运行环境   : {}", profiles.isEmpty() ? "default" : profiles);
            log.info("  签名密钥   : {}（未打印密钥本身）", jwtSecretProvider.describeSource());
            log.info("============================================================");
            log.info("");
        };
    }

    /** 是否处于开发档（dev 档允许把初始口令显示到控制台，生产档禁止，见技术方案 9.1）。 */
    private boolean isDevProfile() {
        for (String p : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }

    /** 规范化 context-path："/" 与空串都视为无前缀，避免拼出 "//"。 */
    private String normalizeContextPath() {
        if (contextPath == null) {
            return "";
        }
        String p = contextPath.trim();
        if (p.isEmpty() || "/".equals(p)) {
            return "";
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    /** 判断前端构建产物是否可被后端直接提供。 */
    private boolean frontendAvailable() {
        try {
            String[] locations = environment.getProperty("spring.web.resources.static-locations", "")
                    .split(",");
            for (String loc : locations) {
                String l = loc.trim();
                if (l.startsWith("file:")) {
                    java.nio.file.Path p = java.nio.file.Paths
                            .get(l.substring("file:".length())).toAbsolutePath().normalize();
                    if (java.nio.file.Files.isRegularFile(p.resolve("index.html"))) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("检查前端资源失败: {}", e.getMessage());
        }
        return false;
    }
}
