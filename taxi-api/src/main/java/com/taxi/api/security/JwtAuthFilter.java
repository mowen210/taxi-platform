package com.taxi.api.security;

import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.result.R;
import com.taxi.common.result.ResultCode;
import com.taxi.common.security.JwtTokenProvider;
import com.taxi.common.util.JsonUtil;
import com.taxi.infra.cache.TokenStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * JWT 认证过滤器。
 * <p>校验顺序：Header 格式 -> 签名与有效期 -> jti 黑名单 -> 权限缓存。
 * 任一环节失败直接返回 401 统一响应体，不进入控制器。</p>
 */
@Component
@Order(10)
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenStore tokenStore;

    public JwtAuthFilter(JwtTokenProvider jwtTokenProvider, TokenStore tokenStore) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenStore = tokenStore;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/v1/auth/login")
                || uri.startsWith("/api/v1/health")
                || uri.startsWith("/ws/")
                || uri.endsWith(".html")
                || uri.startsWith("/monitor")
                || uri.equals("/")
                || uri.equals("/login")
                || uri.equals("/login/")
                || uri.equals("/admin")
                || uri.equals("/admin/")
                || uri.equals("/base")
                || uri.equals("/base/")
                || uri.startsWith("/base.css")
                || uri.startsWith("/base.js")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            writeUnauthorized(response, "缺少访问令牌");
            return;
        }
        String token = header.substring(PREFIX.length()).trim();
        try {
            Claims claims = jwtTokenProvider.parse(token);
            String jti = claims.getId();
            if (jti != null && tokenStore.isBlacklisted(jti)) {
                writeUnauthorized(response, "令牌已注销，请重新登录");
                return;
            }
            Number uid = claims.get("uid", Number.class);
            if (uid == null) {
                writeUnauthorized(response, "令牌缺少用户标识");
                return;
            }
            Set<String> permissions = tokenStore.findPermissions(uid.longValue());
            Number fleetId = claims.get("fleetId", Number.class);
            Number scope = claims.get("scope", Number.class);
            LoginUser user = new LoginUser(
                    uid.longValue(),
                    claims.getSubject(),
                    claims.get("realName", String.class),
                    fleetId == null ? null : fleetId.longValue(),
                    scope == null ? null : scope.intValue(),
                    permissions,
                    jti);
            UserContext.set(user);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("令牌校验失败 uri={} reason={}", request.getRequestURI(), e.getMessage());
            writeUnauthorized(response, "令牌无效或已过期");
        } finally {
            UserContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        R<Void> body = R.fail(ResultCode.UNAUTHORIZED, message);
        response.getWriter().write(JsonUtil.toJson(body));
    }
}
