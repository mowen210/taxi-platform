package com.taxi.application.dto;

import java.time.LocalDateTime;

/**
 * 车队视图。联系电话只输出脱敏值。
 */
public record FleetView(
        Long id,
        String fleetCode,
        String fleetName,
        String taxNo,
        String contact,
        String phoneMask,
        String address,
        Integer status,
        LocalDateTime createTime) {
}
