package com.taxi.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时加载唯一配置文件 config/taxi.env。
 * <p>查找顺序：TAXI_CONFIG_FILE / -Dtaxi.config.file → ./config/taxi.env
 * → /app/config/taxi.env。文件不存在时静默跳过，保留 application.yml 默认值与系统环境变量。</p>
 * <p>配置文件优先级高于 application.yml，但低于已存在的系统环境变量和 -D 参数。</p>
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String CONFIG_FILE_NAME = "taxi.env";
    public static final String CONFIG_FILE_PROPERTY = "TAXI_CONFIG_FILE";

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path path = resolve(environment);
        if (path == null) {
            log.debug("未找到统一配置文件 {}，使用 application.yml 默认值/系统环境变量", CONFIG_FILE_NAME);
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            Map<String, String> parsed = DotenvParser.parse(lines);
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : parsed.entrySet()) {
                String key = entry.getKey();
                if (environment.getSystemEnvironment().containsKey(key)
                        || environment.getSystemProperties().containsKey(key)) {
                    continue;
                }
                values.put(key, entry.getValue());
            }
            if (!values.isEmpty()) {
                environment.getPropertySources().addFirst(
                        new MapPropertySource("taxiConfigFile:" + path.toAbsolutePath(), values));
            }
            log.info("已加载统一配置 {}，注入 {} 项", path.toAbsolutePath(), values.size());
            System.out.println("[taxi-config] loaded " + path.toAbsolutePath() + " (" + values.size() + " keys)");
        } catch (IOException e) {
            throw new IllegalStateException("读取统一配置文件失败: " + path.toAbsolutePath(), e);
        }
    }

    private Path resolve(ConfigurableEnvironment environment) {
        String explicit = environment.getProperty(CONFIG_FILE_PROPERTY);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getProperty("taxi.config.file");
        }
        if (explicit != null && !explicit.isBlank()) {
            Path path = Path.of(explicit.trim());
            return Files.isRegularFile(path) ? path : null;
        }
        List<Path> candidates = List.of(
                Path.of("config", CONFIG_FILE_NAME),
                Path.of("/app/config", CONFIG_FILE_NAME));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
