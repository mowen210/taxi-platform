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
 * 报警通道握手鉴权：令牌走 ?token=，校验 alarm:view 权限。
 */
@Component
public class AlarmHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AlarmHandshakeInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;

    public AlarmHandshakeInterceptor(JwtTokenProvider jwtTokenProvider, TokenStore tokenStore) {
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
            if (!permissions.contains("alarm:view")) {
                log.warn("报警 WebSocket 拒绝：用户 {} 无 alarm:view 权限", claims.getSubject());
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            Number fleetId = claims.get("fleetId", Number.class);
            attributes.put("uid", uid.longValue());
            attributes.put("fleetId", fleetId == null ? null : fleetId.longValue());
            attributes.put("username", claims.getSubject());
            return true;
        } catch (RuntimeException e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
