package com.taxi.infra.cache;

import com.taxi.common.constant.RedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 幂等键存储。用于电召建单等「重复提交必须返回同一结果」的写接口。
 * <p>值语义：{@code PENDING} 表示首个请求仍在处理，其余值即业务结果（如订单号）。</p>
 */
@Component
public class IdempotencyStore {

    private static final String PENDING = "PENDING";

    private final StringRedisTemplate redisTemplate;

    public IdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 抢占幂等键。
     *
     * @return true 表示首次请求，可以继续执行业务
     */
    public boolean tryAcquire(String scene, String key, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue()
                .setIfAbsent(RedisKeys.idempotent(scene, key), PENDING, ttl);
        return Boolean.TRUE.equals(ok);
    }

    /** 读取已缓存的结果；仍在处理中或不存在返回 null */
    public String findResult(String scene, String key) {
        String value = redisTemplate.opsForValue().get(RedisKeys.idempotent(scene, key));
        if (value == null || PENDING.equals(value)) {
            return null;
        }
        return value;
    }

    /** 写入业务结果，覆盖 PENDING */
    public void complete(String scene, String key, String result, Duration ttl) {
        redisTemplate.opsForValue().set(RedisKeys.idempotent(scene, key), result, ttl);
    }

    /** 业务失败时释放键，允许客户端重试 */
    public void release(String scene, String key) {
        redisTemplate.delete(RedisKeys.idempotent(scene, key));
    }
}
