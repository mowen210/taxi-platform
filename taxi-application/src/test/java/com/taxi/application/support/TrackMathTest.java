package com.taxi.application.support;

import com.taxi.domain.track.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackMathTest {

    private static TrackPoint point(double lng, double lat) {
        return new TrackPoint(1L, "0138000001", "沪A00001", LocalDateTime.now(),
                BigDecimal.valueOf(lng), BigDecimal.valueOf(lat), 40, 90, 10, null, 1, 0, 12, null);
    }

    @Test
    @DisplayName("同一点距离为 0")
    void samePoint() {
        assertEquals(0.0, TrackMath.distanceKm(31.230416, 121.473701, 31.230416, 121.473701), 1e-9);
    }

    @Test
    @DisplayName("上海人民广场到陆家嘴约 3.4 公里")
    void knownDistance() {
        double km = TrackMath.distanceKm(31.230416, 121.473701, 31.239700, 121.499800);
        assertTrue(km > 2.4 && km < 3.2, "实际 " + km);
    }

    @Test
    @DisplayName("轨迹里程按相邻点累加，单点为 0")
    void totalDistance() {
        assertEquals(0.0, TrackMath.totalDistanceKm(List.of(point(121.4737, 31.2304))), 1e-9);
        double km = TrackMath.totalDistanceKm(List.of(
                point(121.4737, 31.2304),
                point(121.4747, 31.2304),
                point(121.4757, 31.2304)));
        assertTrue(km > 0.18 && km < 0.20, "实际 " + km);
    }

    @Test
    @DisplayName("空轨迹与缺失坐标不抛异常")
    void emptyAndNullSafe() {
        assertEquals(0.0, TrackMath.totalDistanceKm(List.of()), 1e-9);
        TrackPoint noCoord = new TrackPoint(1L, "t", "p", LocalDateTime.now(),
                null, null, 0, 0, 0, null, 0, 0, 0, null);
        assertEquals(0.0, TrackMath.totalDistanceKm(List.of(noCoord)), 1e-9);
    }
}
