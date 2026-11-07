package com.taxi.application.event;

import com.taxi.domain.track.TrackPoint;

/**
 * 新定位事件。Netty 线程只负责发事件，WebSocket 广播在监听器里做，避免阻塞协议处理。
 */
public record VehiclePositionEvent(TrackPoint point) {
}
