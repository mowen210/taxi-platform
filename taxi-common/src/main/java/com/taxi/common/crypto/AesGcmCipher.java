package com.taxi.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 手机号、身份证等敏感字段的 AES-256/GCM 加解密工具。
 * <p>密文格式：Base64( IV(12B) + CipherText + Tag(16B) )。每次加密使用随机 IV，允许同一明文产生不同密文。</p>
 * <p>密钥由 {@link #init(String)} 在应用启动时注入（环境变量 AES_KEY 提供 32 字节 Base64），禁止硬编码到代码库。</p>
 */
public final class AesGcmCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile SecretKey secretKey;

    private AesGcmCipher() {
    }

    /**
     * 初始化密钥，应用启动时调用一次。
     *
     * @param base64Key Base64 编码的 32 字节密钥
     */
    public static void init(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalArgumentException("AES 密钥不能为空");
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != 32) {
            throw new IllegalArgumentException("AES 密钥长度必须为 32 字节（AES-256），当前：" + raw.length);
        }
        secretKey = new SecretKeySpec(raw, "AES");
    }

    /** 是否已完成初始化 */
    public static boolean initialized() {
        return secretKey != null;
    }

    /**
     * 加密，null 或空串原样返回，避免污染数据库空值语义。
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LENGTH + cipherText.length];
            System.arraycopy(iv, 0, out, 0, IV_LENGTH);
            System.arraycopy(cipherText, 0, out, IV_LENGTH, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    /**
     * 解密，解密失败说明密钥不匹配或密文被篡改，直接抛出异常并记录 WARN，禁止静默返回原文。
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            if (all.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度非法");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(all, 0, iv, 0, IV_LENGTH);
            byte[] body = new byte[all.length - IV_LENGTH];
            System.arraycopy(all, IV_LENGTH, body, 0, body.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段解密失败（密钥不匹配或密文被篡改）", e);
        }
    }

    private static SecretKey requireKey() {
        SecretKey key = secretKey;
        if (key == null) {
            throw new IllegalStateException("AES 密钥未初始化，请配置 aes.base64-key");
        }
        return key;
    }
}
