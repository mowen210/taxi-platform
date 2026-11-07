package com.taxi.infra.crypto;

import com.taxi.common.crypto.AesGcmCipher;
import com.taxi.common.crypto.HmacUtil;
import com.taxi.common.crypto.MaskUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 敏感字段编解码器（手机号、身份证）。
 * <p>约定：调用方持有的实体字段为明文（仅内存），写库前经本类转换为密文；
 * 前端回传的脱敏值（含 {@code *}）一律视为「不修改」，返回 null 让 MyBatis-Plus 跳过该列。</p>
 */
@Component
public class SensitiveFieldCodec {

    private final String hmacSecret;

    public SensitiveFieldCodec(@Value("${security.hmac-secret}") String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    /** 是否脱敏值（掩码回填） */
    public boolean isMasked(String value) {
        return value != null && value.indexOf('*') >= 0;
    }

    /** 加密；脱敏值返回 null（表示不修改） */
    public String encryptOrNull(String plain) {
        if (plain == null || plain.isBlank() || isMasked(plain)) {
            return null;
        }
        return AesGcmCipher.encrypt(plain.trim());
    }

    /** 手机号摘要；脱敏值返回 null */
    public String phoneHashOrNull(String plain) {
        if (plain == null || plain.isBlank() || isMasked(plain)) {
            return null;
        }
        return HmacUtil.sha256Hex(plain.trim(), hmacSecret);
    }

    /** 身份证脱敏值；脱敏值入参返回 null */
    public String idCardMaskOrNull(String plain) {
        if (plain == null || plain.isBlank() || isMasked(plain)) {
            return null;
        }
        return MaskUtil.maskIdCard(plain.trim());
    }

    public String decrypt(String cipher) {
        return AesGcmCipher.decrypt(cipher);
    }
}
