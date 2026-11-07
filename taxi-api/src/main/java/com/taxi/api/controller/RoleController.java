package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.RoleSaveRequest;
import com.taxi.application.service.RoleAppService;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import com.taxi.domain.entity.Role;
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
 * 角色管理接口。
 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleAppService roleAppService;

    public RoleController(RoleAppService roleAppService) {
        this.roleAppService = roleAppService;
    }

    @Operation(summary = "分页查询角色")
    @GetMapping
    @RequiresPerm("system:role:list")
    public R<PageResult<Role>> page(@RequestParam(required = false) String keyword,
                                    @RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "20") long size) {
        return R.ok(roleAppService.page(keyword, page, size));
    }

    @Operation(summary = "全部角色", description = "用于用户分配角色下拉")
    @GetMapping("/all")
    @RequiresPerm("system:role:list")
    public R<List<Role>> all() {
        return R.ok(roleAppService.list());
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @RequiresPerm("system:role:add")
    @OpLog(module = "系统管理", action = "新增角色", bizType = "ROLE")
    public R<Long> create(@Valid @RequestBody RoleSaveRequest request) {
        return R.ok(roleAppService.create(request));
    }

    @Operation(summary = "修改角色")
    @PutMapping("/{id}")
    @RequiresPerm("system:role:edit")
    @OpLog(module = "系统管理", action = "修改角色", bizType = "ROLE")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RoleSaveRequest request) {
        roleAppService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除角色", description = "仍有用户绑定时拒绝删除")
    @DeleteMapping("/{id}")
    @RequiresPerm("system:role:delete")
    @OpLog(module = "系统管理", action = "删除角色", bizType = "ROLE")
    public R<Void> delete(@PathVariable Long id) {
        roleAppService.delete(id);
        return R.ok();
    }

    @Operation(summary = "查询角色已分配菜单")
    @GetMapping("/{id}/menus")
    @RequiresPerm("system:role:list")
    public R<List<Long>> menuIds(@PathVariable Long id) {
        return R.ok(roleAppService.menuIds(id));
    }

    @Operation(summary = "分配菜单权限")
    @PutMapping("/{id}/menus")
    @RequiresPerm("system:role:assign-menu")
    @OpLog(module = "系统管理", action = "分配菜单", bizType = "ROLE")
    public R<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleAppService.assignMenus(id, menuIds);
        return R.ok();
    }
}
