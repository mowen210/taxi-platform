package com.taxi.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 平台启动类。
 * <p>扫描根包 com.taxi，覆盖 common / domain / infra / application / api 五个模块。</p>
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.taxi")
public class TaxiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaxiApplication.class, args);
    }
}
