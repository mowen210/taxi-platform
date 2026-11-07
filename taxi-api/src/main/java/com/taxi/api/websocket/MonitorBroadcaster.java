package com.taxi.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.application.dto.VehiclePositionView;
import com.taxi.application.event.VehiclePositionEvent;
import com.taxi.domain.track.TrackPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 把定位事件转成 WebSocket 增量消息。
 * <p>注意：当前在协议线程同步广播，终端规模上来后应改为独立线程池/消息队列。</p>
 */
@Component
public class MonitorBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(MonitorBroadcaster.class);

    private final MonitorWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public MonitorBroadcaster(MonitorWebSocketHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onVehiclePosition(VehiclePositionEvent event) {
        if (handler.onlineSessions() == 0) {
            return;
        }
        TrackPoint p = event.point();
        VehiclePositionView view = new VehiclePositionView(p.vehicleId(), p.plateNo(), p.terminalId(),
                1, p.lng(), p.lat(), p.speed(), p.direction(), p.ts(), p.alarmFlag());
        try {
            handler.broadcast("{\"type\":\"position\",\"data\":"
                    + objectMapper.writeValueAsString(view) + "}");
        } catch (Exception e) {
            log.warn("位置增量序列化失败 vehicleId={} 原因={}", p.vehicleId(), e.getMessage());
        }
    }
}
