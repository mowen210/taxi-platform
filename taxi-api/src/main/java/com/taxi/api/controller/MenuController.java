package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.MenuNode;
import com.taxi.application.dto.MenuSaveRequest;
import com.taxi.application.service.MenuAppService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单与权限接口。
 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuAppService menuAppService;

    public MenuController(MenuAppService menuAppService) {
        this.menuAppService = menuAppService;
    }

    @Operation(summary = "全量菜单树", description = "菜单管理页使用")
    @GetMapping("/tree")
    @RequiresPerm("system:menu:list")
    public R<List<MenuNode>> tree() {
        return R.ok(menuAppService.tree());
    }

    @Operation(summary = "当前用户菜单树", description = "前端动态路由，只需登录")
    @GetMapping("/mine")
    @RequiresPerm
    public R<List<MenuNode>> mine() {
        return R.ok(menuAppService.myMenus());
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    @RequiresPerm("system:menu:add")
    @OpLog(module = "系统管理", action = "新增菜单", bizType = "MENU")
    public R<Long> create(@Valid @RequestBody MenuSaveRequest request) {
        return R.ok(menuAppService.create(request));
    }

    @Operation(summary = "修改菜单")
    @PutMapping("/{id}")
    @RequiresPerm("system:menu:edit")
    @OpLog(module = "系统管理", action = "修改菜单", bizType = "MENU")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody MenuSaveRequest request) {
        menuAppService.update(id, request);
        return R.ok();
    }

    @Operation(summary = "删除菜单", description = "存在子节点时拒绝删除")
    @DeleteMapping("/{id}")
    @RequiresPerm("system:menu:delete")
    @OpLog(module = "系统管理", action = "删除菜单", bizType = "MENU")
    public R<Void> delete(@PathVariable Long id) {
        menuAppService.delete(id);
        return R.ok();
    }
}
