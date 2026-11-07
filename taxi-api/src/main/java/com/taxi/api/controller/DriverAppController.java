package com.taxi.api.controller;

import com.taxi.api.security.RequiresPerm;
import com.taxi.application.service.DriverPortalAppService;
import com.taxi.common.context.UserContext;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 司机端接口：本人档案、我的行程、接到乘客。
 */
@Tag(name = "司机端")
@RestController
@RequestMapping("/api/v1/driver-app")
public class DriverAppController {

    private final DriverPortalAppService driverAppService;

    public DriverAppController(DriverPortalAppService driverAppService) {
        this.driverAppService = driverAppService;
    }

    @Operation(summary = "当前司机档案")
    @GetMapping("/me")
    @RequiresPerm("driver:app")
    public R<DriverPortalAppService.DriverView> me() {
        return R.ok(driverAppService.me(UserContext.require().userId()));
    }

    @Operation(summary = "我的行程")
    @GetMapping("/orders")
    @RequiresPerm("driver:app")
    public R<PageResult<Map<String, Object>>> orders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(driverAppService.orders(UserContext.require().userId(), status, page, size));
    }

    @Operation(summary = "接到乘客")
    @PostMapping("/orders/{id}/pickup")
    @RequiresPerm("driver:app")
    public R<Void> pickup(@PathVariable Long id) {
        driverAppService.pickup(UserContext.require().userId(), id);
        return R.ok();
    }
}
