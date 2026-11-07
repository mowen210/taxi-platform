package com.taxi.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 新增/修改车辆请求。
 */
public record VehicleSaveRequest(
        @NotBlank(message = "车牌号不能为空")
        @Size(max = 16, message = "车牌号长度不能超过 16")
        @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z0-9]{5,16}$", message = "车牌号格式不正确")
        String plateNo,

        @Size(max = 8, message = "车牌颜色长度不能超过 8")
        String plateColor,

        @Size(max = 32, message = "车辆类型长度不能超过 32")
        String vehicleType,

        @Size(max = 32, message = "车架号长度不能超过 32")
        String vin,

        @Size(max = 32, message = "发动机号长度不能超过 32")
        String engineNo,

        @Size(max = 64, message = "车主长度不能超过 64")
        String ownerName,

        Long fleetId,

        Long driverId,

        @Min(value = 1, message = "状态取值 1~4")
        @Max(value = 4, message = "状态取值 1~4")
        Integer status,

        LocalDate insuranceExpireDate,

        LocalDate annualInspectExpireDate,

        LocalDate purchaseDate,

        @Size(max = 255, message = "备注长度不能超过 255")
        String remark) {
}
