package com.ees;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 效能评估系统启动类。
 *
 * <p><b>为什么排除 {@link UserDetailsServiceAutoConfiguration}：</b></p>
 *
 * <p>本系统使用自建的认证体系：{@code AuthService} 直接用 {@code PasswordEncoder.matches()}
 * 校验 BCrypt 哈希并签发 JWT，{@code JwtAuthenticationFilter} 负责把令牌还原成
 * {@code SecurityContext}，全项目没有使用 Spring Security 的
 * {@code AuthenticationManager} / {@code UserDetailsService} / {@code formLogin} / {@code httpBasic}。</p>
 *
 * <p>如果保留该自动配置，Spring Boot 会额外创建一个名为 {@code user} 的内存用户，
 * 并把随机生成的密码<b>明文打印到启动日志</b>里（"Using generated security password: ..."）。
 * 这违反了技术方案 9.1 的硬性要求：<b>"生产环境关闭调试接口和详细异常堆栈，日志不得打印密码和敏感配置"</b>，
 * 在企业离线交付的安全评审中会被判定为不合规。</p>
 *
 * <p>该内存用户本身无法登录本系统（因为未启用 HTTP Basic，实测对受保护接口返回 401），
 * 排除它不会影响任何功能，只是消除了日志中的密码输出与一个无用账号。</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan("com.ees.config")
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class EesApplication {

    public static void main(String[] args) {
        SpringApplication.run(EesApplication.class, args);
    }
}
