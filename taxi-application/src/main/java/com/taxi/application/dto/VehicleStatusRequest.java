package com.taxi.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 车辆状态变更请求。
 */
public record VehicleStatusRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 1, message = "状态取值 1~4")
        @Max(value = 4, message = "状态取值 1~4")
        Integer status,

        @Size(max = 255, message = "备注长度不能超过 255")
        String remark) {
}
