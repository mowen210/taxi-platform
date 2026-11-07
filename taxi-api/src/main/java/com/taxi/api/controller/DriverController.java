package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.CertificateSaveRequest;
import com.taxi.application.dto.CertificateView;
import com.taxi.application.dto.DriverSaveRequest;
import com.taxi.application.dto.DriverView;
import com.taxi.application.service.DriverAppService;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 司机档案与证件接口。
 */
@Tag(name = "司机管理")
@RestController
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private final DriverAppService driverAppService;

    public DriverController(DriverAppService driverAppService) {
        this.driverAppService = driverAppService;
    }

    @Operation(summary = "分页查询司机")
    @GetMapping
    @RequiresPerm("driver:list")
    public R<PageResult<DriverView>> page(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long fleetId,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "20") long size) {
        return R.ok(driverAppService.page(keyword, fleetId, status, page, size));
    }

    @Operation(summary = "司机详情")
    @GetMapping("/{id}")
    @RequiresPerm("driver:list")
    public R<DriverView> detail(@PathVariable Long id) {
        return R.ok(driverAppService.detail(id));
    }

    @Operation(summary = "新增司机")
    @PostMapping
    @RequiresPerm("driver:add")
    @OpLog(module = "司机管理", action = "新增司机", bizType = "DRIVER")
    public R<Long> create(@Valid @RequestBody DriverSaveRequest request) {
        return R.ok(driverAppService.create(request));
    }

    @Operation(summary = "修改司机")
    @PutMapping("/{id}")
    @RequiresPerm("driver:edit")
    @OpLog(module = "司机管理", action = "修改司机", bizType = "DRIVER")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DriverSaveRequest request) {
        driverAppService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除司机", description = "软删除；仍绑定车辆时拒绝")
    @DeleteMapping("/{id}")
    @RequiresPerm("driver:delete")
    @OpLog(module = "司机管理", action = "删除司机", bizType = "DRIVER")
    public R<Void> delete(@PathVariable Long id) {
        driverAppService.delete(id);
        return R.ok();
    }

    @Operation(summary = "司机证件列表")
    @GetMapping("/{id}/certificates")
    @RequiresPerm("driver:cert:list")
    public R<List<CertificateView>> certificates(@PathVariable Long id) {
        return R.ok(driverAppService.certificates(id));
    }

    @Operation(summary = "新增司机证件")
    @PostMapping("/{id}/certificates")
    @RequiresPerm("driver:cert:add")
    @OpLog(module = "司机管理", action = "新增证件", bizType = "DRIVER_CERT")
    public R<Long> addCertificate(@PathVariable Long id, @Valid @RequestBody CertificateSaveRequest request) {
        return R.ok(driverAppService.addCertificate(id, request));
    }

    @Operation(summary = "修改司机证件")
    @PutMapping("/{id}/certificates/{certId}")
    @RequiresPerm("driver:cert:edit")
    @OpLog(module = "司机管理", action = "修改证件", bizType = "DRIVER_CERT")
    public R<Void> updateCertificate(@PathVariable Long id,
                                     @PathVariable Long certId,
                                     @Valid @RequestBody CertificateSaveRequest request) {
        driverAppService.updateCertificate(id, certId, request);
        return R.ok();
    }

    @Operation(summary = "删除司机证件")
    @DeleteMapping("/{id}/certificates/{certId}")
    @RequiresPerm("driver:cert:delete")
    @OpLog(module = "司机管理", action = "删除证件", bizType = "DRIVER_CERT")
    public R<Void> deleteCertificate(@PathVariable Long id, @PathVariable Long certId) {
        driverAppService.deleteCertificate(id, certId);
        return R.ok();
    }
}
