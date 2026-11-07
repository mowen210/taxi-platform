package com.taxi.api.ratelimit;

import com.taxi.common.constant.RedisKeys;
import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.ResultCode;
import com.taxi.infra.cache.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

/**
 * 限流切面：固定窗口计数。
 * <p>注意固定窗口在窗口边界可能瞬时翻倍，本场景（登录防爆破、导出防刷）可接受；
 * 高频协议类限流在接入层用令牌桶另行实现。</p>
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final TokenStore tokenStore;

    public RateLimitAspect(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Before("@annotation(rateLimit)")
    public void check(JoinPoint joinPoint, RateLimit rateLimit) {
        String scene = rateLimit.name().isBlank()
                ? joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName()
                : rateLimit.name();
        String identity = resolveIdentity();
        String key = RedisKeys.rateLimit(scene, identity);
        long count = tokenStore.incrementRate(key, Duration.ofSeconds(rateLimit.windowSeconds()));
        if (count > rateLimit.limit()) {
            log.warn("触发限流 scene={} identity={} count={} limit={}", scene, identity, count, rateLimit.limit());
            throw new BizException(ResultCode.TOO_MANY_REQUESTS,
                    "请求过于频繁，请 " + rateLimit.windowSeconds() + " 秒后重试");
        }
    }

    private String resolveIdentity() {
        LoginUser user = UserContext.get();
        if (user != null && user.userId() != null) {
            return "u" + user.userId();
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return "ip" + forwarded.split(",")[0].trim();
        }
        return "ip" + request.getRemoteAddr();
    }
}
