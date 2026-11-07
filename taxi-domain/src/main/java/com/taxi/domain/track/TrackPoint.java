package com.taxi.domain.track;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一个定位点。写 TDengine 时对应 st_track 的一行，写 MySQL 兜底表时对应 t_track。
 */
public record TrackPoint(
        Long vehicleId,
        String terminalId,
        String plateNo,
        LocalDateTime ts,
        BigDecimal lng,
        BigDecimal lat,
        int speed,
        int direction,
        int altitude,
        Double mileage,
        Integer accState,
        long alarmFlag,
        long statusFlag,
        String orderNo) {

    public boolean valid() {
        return vehicleId != null && terminalId != null && ts != null && lng != null && lat != null;
    }
}
