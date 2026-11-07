package com.taxi.common.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * HMAC-SHA256 摘要工具。
 * <p>用途：加密字段的等值检索（手机号查询不能对密文做 like，改为对 HMAC 摘要建索引），
 * 以及接口防重放签名。密钥独立于 AES 密钥。</p>
 */
public final class HmacUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacUtil() {
    }

    /**
     * 计算摘要。
     *
     * @param value 明文
     * @param secret 秘钥，不允许为空
     * @return 64 位小写十六进制串
     */
    public static String sha256Hex(String value, String secret) {
        if (value == null) {
            return null;
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("HMAC 密钥不能为空");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    /**
     * 恒定时间比较，用于验签，避免时序侧信道。
     */
    public static boolean safeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
