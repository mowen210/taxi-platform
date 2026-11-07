package com.taxi.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 司机视图。手机号、身份证只输出脱敏值。
 */
public record DriverView(
        Long id,
        String driverCode,
        String name,
        String phoneMask,
        String idCardMask,
        Integer gender,
        String licenseNo,
        String licenseClass,
        LocalDate licenseExpireDate,
        String qualificationNo,
        LocalDate qualificationExpireDate,
        Long fleetId,
        String fleetName,
        Integer onlineStatus,
        BigDecimal safetyScore,
        Integer status,
        LocalDate entryDate,
        String remark,
        LocalDateTime createTime) {
}
