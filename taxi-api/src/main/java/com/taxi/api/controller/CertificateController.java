package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.CertificateView;
import com.taxi.application.dto.WarnScanResult;
import com.taxi.application.service.CertificateWarnService;
import com.taxi.application.service.DriverAppService;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 证件到期预警接口。
 * <p>定时任务每日 08:00 自动跑一次；本接口用于人工补跑和联调验收。</p>
 */
@Tag(name = "证件预警")
@RestController
@RequestMapping("/api/v1/certificates")
public class CertificateController {

    private final CertificateWarnService warnService;
    private final DriverAppService driverAppService;

    public CertificateController(CertificateWarnService warnService, DriverAppService driverAppService) {
        this.warnService = warnService;
        this.driverAppService = driverAppService;
    }

    @Operation(summary = "临期/过期证件列表")
    @GetMapping("/expiring")
    @RequiresPerm("driver:cert:list")
    public R<List<CertificateView>> expiring(@RequestParam(defaultValue = "30") int days) {
        return R.ok(driverAppService.expiring(days));
    }

    @Operation(summary = "立即执行到期扫描", description = "刷新预警状态、生成站内通知、按配置发送短信")
    @PostMapping("/warn-scan")
    @RequiresPerm("cert:warn:scan")
    @OpLog(module = "证件预警", action = "执行到期扫描", bizType = "CERT_WARN")
    public R<WarnScanResult> scan() {
        return R.ok(warnService.scan());
    }
}
