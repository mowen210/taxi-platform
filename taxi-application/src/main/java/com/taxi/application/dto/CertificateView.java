package com.taxi.application.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证件视图。daysLeft 为距到期天数，负数表示已过期。
 */
public record CertificateView(
        Long id,
        Long driverId,
        String driverName,
        Integer certType,
        String certTypeName,
        String certNo,
        LocalDate issueDate,
        LocalDate expireDate,
        Long daysLeft,
        String fileUrl,
        Integer warnStatus,
        LocalDateTime lastWarnTime) {
}
