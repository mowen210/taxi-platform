package com.taxi.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 派单请求。车辆与司机必须同属一个车队。
 */
public record OrderDispatchRequest(
        @NotNull(message = "司机不能为空")
        Long driverId,

        @NotNull(message = "车辆不能为空")
        Long vehicleId,

        @Size(max = 512, message = "备注长度不能超过 512")
        String remark) {
}
