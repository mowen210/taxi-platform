package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.BindRequest;
import com.taxi.application.dto.BindingView;
import com.taxi.application.dto.TerminalSaveRequest;
import com.taxi.application.dto.TerminalView;
import com.taxi.application.service.TerminalAppService;
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
 * 终端档案与设备绑定接口。
 */
@Tag(name = "终端与设备绑定")
@RestController
@RequestMapping("/api/v1/terminals")
public class TerminalController {

    private final TerminalAppService terminalAppService;

    public TerminalController(TerminalAppService terminalAppService) {
        this.terminalAppService = terminalAppService;
    }

    @Operation(summary = "分页查询终端")
    @GetMapping
    @RequiresPerm("terminal:list")
    public R<PageResult<TerminalView>> page(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer onlineStatus,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "20") long size) {
        return R.ok(terminalAppService.page(keyword, onlineStatus, page, size));
    }

    @Operation(summary = "终端详情")
    @GetMapping("/{id}")
    @RequiresPerm("terminal:list")
    public R<TerminalView> detail(@PathVariable Long id) {
        return R.ok(terminalAppService.detail(id));
    }

    @Operation(summary = "新增终端")
    @PostMapping
    @RequiresPerm("terminal:add")
    @OpLog(module = "终端管理", action = "新增终端", bizType = "TERMINAL")
    public R<Long> create(@Valid @RequestBody TerminalSaveRequest request) {
        return R.ok(terminalAppService.create(request));
    }

    @Operation(summary = "修改终端")
    @PutMapping("/{id}")
    @RequiresPerm("terminal:edit")
    @OpLog(module = "终端管理", action = "修改终端", bizType = "TERMINAL")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody TerminalSaveRequest request) {
        terminalAppService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除终端", description = "存在生效绑定时拒绝")
    @DeleteMapping("/{id}")
    @RequiresPerm("terminal:delete")
    @OpLog(module = "终端管理", action = "删除终端", bizType = "TERMINAL")
    public R<Void> delete(@PathVariable Long id) {
        terminalAppService.delete(id);
        return R.ok();
    }

    @Operation(summary = "车辆-终端-计价器绑定")
    @PostMapping("/bindings")
    @RequiresPerm("terminal:bind")
    @OpLog(module = "终端管理", action = "设备绑定", bizType = "DEVICE_BINDING")
    public R<BindingView> bind(@Valid @RequestBody BindRequest request) {
        return R.ok(terminalAppService.bind(request));
    }

    @Operation(summary = "按车辆解绑")
    @DeleteMapping("/bindings/{vehicleId}")
    @RequiresPerm("terminal:bind")
    @OpLog(module = "终端管理", action = "设备解绑", bizType = "DEVICE_BINDING")
    public R<Void> unbind(@PathVariable Long vehicleId,
                          @RequestParam(required = false) String remark) {
        terminalAppService.unbind(vehicleId, remark);
        return R.ok();
    }

    @Operation(summary = "分页查询绑定记录")
    @GetMapping("/bindings")
    @RequiresPerm("terminal:list")
    public R<PageResult<BindingView>> bindings(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer bindStatus,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size) {
        return R.ok(terminalAppService.bindings(keyword, bindStatus, page, size));
    }

    @Operation(summary = "车辆绑定历史")
    @GetMapping("/bindings/vehicle/{vehicleId}")
    @RequiresPerm("terminal:list")
    public R<List<BindingView>> history(@PathVariable Long vehicleId) {
        return R.ok(terminalAppService.history(vehicleId));
    }
}
