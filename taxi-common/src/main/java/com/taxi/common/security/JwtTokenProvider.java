package com.taxi.common.security;

import com.taxi.common.context.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 签发与解析。
 * <p>设计要点：短期有效（默认 2 小时）、带 jti 支持注销黑名单、算法固定 HS256、
 * 权限码不进令牌（放 Redis，便于动态改权后即时生效）。</p>
 */
public class JwtTokenProvider {

    /** HS256 要求密钥长度不小于 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final String issuer;
    private final long ttlSeconds;

    public JwtTokenProvider(String secret, String issuer, long ttlSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT 密钥长度不足 32 字节，存在被爆破风险");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("JWT 有效期必须大于 0");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * 签发令牌。
     *
     * @param user 登录用户
     * @return 令牌、jti 与过期时间
     */
    public JwtIssue issue(LoginUser user) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(ttlSeconds);
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder()
                .id(jti)
                .issuer(issuer)
                .subject(user.username())
                .claim("uid", user.userId())
                .claim("realName", user.realName())
                .claim("fleetId", user.fleetId())
                .claim("scope", user.dataScope())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                // 固定 HS256：不随密钥长度自动升级，保证与接口文档、终端网关约定的算法一致
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
        return new JwtIssue(token, jti, expireAt.getEpochSecond());
    }

    /**
     * 解析并校验令牌（签名、issuer、过期时间）。
     *
     * @throws JwtException 签名错误、过期、格式非法等
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 由 Claims 还原登录用户（权限码由调用方从 Redis 补齐） */
    public LoginUser toLoginUser(Claims claims, Set<String> permissions) {
        Number uid = claims.get("uid", Number.class);
        Number fleetId = claims.get("fleetId", Number.class);
        Number scope = claims.get("scope", Number.class);
        return new LoginUser(
                uid == null ? null : uid.longValue(),
                claims.getSubject(),
                claims.get("realName", String.class),
                fleetId == null ? null : fleetId.longValue(),
                scope == null ? null : scope.intValue(),
                permissions,
                claims.getId());
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    /**
     * 签发结果。
     *
     * @param token         完整 JWT
     * @param jti           令牌唯一标识
     * @param expiresAtEpoch 过期时间（秒级时间戳）
     */
    public record JwtIssue(String token, String jti, long expiresAtEpoch) {
    }
}
