package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.FleetSaveRequest;
import com.taxi.application.dto.FleetView;
import com.taxi.application.service.FleetAppService;
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
 * 车队管理接口。
 */
@Tag(name = "车队管理")
@RestController
@RequestMapping("/api/v1/fleets")
public class FleetController {

    private final FleetAppService fleetAppService;

    public FleetController(FleetAppService fleetAppService) {
        this.fleetAppService = fleetAppService;
    }

    @Operation(summary = "分页查询车队")
    @GetMapping
    @RequiresPerm("fleet:list")
    public R<PageResult<FleetView>> page(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long size) {
        return R.ok(fleetAppService.page(keyword, status, page, size));
    }

    @Operation(summary = "车队下拉选项", description = "仅返回启用状态车队的编码与名称")
    @GetMapping("/options")
    public R<List<FleetView>> options() {
        return R.ok(fleetAppService.options());
    }

    @Operation(summary = "车队详情")
    @GetMapping("/{id}")
    @RequiresPerm("fleet:list")
    public R<FleetView> detail(@PathVariable Long id) {
        return R.ok(fleetAppService.detail(id));
    }

    @Operation(summary = "新增车队")
    @PostMapping
    @RequiresPerm("fleet:add")
    @OpLog(module = "车队管理", action = "新增车队", bizType = "FLEET")
    public R<Long> create(@Valid @RequestBody FleetSaveRequest request) {
        return R.ok(fleetAppService.create(request));
    }

    @Operation(summary = "修改车队")
    @PutMapping("/{id}")
    @RequiresPerm("fleet:edit")
    @OpLog(module = "车队管理", action = "修改车队", bizType = "FLEET")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody FleetSaveRequest request) {
        fleetAppService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除车队", description = "软删除；车队下有车辆或司机时拒绝")
    @DeleteMapping("/{id}")
    @RequiresPerm("fleet:delete")
    @OpLog(module = "车队管理", action = "删除车队", bizType = "FLEET")
    public R<Void> delete(@PathVariable Long id) {
        fleetAppService.delete(id);
        return R.ok();
    }
}
