package com.taxi.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 新增/修改司机请求。
 */
public record DriverSaveRequest(
        @NotBlank(message = "司机编号不能为空")
        @Size(max = 64, message = "司机编号长度不能超过 64")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "司机编号只允许字母、数字、下划线、横线")
        String driverCode,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名长度不能超过 64")
        String name,

        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String phone,

        @Pattern(regexp = "^$|^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
        String idCard,

        @Min(value = 1, message = "性别取值 1 或 2")
        @Max(value = 2, message = "性别取值 1 或 2")
        Integer gender,

        @Size(max = 32, message = "驾驶证号长度不能超过 32")
        String licenseNo,

        @Size(max = 8, message = "准驾车型长度不能超过 8")
        String licenseClass,

        LocalDate licenseExpireDate,

        @Size(max = 32, message = "从业资格证号长度不能超过 32")
        String qualificationNo,

        LocalDate qualificationExpireDate,

        Long fleetId,

        @Min(value = 1, message = "状态取值 1~3")
        @Max(value = 3, message = "状态取值 1~3")
        Integer status,

        LocalDate entryDate,

        @Size(max = 255, message = "备注长度不能超过 255")
        String remark) {
}
