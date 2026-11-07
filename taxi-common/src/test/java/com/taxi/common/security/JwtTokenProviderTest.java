package com.taxi.common.security;

import com.taxi.common.context.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JWT 签发与校验单测。纯单元测试，不依赖 Spring 容器与外部服务。
 */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-key-must-be-at-least-32-bytes-long";
    private static final String ISSUER = "taxi-test";

    @Test
    @DisplayName("签发后应能解析出用户标识与 jti")
    void shouldIssueAndParseToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, ISSUER, 600);
        LoginUser user = new LoginUser(9L, "tester", "测试员", 5L, 2, Set.of("system:user:list"), null);

        JwtTokenProvider.JwtIssue issue = provider.issue(user);
        Claims claims = provider.parse(issue.token());

        assertEquals("tester", claims.getSubject());
        assertEquals(9L, ((Number) claims.get("uid")).longValue());
        assertEquals(5L, ((Number) claims.get("fleetId")).longValue());
        assertEquals(2, ((Number) claims.get("scope")).intValue());
        assertEquals(issue.jti(), claims.getId());
        assertEquals(ISSUER, claims.getIssuer());
        assertNotNull(issue.expiresAtEpoch());
    }

    @Test
    @DisplayName("密钥不足 32 字节必须拒绝启动")
    void shouldRejectShortSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider("short-secret", ISSUER, 600));
    }

    @Test
    @DisplayName("有效期必须为正数")
    void shouldRejectNonPositiveTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider(SECRET, ISSUER, 0));
    }

    @Test
    @DisplayName("过期令牌解析必须失败")
    void shouldFailOnExpiredToken() throws InterruptedException {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, ISSUER, 1);
        LoginUser user = new LoginUser(1L, "expired", null, null, 1, Set.of(), null);
        String token = provider.issue(user).token();

        Thread.sleep(1300L);

        assertThrows(JwtException.class, () -> provider.parse(token));
    }

    @Test
    @DisplayName("篡改令牌签名必须失败")
    void shouldFailOnTamperedToken() {
        JwtTokenProvider provider = new JwtTokenProvider(SECRET, ISSUER, 600);
        JwtTokenProvider other = new JwtTokenProvider("another-secret-key-with-enough-length-32bytes", ISSUER, 600);
        String token = provider.issue(new LoginUser(1L, "u", null, null, 1, Set.of(), null)).token();

        assertThrows(JwtException.class, () -> other.parse(token));
    }
}
