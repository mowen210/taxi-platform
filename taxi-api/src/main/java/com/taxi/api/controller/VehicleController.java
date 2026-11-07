package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.VehicleSaveRequest;
import com.taxi.application.dto.VehicleStatusRequest;
import com.taxi.application.dto.VehicleView;
import com.taxi.application.service.VehicleAppService;
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
 * 车辆档案接口。
 */
@Tag(name = "车辆管理")
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private final VehicleAppService vehicleAppService;

    public VehicleController(VehicleAppService vehicleAppService) {
        this.vehicleAppService = vehicleAppService;
    }

    @Operation(summary = "分页查询车辆")
    @GetMapping
    @RequiresPerm("vehicle:list")
    public R<PageResult<VehicleView>> page(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long fleetId,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        return R.ok(vehicleAppService.page(keyword, fleetId, status, page, size));
    }

    @Operation(summary = "保险/年检临期车辆")
    @GetMapping("/expiring")
    @RequiresPerm("vehicle:list")
    public R<List<VehicleView>> expiring(@RequestParam(defaultValue = "30") int days) {
        return R.ok(vehicleAppService.expiring(days));
    }

    @Operation(summary = "车辆详情")
    @GetMapping("/{id}")
    @RequiresPerm("vehicle:list")
    public R<VehicleView> detail(@PathVariable Long id) {
        return R.ok(vehicleAppService.detail(id));
    }

    @Operation(summary = "新增车辆")
    @PostMapping
    @RequiresPerm("vehicle:add")
    @OpLog(module = "车辆管理", action = "新增车辆", bizType = "VEHICLE")
    public R<Long> create(@Valid @RequestBody VehicleSaveRequest request) {
        return R.ok(vehicleAppService.create(request));
    }

    @Operation(summary = "修改车辆")
    @PutMapping("/{id}")
    @RequiresPerm("vehicle:edit")
    @OpLog(module = "车辆管理", action = "修改车辆", bizType = "VEHICLE")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody VehicleSaveRequest request) {
        vehicleAppService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "变更车辆状态", description = "置为营运中前必须有生效的终端-计价器绑定")
    @PutMapping("/{id}/status")
    @RequiresPerm("vehicle:status")
    @OpLog(module = "车辆管理", action = "变更车辆状态", bizType = "VEHICLE")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody VehicleStatusRequest request) {
        vehicleAppService.updateStatus(id, request);
        return R.ok();
    }

    @Operation(summary = "删除车辆", description = "软删除；存在生效绑定时拒绝")
    @DeleteMapping("/{id}")
    @RequiresPerm("vehicle:delete")
    @OpLog(module = "车辆管理", action = "删除车辆", bizType = "VEHICLE")
    public R<Void> delete(@PathVariable Long id) {
        vehicleAppService.delete(id);
        return R.ok();
    }
}
