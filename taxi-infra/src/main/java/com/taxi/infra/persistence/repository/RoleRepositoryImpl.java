package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Role;
import com.taxi.domain.repository.RoleRepository;
import com.taxi.infra.persistence.mapper.RoleMapper;
import com.taxi.infra.persistence.mapper.RoleMenuMapper;
import com.taxi.infra.persistence.mapper.UserRoleMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 角色仓储实现。
 */
@Repository
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final UserRoleMapper userRoleMapper;

    public RoleRepositoryImpl(RoleMapper roleMapper, RoleMenuMapper roleMenuMapper, UserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public Optional<Role> findById(Long id) {
        return Optional.ofNullable(roleMapper.selectById(id));
    }

    @Override
    public List<Role> findAll() {
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery().orderByAsc(Role::getId));
    }

    @Override
    public PageResult<Role> search(String keyword, long page, long size) {
        LambdaQueryWrapper<Role> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(Role::getRoleCode, trimmed).or().like(Role::getRoleName, trimmed));
        }
        wrapper.orderByAsc(Role::getId);
        Page<Role> result = roleMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public boolean existsByCode(String roleCode, Long excludeId) {
        LambdaQueryWrapper<Role> wrapper = Wrappers.<Role>lambdaQuery().eq(Role::getRoleCode, roleCode);
        if (excludeId != null) {
            wrapper.ne(Role::getId, excludeId);
        }
        return roleMapper.selectCount(wrapper) > 0;
    }

    @Override
    public void save(Role role) {
        roleMapper.insert(role);
    }

    @Override
    public void update(Role role) {
        roleMapper.updateById(role);
    }

    @Override
    public void deleteById(Long id) {
        roleMapper.deleteById(id);
    }

    @Override
    public List<Long> findMenuIds(Long roleId) {
        return roleMenuMapper.selectMenuIds(roleId);
    }

    @Override
    public void replaceMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.deleteByRoleId(roleId);
        if (menuIds == null) {
            return;
        }
        for (Long menuId : menuIds) {
            if (menuId != null) {
                roleMenuMapper.insertOne(IdWorker.getId(), roleId, menuId, null);
            }
        }
    }

    @Override
    public long countUsers(Long roleId) {
        return userRoleMapper.countByRoleId(roleId);
    }
}
