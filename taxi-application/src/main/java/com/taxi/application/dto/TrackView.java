package com.taxi.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 轨迹回放数据：点位 + 汇总（里程、最高速度、时长）。
 */
public record TrackView(
        Long vehicleId,
        String plateNo,
        String provider,
        LocalDateTime start,
        LocalDateTime end,
        int pointCount,
        double distanceKm,
        int maxSpeed,
        long durationSec,
        List<Point> points) {

    public record Point(LocalDateTime ts, BigDecimal lng, BigDecimal lat, int speed, int direction) {
    }
}
