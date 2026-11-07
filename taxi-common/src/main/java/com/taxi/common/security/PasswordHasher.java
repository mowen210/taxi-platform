package com.taxi.common.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 口令散列（BCrypt，强度 10，自带盐）。
 * <p>数据库中只存散列值，任何日志、接口回传、异常信息中都不得出现明文口令。</p>
 */
public final class PasswordHasher {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);

    private PasswordHasher() {
    }

    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("口令不能为空");
        }
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验口令。散列为空时直接返回 false，防止历史脏数据绕过。
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
