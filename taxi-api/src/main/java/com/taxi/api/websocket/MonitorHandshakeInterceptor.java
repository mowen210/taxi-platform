package com.taxi.api.websocket;

import com.taxi.common.security.JwtTokenProvider;
import com.taxi.infra.cache.TokenStore;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Set;

/**
 * WebSocket 握手鉴权：浏览器无法带 Authorization 头，令牌走查询参数 {@code ?token=...}。
 * <p>校验顺序：签名/有效期 → jti 黑名单 → 权限码 monitor:view。任一失败直接 401/403，不建立连接。</p>
 */
@Component
public class MonitorHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(MonitorHandshakeInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;

    public MonitorHandshakeInterceptor(JwtTokenProvider jwtTokenProvider, TokenStore tokenStore) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = jwtTokenProvider.parse(token.trim());
            String jti = claims.getId();
            if (jti != null && tokenStore.isBlacklisted(jti)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            Number uid = claims.get("uid", Number.class);
            if (uid == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            Set<String> permissions = tokenStore.findPermissions(uid.longValue());
            if (!permissions.contains("monitor:view")) {
                log.warn("WebSocket 拒绝：用户 {} 无 monitor:view 权限", claims.getSubject());
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            Number fleetId = claims.get("fleetId", Number.class);
            attributes.put("uid", uid.longValue());
            attributes.put("fleetId", fleetId == null ? null : fleetId.longValue());
            attributes.put("username", claims.getSubject());
            return true;
        } catch (RuntimeException e) {
            log.warn("WebSocket 握手令牌无效：{}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
