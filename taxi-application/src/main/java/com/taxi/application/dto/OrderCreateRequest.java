package com.taxi.application.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建电召订单请求。
 */
public record OrderCreateRequest(
        @Size(max = 64, message = "乘客称呼长度不能超过 64")
        String passengerName,

        @Pattern(regexp = "^$|^1\\d{10}$", message = "乘客手机号格式不正确")
        String passengerPhone,

        @Size(max = 255, message = "上车地址长度不能超过 255")
        String startAddress,

        @DecimalMin(value = "-180.0", message = "上车经度超出范围")
        @DecimalMax(value = "180.0", message = "上车经度超出范围")
        BigDecimal startLng,

        @DecimalMin(value = "-90.0", message = "上车纬度超出范围")
        @DecimalMax(value = "90.0", message = "上车纬度超出范围")
        BigDecimal startLat,

        @Size(max = 255, message = "下车地址长度不能超过 255")
        String endAddress,

        @DecimalMin(value = "-180.0", message = "下车经度超出范围")
        @DecimalMax(value = "180.0", message = "下车经度超出范围")
        BigDecimal endLng,

        @DecimalMin(value = "-90.0", message = "下车纬度超出范围")
        @DecimalMax(value = "90.0", message = "下车纬度超出范围")
        BigDecimal endLat,

        Long pricingPlanId,

        @Size(max = 512, message = "备注长度不能超过 512")
        String remark) {
}
