package com.taxi.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增/修改车队请求。
 */
public record FleetSaveRequest(
        @NotBlank(message = "车队编码不能为空")
        @Size(max = 64, message = "车队编码长度不能超过 64")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "车队编码只允许字母、数字、下划线、横线")
        String fleetCode,

        @NotBlank(message = "车队名称不能为空")
        @Size(max = 128, message = "车队名称长度不能超过 128")
        String fleetName,

        @Pattern(regexp = "^$|^[A-Z0-9]{15,20}$", message = "税号需为 15~20 位大写字母或数字")
        String taxNo,

        @Size(max = 64, message = "联系人长度不能超过 64")
        String contact,

        @Pattern(regexp = "^$|^1\\d{10}$", message = "联系电话格式不正确")
        String phone,

        @Size(max = 255, message = "地址长度不能超过 255")
        String address,

        @Min(value = 0, message = "状态取值 0 或 1")
        @Max(value = 1, message = "状态取值 0 或 1")
        Integer status) {
}
