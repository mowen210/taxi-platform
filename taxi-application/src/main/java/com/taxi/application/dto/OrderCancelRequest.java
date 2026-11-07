package com.taxi.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 取消订单请求。
 */
public record OrderCancelRequest(
        @NotBlank(message = "取消原因不能为空")
        @Size(max = 512, message = "取消原因长度不能超过 512")
        String reason) {
}
