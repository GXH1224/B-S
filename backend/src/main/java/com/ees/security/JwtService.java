package com.ees.security;

import com.ees.config.EesProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JWT 令牌服务。
 *
 * <p>签名密钥不再从配置里直接取默认值，而是统一由 {@link JwtSecretProvider} 解析：
 * 优先环境变量 {@code EES_JWT_SECRET}，其次本地密钥文件，最后首次启动自动生成，
 * 详见该类的说明。这样交付包里不含任何可用密钥。</p>
 *
 * <p>本类只校验签名、签发者与有效期，<b>不比对数据库中的用户</b>，
 * 因此签名密钥一旦泄漏即等同于可伪造任意用户的全部权限 —— 这也是必须移除内置默认密钥的原因。</p>
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final EesProperties properties;
    private final SecretKey key;

    public JwtService(EesProperties properties, JwtSecretProvider secretProvider) {
        this.properties = properties;
        String secret = secretProvider.getSecret();
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
            if (bytes.length < 32) {
                bytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length == 0) {
            // 正常情况下不可达：JwtSecretProvider 已保证解析出的密钥非空（无可用密钥时会自动生成）。
            // 这里显式拦截，避免落到下面的 bytes[i % bytes.length] 抛
            // ArithmeticException: / by zero，把"密钥为空"变成语义清晰的启动失败原因。
            throw new IllegalStateException("JWT 签名密钥为空：请设置环境变量 EES_JWT_SECRET，"
                    + "或确保存储根目录可写，以便自动生成 <存储根>/.jwt-secret");
        }
        if (bytes.length < 32) {
            // HS256 要求密钥不少于 256 bit，不足时补足
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = bytes.length; i < 32; i++) {
                padded[i] = (byte) (bytes[i % bytes.length] ^ (i * 31));
            }
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generate(LoginUser user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", user.getUserId());
        claims.put("name", user.getRealName());
        claims.put("role", user.getRoleCode());
        claims.put("scope", user.getDataScope());
        claims.put("perms", List.copyOf(user.getPermissions()));
        Date now = new Date();
        Date exp = new Date(now.getTime() + properties.getSecurity().getJwtExpireMinutes() * 60_000L);
        return Jwts.builder()
                .claims(claims)
                .subject(user.getUsername())
                .issuer(properties.getSecurity().getJwtIssuer())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    public long expireSeconds() {
        return properties.getSecurity().getJwtExpireMinutes() * 60L;
    }

    /** 解析令牌；非法或过期返回 null。 */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.getSecurity().getJwtIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Set<String> perms = new HashSet<>();
            Object raw = claims.get("perms");
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    perms.add(String.valueOf(o));
                }
            }
            Number uid = claims.get("uid", Number.class);
            return LoginUser.forToken(
                    uid == null ? null : uid.longValue(),
                    claims.getSubject(),
                    claims.get("name", String.class),
                    claims.get("role", String.class),
                    claims.get("scope", String.class),
                    perms);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
