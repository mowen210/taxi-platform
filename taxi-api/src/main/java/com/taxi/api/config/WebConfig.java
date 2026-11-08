package com.taxi.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置：跨域与接口文档元信息。
 * <p>跨域来源统一由 config/taxi.env 的 CORS_ALLOWED_ORIGINS 控制；
 * 生产环境由 Nginx 统一反向代理并收敛跨域。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public OpenAPI taxiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("出租汽车运营监管平台 API")
                .version("1.0.0-stage1")
                .description("统一前缀 /api/v1，JWT 置于 Header Authorization: Bearer xxx")
                .contact(new Contact().name("平台研发组")));
    }
}
