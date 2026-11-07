package com.taxi.common.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 敏感字段加解密单测。
 */
class AesGcmCipherTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @BeforeAll
    static void init() {
        AesGcmCipher.init(KEY);
    }

    @Test
    @DisplayName("加密后应可解密还原")
    void shouldRoundTrip() {
        String phone = "13800001111";
        String encrypted = AesGcmCipher.encrypt(phone);

        assertNotEquals(phone, encrypted);
        assertEquals(phone, AesGcmCipher.decrypt(encrypted));
    }

    @Test
    @DisplayName("同一明文每次加密结果不同（随机 IV）")
    void shouldProduceDifferentCipherText() {
        String first = AesGcmCipher.encrypt("310101199001011234");
        String second = AesGcmCipher.encrypt("310101199001011234");

        assertNotEquals(first, second);
        assertEquals("310101199001011234", AesGcmCipher.decrypt(first));
        assertEquals("310101199001011234", AesGcmCipher.decrypt(second));
    }

    @Test
    @DisplayName("空值原样返回，不产生密文")
    void shouldPassThroughNullAndEmpty() {
        assertNull(AesGcmCipher.encrypt(null));
        assertEquals("", AesGcmCipher.encrypt(""));
        assertNull(AesGcmCipher.decrypt(null));
    }

    @Test
    @DisplayName("非法密文解密必须抛错，不允许静默返回原文")
    void shouldFailOnInvalidCipherText() {
        assertThrows(IllegalStateException.class, () -> AesGcmCipher.decrypt("not-a-cipher-text"));
    }

    @Test
    @DisplayName("密钥长度不是 32 字节必须拒绝")
    void shouldRejectInvalidKeyLength() {
        String shortKey = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> AesGcmCipher.init(shortKey));
    }

    @Test
    @DisplayName("脱敏工具输出符合页面要求")
    void shouldMaskSensitiveValues() {
        assertEquals("138****1111", MaskUtil.maskPhone("13800001111"));
        assertEquals("310101********1234", MaskUtil.maskIdCard("310101199001011234"));
        assertEquals("张*", MaskUtil.maskName("张三"));
    }
}
