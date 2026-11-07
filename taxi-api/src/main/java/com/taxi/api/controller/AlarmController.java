package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.AlarmHandleRequest;
import com.taxi.application.dto.AlarmView;
import com.taxi.application.dto.LiveView;
import com.taxi.application.dto.MediaView;
import com.taxi.application.service.AlarmAppService;
import com.taxi.application.service.MediaAppService;
import com.taxi.common.context.UserContext;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报警中心：查询、三级处置、联动抓拍与 1078 实时预览。
 */
@Tag(name = "报警中心")
@RestController
@RequestMapping("/api/v1/alarms")
public class AlarmController {

    private final AlarmAppService alarmAppService;
    private final MediaAppService mediaAppService;

    public AlarmController(AlarmAppService alarmAppService, MediaAppService mediaAppService) {
        this.alarmAppService = alarmAppService;
        this.mediaAppService = mediaAppService;
    }

    @Operation(summary = "分页查询报警")
    @GetMapping
    @RequiresPerm("alarm:view")
    public R<PageResult<AlarmView>> page(
            @RequestParam(required = false) String alarmType,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Integer handleStatus,
            @RequestParam(required = false) Long fleetId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(alarmAppService.page(alarmType, level, handleStatus, fleetId, start, end, page, size));
    }

    @Operation(summary = "报警详情")
    @GetMapping("/{id}")
    @RequiresPerm("alarm:view")
    public R<AlarmView> detail(@PathVariable Long id) {
        return R.ok(alarmAppService.detail(id));
    }

    @Operation(summary = "报警处置", description = "action=ACK 受理 / HANDLE 处置完成 / IGNORE 忽略；后两者必须填写说明")
    @PostMapping("/{id}/handle")
    @RequiresPerm("alarm:handle")
    @OpLog(module = "报警中心", action = "报警处置", bizType = "ALARM")
    public R<AlarmView> handle(@PathVariable Long id, @Valid @RequestBody AlarmHandleRequest request) {
        return R.ok(alarmAppService.handle(id, request, UserContext.require().userId()));
    }

    @Operation(summary = "报警关联媒体")
    @GetMapping("/{id}/media")
    @RequiresPerm("media:view")
    public R<List<MediaView>> media(@PathVariable Long id) {
        return R.ok(mediaAppService.listByAlarm(id));
    }

    @Operation(summary = "联动抓拍（0x8801）")
    @PostMapping("/{id}/snapshot")
    @RequiresPerm("media:live")
    @OpLog(module = "报警中心", action = "联动抓拍", bizType = "ALARM")
    public R<Boolean> snapshot(@PathVariable Long id,
                               @RequestParam(defaultValue = "1") int channel) {
        return R.ok(alarmAppService.snapshot(id, channel));
    }

    @Operation(summary = "请求实时音视频（0x9101），返回 HLS 地址")
    @PostMapping("/{id}/live")
    @RequiresPerm("media:live")
    @OpLog(module = "报警中心", action = "实时预览", bizType = "ALARM")
    public R<LiveView> live(@PathVariable Long id,
                            @RequestParam(defaultValue = "1") int channel,
                            @RequestParam(defaultValue = "1") int streamType) {
        return R.ok(alarmAppService.live(id, channel, streamType));
    }

    @Operation(summary = "停止实时音视频（0x9102）")
    @PostMapping("/{id}/live/stop")
    @RequiresPerm("media:live")
    public R<Void> stopLive(@PathVariable Long id, @RequestParam(defaultValue = "1") int channel) {
        alarmAppService.stopLive(id, channel);
        return R.ok();
    }
}
