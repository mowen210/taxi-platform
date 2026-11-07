package com.taxi.infra.cache;

import com.taxi.common.constant.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

/**
 * 令牌与缓存操作门面。
 * <p>职责：权限缓存、令牌黑名单、登录失败计数、接口限流计数。
 * 所有键集中由 {@link RedisKeys} 生成，禁止散落字面量拼接。</p>
 */
@Component
public class TokenStore {

    private final StringRedisTemplate redisTemplate;

    public TokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 缓存用户权限码，TTL 与令牌一致，改权后即时生效靠主动清理 */
    public void cachePermissions(Long userId, Collection<String> permissions, Duration ttl) {
        String key = RedisKeys.authPerm(userId);
        redisTemplate.delete(key);
        if (permissions != null && !permissions.isEmpty()) {
            redisTemplate.opsForSet().add(key, permissions.toArray(String[]::new));
            redisTemplate.expire(key, ttl);
        }
    }

    public Set<String> findPermissions(Long userId) {
        Set<String> members = redisTemplate.opsForSet().members(RedisKeys.authPerm(userId));
        return members == null ? Set.of() : members;
    }

    /** 终端是否在线（会话存在）。兼容 808 头 12 位与档案 10 位号码写法 */
    public boolean hasSession(String terminalId) {
        if (terminalId == null || terminalId.isBlank()) {
            return false;
        }
        if (Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.terminalSession(terminalId)))) {
            return true;
        }
        String numeric = terminalId.replaceFirst("^0+", "");
        return !numeric.isEmpty()
                && Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.terminalSession(numeric)));
    }

    public void evictPermissions(Long userId) {
        redisTemplate.delete(RedisKeys.authPerm(userId));
    }

    /** 注销令牌：按 jti 加入黑名单，TTL 取令牌剩余有效期 */
    public void blacklist(String jti, Duration ttl) {
        redisTemplate.opsForValue().set(RedisKeys.blacklist(jti), "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.blacklist(jti)));
    }

    /** 登录失败计数，返回当前失败次数 */
    public long increaseLoginFail(String username, Duration window) {
        String key = RedisKeys.loginFail(username);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count == null ? 0L : count;
    }

    public long getLoginFail(String username) {
        String value = redisTemplate.opsForValue().get(RedisKeys.loginFail(username));
        return value == null ? 0L : Long.parseLong(value);
    }

    public void clearLoginFail(String username) {
        redisTemplate.delete(RedisKeys.loginFail(username));
    }

    /**
     * 固定窗口限流计数。
     *
     * @return 当前窗口内计数，首次自增时设置窗口过期
     */
    public long incrementRate(String key, Duration window) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, window);
        }
        return count == null ? 0L : count;
    }
}
