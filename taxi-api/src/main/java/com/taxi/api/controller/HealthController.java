package com.taxi.api.controller;

import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口（免鉴权），供容器探针与 Nginx 上游检测使用。
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Operation(summary = "存活探针")
    @GetMapping
    public R<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("application", "taxi-platform");
        data.put("stage", "stage1-scaffold-rbac");
        data.put("time", LocalDateTime.now());
        return R.ok(data);
    }
}
