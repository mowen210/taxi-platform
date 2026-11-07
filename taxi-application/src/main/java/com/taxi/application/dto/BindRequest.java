package com.taxi.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 车辆-终端-计价器绑定请求。
 */
public record BindRequest(
        @NotNull(message = "车辆不能为空")
        Long vehicleId,

        @NotBlank(message = "终端手机号不能为空")
        @Size(max = 32, message = "终端手机号长度不能超过 32")
        String terminalId,

        @NotBlank(message = "计价器 SN 不能为空")
        @Size(max = 64, message = "计价器 SN 长度不能超过 64")
        String taximeterSn,

        @Size(max = 255, message = "备注长度不能超过 255")
        String remark) {
}
