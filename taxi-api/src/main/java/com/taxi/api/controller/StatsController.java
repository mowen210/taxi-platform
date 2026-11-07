package com.taxi.api.controller;

import com.taxi.api.security.RequiresPerm;
import com.taxi.application.service.StatsAppService;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 运营统计与大屏数据源。
 */
@Tag(name = "统计报表")
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private final StatsAppService statsAppService;

    public StatsController(StatsAppService statsAppService) {
        this.statsAppService = statsAppService;
    }

    @Operation(summary = "总览指标")
    @GetMapping("/overview")
    @RequiresPerm("stats:view")
    public R<Map<String, Object>> overview() {
        return R.ok(statsAppService.overview());
    }

    @Operation(summary = "订单/营收趋势")
    @GetMapping("/trend")
    @RequiresPerm("stats:view")
    public R<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(statsAppService.trend(days));
    }

    @Operation(summary = "车队排行")
    @GetMapping("/fleets")
    @RequiresPerm("stats:view")
    public R<List<Map<String, Object>>> fleets(@RequestParam(defaultValue = "7") int days) {
        return R.ok(statsAppService.fleetRanking(days));
    }

    @Operation(summary = "报警类型分布")
    @GetMapping("/alarms")
    @RequiresPerm("stats:view")
    public R<List<Map<String, Object>>> alarms(@RequestParam(defaultValue = "7") int days) {
        return R.ok(statsAppService.alarmDistribution(days));
    }
}
