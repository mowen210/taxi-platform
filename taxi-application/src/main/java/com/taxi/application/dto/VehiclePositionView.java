package com.taxi.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监控地图上的一个车辆位置。
 */
public record VehiclePositionView(
        Long vehicleId,
        String plateNo,
        String terminalId,
        Integer onlineStatus,
        BigDecimal lng,
        BigDecimal lat,
        Integer speed,
        Integer direction,
        LocalDateTime ts,
        Long alarmFlag) {
}
