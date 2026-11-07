package com.taxi.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 转异常订单请求。
 */
public record OrderAbnormalRequest(
        @NotBlank(message = "异常原因不能为空")
        @Size(max = 512, message = "异常原因长度不能超过 512")
        String reason) {
}
