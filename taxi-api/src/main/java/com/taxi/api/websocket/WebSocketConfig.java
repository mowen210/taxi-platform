package com.taxi.api.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 注册。监控通道独立于 REST，鉴权在握手拦截器完成。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MonitorWebSocketHandler monitorHandler;
    private final MonitorHandshakeInterceptor handshakeInterceptor;
    private final AlarmWebSocketHandler alarmHandler;
    private final AlarmHandshakeInterceptor alarmHandshakeInterceptor;

    public WebSocketConfig(MonitorWebSocketHandler monitorHandler,
                           MonitorHandshakeInterceptor handshakeInterceptor,
                           AlarmWebSocketHandler alarmHandler,
                           AlarmHandshakeInterceptor alarmHandshakeInterceptor) {
        this.monitorHandler = monitorHandler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.alarmHandler = alarmHandler;
        this.alarmHandshakeInterceptor = alarmHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(monitorHandler, "/ws/v1/monitor")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
        registry.addHandler(alarmHandler, "/ws/v1/alarm")
                .addInterceptors(alarmHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
