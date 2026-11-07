package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.ResetPasswordRequest;
import com.taxi.application.dto.UserSaveRequest;
import com.taxi.application.dto.UserView;
import com.taxi.application.service.UserAppService;
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
 * 用户管理接口（RBAC 用户侧）。
 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAppService userAppService;

    public UserController(UserAppService userAppService) {
        this.userAppService = userAppService;
    }

    @Operation(summary = "分页查询用户", description = "非全局数据权限账号自动限定本车队")
    @GetMapping
    @RequiresPerm("system:user:list")
    public R<PageResult<UserView>> page(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long fleetId,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size) {
        return R.ok(userAppService.page(keyword, fleetId, status, page, size));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    @RequiresPerm("system:user:list")
    public R<UserView> detail(@PathVariable Long id) {
        return R.ok(userAppService.detail(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @RequiresPerm("system:user:add")
    @OpLog(module = "系统管理", action = "新增用户", bizType = "USER")
    public R<Long> create(@Valid @RequestBody UserSaveRequest request) {
        return R.ok(userAppService.create(request));
    }

    @Operation(summary = "修改用户")
    @PutMapping("/{id}")
    @RequiresPerm("system:user:edit")
    @OpLog(module = "系统管理", action = "修改用户", bizType = "USER")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserSaveRequest request) {
        userAppService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除用户", description = "软删除；禁止删除自己与内置管理员")
    @DeleteMapping("/{id}")
    @RequiresPerm("system:user:delete")
    @OpLog(module = "系统管理", action = "删除用户", bizType = "USER")
    public R<Void> delete(@PathVariable Long id) {
        userAppService.delete(id);
        return R.ok();
    }

    @Operation(summary = "重置口令")
    @PutMapping("/{id}/password")
    @RequiresPerm("system:user:reset-pwd")
    @OpLog(module = "系统管理", action = "重置口令", bizType = "USER")
    public R<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userAppService.resetPassword(id, request.newPassword());
        return R.ok();
    }

    @Operation(summary = "分配角色")
    @PutMapping("/{id}/roles")
    @RequiresPerm("system:user:assign-role")
    @OpLog(module = "系统管理", action = "分配角色", bizType = "USER")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userAppService.assignRoles(id, roleIds);
        return R.ok();
    }
}
