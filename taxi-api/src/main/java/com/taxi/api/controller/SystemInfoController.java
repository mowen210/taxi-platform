package com.taxi.api.controller;

import com.taxi.api.security.RequiresPerm;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理首页使用的系统信息与运行参数。
 * <p>只返回非敏感配置；JWT/AES/HMAC/数据库口令/Redis 口令一律不返回。</p>
 */
@Tag(name = "系统信息", description = "管理首页基础信息与运行参数")
@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {

    private final Environment environment;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${protocol.enabled:true}")
    private boolean protocolEnabled;

    @Value("${protocol.port:7611}")
    private int protocolPort;

    @Value("${track.provider:mysql}")
    private String trackProvider;

    @Value("${media.storage-path:./media}")
    private String mediaStoragePath;

    @Value("${media.hls-base:http://127.0.0.1:8888}")
    private String mediaHlsBase;

    @Value("${invoice.mock:true}")
    private boolean invoiceMock;

    @Value("${alarm.escalate.level1-seconds:600}")
    private long alarmLevel1Seconds;

    @Value("${alarm.escalate.level2-seconds:300}")
    private long alarmLevel2Seconds;

    @Value("${billing.default-driver-share:0.8000}")
    private String billingDefaultDriverShare;

    @Value("${cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String corsAllowedOrigins;

    public SystemInfoController(Environment environment) {
        this.environment = environment;
    }

    @Operation(summary = "基础信息与运行参数")
    @GetMapping("/info")
    @RequiresPerm("monitor:view")
    public R<Map<String, Object>> info() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", "出租汽车运营监管平台");
        data.put("version", "1.0.0");
        data.put("stage", "阶段 1~10");
        data.put("serverPort", serverPort);
        data.put("protocolEnabled", protocolEnabled);
        data.put("protocolPort", protocolPort);
        data.put("trackProvider", trackProvider);
        data.put("mediaStoragePath", mediaStoragePath);
        data.put("mediaHlsBase", mediaHlsBase);
        data.put("invoiceMock", invoiceMock);
        data.put("alarmLevel1Seconds", alarmLevel1Seconds);
        data.put("alarmLevel2Seconds", alarmLevel2Seconds);
        data.put("billingDefaultDriverShare", billingDefaultDriverShare);
        data.put("corsAllowedOrigins", corsAllowedOrigins);
        data.put("dbHost", environment.getProperty("DB_HOST", "127.0.0.1"));
        data.put("dbPort", environment.getProperty("DB_PORT", "3306"));
        data.put("dbName", environment.getProperty("DB_NAME", "taxi_platform"));
        data.put("redisHost", environment.getProperty("REDIS_HOST", "127.0.0.1"));
        data.put("redisPort", environment.getProperty("REDIS_PORT", "6379"));
        data.put("javaVersion", System.getProperty("java.version"));
        data.put("osName", System.getProperty("os.name"));
        data.put("startTime", LocalDateTime.ofInstant(
                Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()),
                ZoneId.systemDefault()));
        data.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        return R.ok(data);
    }
}
