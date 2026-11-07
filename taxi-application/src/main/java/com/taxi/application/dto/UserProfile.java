package com.taxi.application.dto;

import java.time.LocalDateTime;

/**
 * 登录用户档案（不含任何口令信息，手机号已脱敏）。
 */
public record UserProfile(
        Long userId,
        String username,
        String realName,
        String phoneMask,
        String email,
        Long fleetId,
        Integer status,
        Integer dataScope,
        java.util.List<Long> roleIds,
        java.util.List<String> permissions,
        LocalDateTime lastLoginTime) {
}
