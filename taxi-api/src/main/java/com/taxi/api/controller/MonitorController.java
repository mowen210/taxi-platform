package com.taxi.api.controller;

import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.TrackView;
import com.taxi.application.dto.VehiclePositionView;
import com.taxi.application.service.TrackAppService;
import com.taxi.application.support.ScopeGuard;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实时监控与轨迹回放接口。
 * <p>WebSocket 通道：{@code /ws/v1/monitor?token=...}，建连下发快照，之后推送增量。</p>
 */
@Tag(name = "实时监控")
@RestController
@RequestMapping("/api/v1/monitor")
public class MonitorController {

    private final TrackAppService trackAppService;

    public MonitorController(TrackAppService trackAppService) {
        this.trackAppService = trackAppService;
    }

    @Operation(summary = "监控车辆列表（含最新位置与在线状态）")
    @GetMapping("/vehicles")
    @RequiresPerm("monitor:view")
    public R<List<VehiclePositionView>> vehicles() {
        return R.ok(trackAppService.latestPositions(ScopeGuard.fleetFilter(null)));
    }

    @Operation(summary = "轨迹回放：点位与里程/最高速度/时长汇总")
    @GetMapping("/track")
    @RequiresPerm("track:view")
    public R<TrackView> track(
            @RequestParam Long vehicleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Integer limit) {
        return R.ok(trackAppService.track(vehicleId, start, end, limit));
    }
}
