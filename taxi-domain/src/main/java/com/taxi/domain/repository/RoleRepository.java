package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Role;

import java.util.List;
import java.util.Optional;

/**
 * 角色仓储。
 */
public interface RoleRepository {

    Optional<Role> findById(Long id);

    List<Role> findAll();

    PageResult<Role> search(String keyword, long page, long size);

    boolean existsByCode(String roleCode, Long excludeId);

    void save(Role role);

    void update(Role role);

    void deleteById(Long id);

    List<Long> findMenuIds(Long roleId);

    void replaceMenus(Long roleId, List<Long> menuIds);

    long countUsers(Long roleId);
}
