package com.taxi.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 行程结束请求。金额与里程可空，后续由 905 报文或计价引擎补齐。
 */
public record OrderFinishRequest(
        LocalDateTime finishTime,

        @DecimalMin(value = "0.0", message = "里程不能为负数")
        BigDecimal mileage,

        @DecimalMin(value = "0.0", message = "空驶里程不能为负数")
        BigDecimal emptyMileage,

        @Min(value = 0, message = "等候时长不能为负数")
        Integer waitingDuration,

        @Min(value = 0, message = "行程时长不能为负数")
        Integer durationSec,

        @Size(max = 512, message = "备注长度不能超过 512")
        String remark) {
}
