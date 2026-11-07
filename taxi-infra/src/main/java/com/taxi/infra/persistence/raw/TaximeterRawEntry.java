package com.taxi.infra.persistence.raw;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 905 原始报文留痕记录，对应按日分表 t_taximeter_raw_YYYYMMDD。
 */
public record TaximeterRawEntry(
        String orderNo,
        String terminalId,
        Long vehicleId,
        String taximeterSn,
        int bizType,
        Integer msgSeq,
        String businessId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal pickupLng,
        BigDecimal pickupLat,
        BigDecimal dropoffLng,
        BigDecimal dropoffLat,
        BigDecimal amount,
        BigDecimal billingMileage,
        Integer waitingDuration,
        String rawHex,
        int parseStatus,
        String parseError,
        LocalDateTime reportTime) {
}
