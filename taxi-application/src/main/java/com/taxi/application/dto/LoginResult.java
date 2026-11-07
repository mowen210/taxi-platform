package com.taxi.application.dto;

/**
 * 登录结果。
 *
 * @param token     JWT
 * @param tokenType 固定 Bearer
 * @param expiresIn 有效期（秒）
 * @param user      用户档案
 */
public record LoginResult(String token, String tokenType, long expiresIn, UserProfile user) {
}
