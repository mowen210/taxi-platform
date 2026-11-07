package com.taxi.api.websocket;

import com.taxi.application.service.TrackAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监控 WebSocket：建连下发全量快照，之后每个 0x0200 推送一条增量。
 *
 * <p>消息格式：</p>
 * <pre>
 * {"type":"snapshot","data":[{vehicleId,plateNo,terminalId,onlineStatus,lng,lat,speed,direction,ts,alarmFlag}]}
 * {"type":"position","data":{...同上...}}
 * </pre>
 */
@Component
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MonitorWebSocketHandler.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final TrackAppService trackAppService;

    public MonitorWebSocketHandler(TrackAppService trackAppService) {
        this.trackAppService = trackAppService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        Long fleetId = fleetId(session);
        String snapshot = "{\"type\":\"snapshot\",\"data\":" + trackAppService.latestJson(fleetId) + "}";
        send(session, snapshot);
        log.info("监控 WebSocket 建立 user={} 在线={}", session.getAttributes().get("username"), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("ping".equalsIgnoreCase(message.getPayload().trim())) {
            send(session, "{\"type\":\"pong\"}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("监控 WebSocket 断开 user={} 在线={} 原因={}",
                session.getAttributes().get("username"), sessions.size(), status);
    }

    /** 广播一条消息；单个会话异常不影响其它会话 */
    public void broadcast(String json) {
        for (WebSocketSession session : sessions) {
            send(session, json);
        }
    }

    public int onlineSessions() {
        return sessions.size();
    }

    private void send(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            sessions.remove(session);
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.warn("监控消息发送失败 user={} 原因={}", session.getAttributes().get("username"), e.getMessage());
            sessions.remove(session);
        }
    }

    private Long fleetId(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        Object value = attributes.get("fleetId");
        return value instanceof Number number ? number.longValue() : null;
    }
}
