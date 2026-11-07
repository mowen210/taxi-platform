package com.taxi.application.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 日志版短信通道：未接入厂商通道时使用，只打印不真实发送。
 * <p>启用真实通道后通过 {@code sms.provider} 切换实现，本类自动失效。</p>
 */
@Component
@ConditionalOnProperty(prefix = "sms", name = "provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsSender.class);

    @Override
    public boolean send(String mobile, String content) {
        // 手机号脱敏后再落日志，避免日志成为敏感数据泄漏面
        log.info("[SMS-MOCK] mobile={} content={}", mask(mobile), content);
        return true;
    }

    private String mask(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return "***";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
