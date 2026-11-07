package com.taxi.application.dto;

import java.time.LocalDateTime;

/**
 * 用户列表视图。手机号、身份证只输出脱敏值。
 */
public record UserView(
        Long id,
        String username,
        String realName,
        String phoneMask,
        String idCardMask,
        String email,
        Long fleetId,
        Integer status,
        LocalDateTime lastLoginTime,
        LocalDateTime createTime) {
}
