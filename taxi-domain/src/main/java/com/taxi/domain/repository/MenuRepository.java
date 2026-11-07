package com.taxi.domain.repository;

import com.taxi.domain.entity.Menu;

import java.util.List;
import java.util.Optional;

/**
 * 菜单/权限仓储。
 */
public interface MenuRepository {

    /** 全量按 parent_id、sort_no 升序 */
    List<Menu> findAllOrdered();

    Optional<Menu> findById(Long id);

    /** 按角色集合查询被授权的菜单（去重、排序） */
    List<Menu> findByRoleIds(List<Long> roleIds);

    void save(Menu menu);

    void update(Menu menu);

    void deleteById(Long id);

    /** 统计子节点数，删除前校验 */
    long countChildren(Long id);

    /** 统计权限码被引用次数（用于提示） */
    long countByPermCode(String permCode, Long excludeId);
}
