package com.ees.util;

import com.ees.common.BizException;
import com.ees.common.ErrorCode;
import com.ees.config.EesProperties;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 对称加密工具：数据源密码加密保存，页面不回显完整密码（方案 9.1）。
 */
public final class CryptoUtils {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final String PREFIX = "ENC:";

    private CryptoUtils() {
    }

    private static SecretKeySpec key(EesProperties properties) {
        String secret = properties.getSecurity().getJwtSecret();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] k = md.digest(("ees-datasource::" + secret).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(k, "AES");
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "生成加密密钥失败", e);
        }
    }

    public static String encrypt(String plain, EesProperties properties) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key(properties), new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "密码加密失败", e);
        }
    }

    public static String decrypt(String cipherText, EesProperties properties) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        if (!cipherText.startsWith(PREFIX)) {
            // 兼容历史明文数据
            return cipherText;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            byte[] data = new byte[all.length - IV_BYTES];
            System.arraycopy(all, IV_BYTES, data, 0, data.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key(properties), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "密码解密失败，可能加密密钥已变更，请重新录入数据源密码", e);
        }
    }

    public static boolean isEncrypted(String s) {
        return s != null && s.startsWith(PREFIX);
    }
}
