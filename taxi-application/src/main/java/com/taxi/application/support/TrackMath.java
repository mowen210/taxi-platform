package com.taxi.application.support;

import com.taxi.domain.track.TrackPoint;

/**
 * 轨迹计算：相邻点球面距离（Haversine）。用于回放里程统计，不用于计费。
 */
public final class TrackMath {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private TrackMath() {
    }

    /** 两点间距离（km） */
    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** 轨迹总里程（km），单点或空轨迹为 0 */
    public static double totalDistanceKm(Iterable<TrackPoint> points) {
        double total = 0;
        TrackPoint previous = null;
        for (TrackPoint point : points) {
            if (point.lng() == null || point.lat() == null) {
                continue;
            }
            if (previous != null) {
                total += distanceKm(previous.lat().doubleValue(), previous.lng().doubleValue(),
                        point.lat().doubleValue(), point.lng().doubleValue());
            }
            previous = point;
        }
        return total;
    }
}
