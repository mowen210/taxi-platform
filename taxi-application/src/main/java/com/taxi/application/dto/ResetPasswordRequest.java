package com.taxi.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置他人口令请求。
 */
public record ResetPasswordRequest(
        @NotBlank(message = "新口令不能为空")
        @Size(min = 8, max = 64, message = "新口令长度需在 8~64 之间")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新口令必须同时包含字母与数字")
        String newPassword) {
}
