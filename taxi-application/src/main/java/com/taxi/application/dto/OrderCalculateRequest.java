package com.taxi.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 费用核算请求。阶段 3 由调度人工录入，阶段 5 起改由 905 计价引擎写入。
 */
public record OrderCalculateRequest(
        @NotNull(message = "计价金额不能为空")
        @DecimalMin(value = "0.0", message = "计价金额不能为负数")
        BigDecimal totalAmount,

        @DecimalMin(value = "0.0", message = "优惠金额不能为负数")
        BigDecimal discountAmount,

        Long pricingPlanId) {
}
