package com.taxi.application.support;

import com.taxi.application.dto.MenuNode;
import com.taxi.domain.entity.Menu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单树构建单测。
 */
class MenuTreeBuilderTest {

    @Test
    @DisplayName("按父子关系与排序号构建两级树")
    void shouldBuildTwoLevelTree() {
        Menu root = menu(1L, 0L, "系统管理", 1);
        Menu userMenu = menu(101L, 1L, "用户管理", 2);
        Menu roleMenu = menu(102L, 1L, "角色管理", 1);

        List<MenuNode> tree = MenuTreeBuilder.build(List.of(root, userMenu, roleMenu));

        assertEquals(1, tree.size());
        assertEquals("系统管理", tree.get(0).menuName());
        assertEquals(2, tree.get(0).children().size());
        assertEquals("角色管理", tree.get(0).children().get(0).menuName());
        assertEquals("用户管理", tree.get(0).children().get(1).menuName());
    }

    @Test
    @DisplayName("父节点缺失时降级为根节点，不丢数据")
    void shouldTreatOrphanAsRoot() {
        Menu orphan = menu(200L, 999L, "孤儿菜单", 1);

        List<MenuNode> tree = MenuTreeBuilder.build(List.of(orphan));

        assertEquals(1, tree.size());
        assertEquals("孤儿菜单", tree.get(0).menuName());
    }

    @Test
    @DisplayName("空集合返回空树")
    void shouldReturnEmptyForEmptyInput() {
        assertTrue(MenuTreeBuilder.build(List.of()).isEmpty());
        assertTrue(MenuTreeBuilder.build(null).isEmpty());
    }

    private Menu menu(Long id, Long parentId, String name, int sortNo) {
        Menu menu = new Menu();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(2);
        menu.setSortNo(sortNo);
        menu.setVisible(1);
        return menu;
    }
}
