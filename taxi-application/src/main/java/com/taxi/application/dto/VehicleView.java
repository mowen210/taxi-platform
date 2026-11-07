package com.taxi.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 车辆视图。daysLeft 为负数表示已过期，供前端标红。
 */
public record VehicleView(
        Long id,
        String plateNo,
        String plateColor,
        String vehicleType,
        String vin,
        String engineNo,
        String ownerName,
        Long fleetId,
        String fleetName,
        Long driverId,
        String driverName,
        Integer status,
        LocalDate insuranceExpireDate,
        Long insuranceDaysLeft,
        LocalDate annualInspectExpireDate,
        Long inspectDaysLeft,
        LocalDate purchaseDate,
        String remark,
        LocalDateTime createTime) {
}
