package com.taxi.application.config;

import com.taxi.common.crypto.AesGcmCipher;
import com.taxi.common.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全相关 Bean：JWT 签发器与敏感字段密钥初始化。
 * <p>密钥只从环境变量/配置中心注入，禁止写入代码仓库；启动时校验长度，失败即拒绝启动。</p>
 */
@Configuration
public class SecurityBeans {

    private static final Logger log = LoggerFactory.getLogger(SecurityBeans.class);

    @Bean
    public JwtTokenProvider jwtTokenProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:taxi-platform}") String issuer,
            @Value("${security.jwt.ttl-seconds:7200}") long ttlSeconds) {
        return new JwtTokenProvider(secret, issuer, ttlSeconds);
    }

    /**
     * 启动即初始化 AES 密钥，避免首个加密请求才暴露配置错误。
     */
    @Bean
    public ApplicationRunner cryptoKeyInitializer(
            @Value("${security.aes-key}") String aesKey,
            @Value("${security.hmac-secret}") String hmacSecret) {
        return args -> {
            AesGcmCipher.init(aesKey);
            if (hmacSecret == null || hmacSecret.isBlank()) {
                throw new IllegalStateException("security.hmac-secret 未配置");
            }
            log.info("敏感字段加密组件初始化完成，AES-256/GCM 就绪");
        };
    }
}
