package com.taxi.api.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解（固定窗口计数，Redis 实现）。
 * <p>按「登录用户 ID」或「客户端 IP」维度计数，窗口内超过阈值返回 429。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimit {

    /** 限流场景名，用于生成 Redis 键 */
    String name() default "";

    /** 窗口内允许的最大次数 */
    int limit() default 30;

    /** 窗口长度（秒） */
    int windowSeconds() default 60;
}
