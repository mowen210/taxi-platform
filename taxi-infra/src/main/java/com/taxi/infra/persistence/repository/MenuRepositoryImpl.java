package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taxi.domain.entity.Menu;
import com.taxi.domain.repository.MenuRepository;
import com.taxi.infra.persistence.mapper.MenuMapper;
import com.taxi.infra.persistence.mapper.RoleMenuMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 菜单仓储实现。
 */
@Repository
public class MenuRepositoryImpl implements MenuRepository {

    private final MenuMapper menuMapper;
    private final RoleMenuMapper roleMenuMapper;

    public MenuRepositoryImpl(MenuMapper menuMapper, RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    @Override
    public List<Menu> findAllOrdered() {
        return menuMapper.selectList(Wrappers.<Menu>lambdaQuery()
                .orderByAsc(Menu::getParentId)
                .orderByAsc(Menu::getSortNo));
    }

    @Override
    public Optional<Menu> findById(Long id) {
        return Optional.ofNullable(menuMapper.selectById(id));
    }

    @Override
    public List<Menu> findByRoleIds(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Menu> merged = new LinkedHashMap<>();
        for (Long roleId : roleIds) {
            List<Long> menuIds = roleMenuMapper.selectMenuIds(roleId);
            if (menuIds.isEmpty()) {
                continue;
            }
            List<Menu> menus = menuMapper.selectBatchIds(menuIds);
            menus.forEach(menu -> merged.putIfAbsent(menu.getId(), menu));
        }
        List<Menu> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> {
            int parentCompare = Long.compare(a.getParentId(), b.getParentId());
            return parentCompare != 0 ? parentCompare : Integer.compare(a.getSortNo(), b.getSortNo());
        });
        return result;
    }

    @Override
    public void save(Menu menu) {
        menuMapper.insert(menu);
    }

    @Override
    public void update(Menu menu) {
        menuMapper.updateById(menu);
    }

    @Override
    public void deleteById(Long id) {
        menuMapper.deleteById(id);
    }

    @Override
    public long countChildren(Long id) {
        return menuMapper.selectCount(Wrappers.<Menu>lambdaQuery().eq(Menu::getParentId, id));
    }

    @Override
    public long countByPermCode(String permCode, Long excludeId) {
        if (permCode == null || permCode.isBlank()) {
            return 0;
        }
        LambdaQueryWrapper<Menu> wrapper = Wrappers.<Menu>lambdaQuery().eq(Menu::getPermCode, permCode);
        if (excludeId != null) {
            wrapper.ne(Menu::getId, excludeId);
        }
        return menuMapper.selectCount(wrapper);
    }
}
