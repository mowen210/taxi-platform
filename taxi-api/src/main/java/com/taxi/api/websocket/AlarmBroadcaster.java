package com.taxi.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.application.event.AlarmEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 报警事件转 WebSocket 消息。
 */
@Component
public class AlarmBroadcaster {

    private final AlarmWebSocketHandler handler;
    private final ObjectMapper objectMapper;

    public AlarmBroadcaster(AlarmWebSocketHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onAlarm(AlarmEvent event) {
        if (handler.onlineSessions() == 0) {
            return;
        }
        try {
            handler.broadcast("{\"type\":\"alarm\",\"escalated\":" + event.escalated()
                    + ",\"data\":" + objectMapper.writeValueAsString(event.alarm()) + "}");
        } catch (Exception ignored) {
            // 序列化失败只影响推送，不影响报警落库
        }
    }
}
