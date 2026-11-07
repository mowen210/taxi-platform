package com.taxi.application.support;

import com.taxi.application.dto.MenuNode;
import com.taxi.domain.entity.Menu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 菜单树构建（纯函数，可脱网单测）。
 * <p>规则：parent_id=0 或父节点不存在的节点视为根；同一父级按 sort_no 升序；最大深度 10 层，防止脏数据造成死循环。</p>
 */
public final class MenuTreeBuilder {

    private static final int MAX_DEPTH = 10;

    private MenuTreeBuilder() {
    }

    public static List<MenuNode> build(List<Menu> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Menu>> childrenMap = new LinkedHashMap<>();
        Map<Long, Menu> all = new LinkedHashMap<>();
        for (Menu menu : menus) {
            all.put(menu.getId(), menu);
            childrenMap.computeIfAbsent(menu.getParentId(), key -> new ArrayList<>()).add(menu);
        }
        childrenMap.values().forEach(list -> list.sort((a, b) -> Integer.compare(nullSafe(a.getSortNo()), nullSafe(b.getSortNo()))));

        List<Menu> roots = new ArrayList<>();
        for (Menu menu : menus) {
            Long parentId = menu.getParentId();
            if (parentId == null || parentId == 0L || !all.containsKey(parentId)) {
                roots.add(menu);
            }
        }
        roots.sort((a, b) -> Integer.compare(nullSafe(a.getSortNo()), nullSafe(b.getSortNo())));
        return roots.stream().map(menu -> toNode(menu, childrenMap, 1)).toList();
    }

    private static MenuNode toNode(Menu menu, Map<Long, List<Menu>> childrenMap, int depth) {
        List<MenuNode> children = List.of();
        if (depth < MAX_DEPTH) {
            List<Menu> childMenus = childrenMap.get(menu.getId());
            if (childMenus != null && !childMenus.isEmpty()) {
                children = childMenus.stream().map(child -> toNode(child, childrenMap, depth + 1)).toList();
            }
        }
        return new MenuNode(
                menu.getId(),
                menu.getParentId(),
                menu.getMenuType(),
                menu.getMenuName(),
                menu.getPath(),
                menu.getComponent(),
                menu.getPermCode(),
                menu.getIcon(),
                menu.getSortNo(),
                menu.getVisible(),
                children);
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }
}
