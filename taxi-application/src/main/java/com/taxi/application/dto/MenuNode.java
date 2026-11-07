package com.taxi.application.dto;

import java.util.List;

/**
 * 菜单树节点（前端 Element Plus 树结构）。
 */
public record MenuNode(
        Long id,
        Long parentId,
        Integer menuType,
        String menuName,
        String path,
        String component,
        String permCode,
        String icon,
        Integer sortNo,
        Integer visible,
        List<MenuNode> children) {
}
