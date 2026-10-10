package com.ees.security;

import com.ees.config.EesProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * JWT 签名密钥的解析与落地。
 *
 * <p><b>为什么需要这个类（企业离线交付的安全整改）：</b></p>
 *
 * <p>整改前，{@code application.yml} 与 {@code EesProperties} 各内置了一个"默认密钥"。
 * 这类默认值本质上是一把<b>随源码一起交付给甲方的公开密钥</b>：任何拿到交付包（甚至只拿到
 * 安装介质）的人都能用它本地签发一枚 {@code role=SYSTEM_ADMIN} 的令牌，
 * 而 {@link JwtService} 只校验签名与签发者，不比对数据库中的用户，
 * 于是可以直接调用全部系统管理员接口。</p>
 *
 * <p>交付前的实测结论（整改前）：用仓库中的默认密钥伪造令牌访问 {@code /api/users}、
 * {@code /api/configs}、{@code /api/audit-logs} 全部返回成功，
 * 甚至能 {@code POST /api/data-sources} 写入数据 —— 属于<b>可越权读写</b>的真实漏洞，
 * 在安全评审中会被判定为不合规，故彻底移除默认密钥。</p>
 *
 * <p><b>整改后的取值优先级（三级回退，无需任何配置即可离线运行）：</b></p>
 * <ol>
 *   <li>环境变量 / 配置项 {@code EES_JWT_SECRET}（甲方有统一密钥管理要求时使用）；</li>
 *   <li>本地密钥文件 {@code <ees.storage.root>/.jwt-secret}（首次启动自动生成，<b>持久化保存</b>）；</li>
 *   <li>上述都不可用时，退化为进程内随机密钥（仅告警，重启后需要重新登录）。</li>
 * </ol>
 *
 * <p><b>为什么必须持久化而不是每次启动随机生成：</b>
 * {@link com.ees.util.CryptoUtils} 的数据源密码 AES 密钥同样派生自本密钥，
 * 若每次启动都换新密钥，已保存的数据源密码将无法解密（表现为"密码解密失败"），
 * 因此密钥必须跨重启保持稳定。密钥文件位于 {@code data} 目录下，
 * 而 {@code backend/data/} 已被 {@code .gitignore} 忽略，不会进入版本库。</p>
 *
 * <p><b>同时保留的兼容处理：</b>若甲方配置里残留了整改前的那两个公开密钥，
 * 一律识别为不安全并忽略（见 {@link #REMOVED_INSECURE_SECRETS}），避免"改了配置仍不安全"。</p>
 */
@Component
public class JwtSecretProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretProvider.class);

    /** 自动生成的部署密钥文件名，落在 {@code ees.storage.root} 目录下。 */
    private static final String KEY_FILE_NAME = ".jwt-secret";

    /** 自动生成密钥的随机字节数（512 bit，远超 HS256 要求的 256 bit）。 */
    private static final int KEY_BYTES = 64;

    /** HS256 允许的最短密钥长度（256 bit），短于该长度视为不安全。 */
    private static final int MIN_SAFE_LENGTH = 32;

    /**
     * 整改前内置在代码与配置里的"默认密钥"。
     *
     * <p>这两个字符串曾经随源码交付，等同于公开值，任何人都能据此伪造管理员令牌；
     * 因此即使甲方配置中仍残留它们也一律忽略，并给出明确告警。</p>
     */
    private static final Set<String> REMOVED_INSECURE_SECRETS = Set.of(
            "ZWVzLWVmZmljaWVuY3ktZXZhbHVhdGlvbi1zeXN0ZW0tand0LXNlY3JldC1rZXktMjAyNi1wbGVhc2UtY2hhbmdlLWluLXByb2R1Y3Rpb24=",
            "ees-default-secret-please-change-in-production-environment-2026");

    private final EesProperties properties;
    private final String secret;
    private final String source;

    public JwtSecretProvider(EesProperties properties) {
        this.properties = properties;
        String[] resolved = resolve();
        this.secret = resolved[0];
        this.source = resolved[1];
        // 回写到 EesProperties：JwtService（令牌签名）与 CryptoUtils（数据源密码加密）
        // 都从配置对象读取该值，此处统一为最终生效的密钥，避免两处口径不一致。
        properties.getSecurity().setJwtSecret(this.secret);
    }

    /** 最终生效的 JWT 签名密钥。 */
    public String getSecret() {
        return secret;
    }

    /** 密钥来源的说明文字，用于启动自检横幅展示。 */
    public String describeSource() {
        return source;
    }

    /** @return 长度为 2 的数组：[0] 最终密钥，[1] 密钥来源说明。 */
    private String[] resolve() {
        String configured = trimToNull(properties.getSecurity().getJwtSecret());

        if (configured != null && REMOVED_INSECURE_SECRETS.contains(configured)) {
            log.warn("======================================================================");
            log.warn("安全告警：配置中的 jwt-secret 仍是整改前内置的演示密钥。");
            log.warn("该密钥随源码交付、等同公开值，任何人都能据此伪造系统管理员令牌，");
            log.warn("已在本次启动中被忽略，改用自动生成的部署密钥。");
            log.warn("请删除配置中的该项，或改为设置环境变量 EES_JWT_SECRET。");
            log.warn("======================================================================");
            configured = null;
        }

        if (configured != null) {
            if (configured.length() < MIN_SAFE_LENGTH) {
                log.warn("安全告警：EES_JWT_SECRET 长度仅 {} 字符，短于推荐的 {} 字符，存在被暴力猜解的风险。",
                        configured.length(), MIN_SAFE_LENGTH);
            }
            log.info("JWT 签名密钥来源：环境变量 / 配置项 EES_JWT_SECRET（长度 {}）。", configured.length());
            return new String[]{configured, "环境变量 EES_JWT_SECRET"};
        }

        return loadOrCreateLocalKey();
    }

    /**
     * 读取本地密钥文件；不存在或内容不合法时生成一个新的并持久化。
     *
     * @return 长度为 2 的数组：[0] 最终密钥，[1] 密钥来源说明。
     */
    private String[] loadOrCreateLocalKey() {
        Path keyFile = Paths.get(properties.getStorage().getRoot(), KEY_FILE_NAME)
                .toAbsolutePath().normalize();

        try {
            if (Files.isRegularFile(keyFile)) {
                String existing = trimToNull(new String(Files.readAllBytes(keyFile), StandardCharsets.UTF_8));
                if (existing != null && existing.length() >= MIN_SAFE_LENGTH) {
                    log.info("JWT 签名密钥来源：本地密钥文件 {}", keyFile);
                    return new String[]{existing, "本地密钥文件 " + keyFile};
                }
                log.warn("本地密钥文件 {} 内容为空或过短，将重新生成。", keyFile);
            }

            String generated = generateKey();
            Path parent = keyFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(keyFile, (generated + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            restrictToOwner(keyFile);
            log.info("首次启动：已自动生成本机专用的 JWT 签名密钥并保存到 {}", keyFile);
            log.info("该密钥不在版本库中，请随 data 目录一并备份；如需自行指定，可设置环境变量 EES_JWT_SECRET 覆盖。");
            return new String[]{generated, "首次启动自动生成（已保存到 " + keyFile + "）"};

        } catch (IOException | RuntimeException e) {
            // 目录只读等异常场景：退化为进程内随机密钥，保证系统仍能启动，
            // 但必须明确告警，因为重启后需要重新登录、且已保存的数据源密码将无法解密。
            log.warn("无法读写本地密钥文件 {}（{}），本次启动退化为进程内随机密钥；"
                    + "重启后所有登录态失效，且已保存的数据源密码将无法解密。"
                    + "建议设置环境变量 EES_JWT_SECRET 以获得稳定密钥。", keyFile, e.toString());
            return new String[]{generateKey(), "进程内随机密钥（临时，未持久化）"};
        }
    }

    private String generateKey() {
        byte[] raw = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    /**
     * 尽力收紧密钥文件权限（仅属主可读写）。
     * Windows 上 {@link java.io.File} 的权限接口实现有限，失败不影响功能，故静默忽略。
     */
    private void restrictToOwner(Path keyFile) {
        try {
            java.io.File file = keyFile.toFile();
            file.setReadable(false, false);
            file.setReadable(true, true);
            file.setWritable(false, false);
            file.setWritable(true, true);
        } catch (RuntimeException ignored) {
            // 权限收紧为尽力而为，不影响密钥功能
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
