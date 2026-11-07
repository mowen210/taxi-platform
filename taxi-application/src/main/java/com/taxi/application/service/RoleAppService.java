package com.taxi.application.service;

import com.taxi.application.dto.RoleSaveRequest;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Role;
import com.taxi.domain.repository.RoleRepository;
import com.taxi.domain.repository.UserRepository;
import com.taxi.infra.cache.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理用例。
 * <p>约束：内置超级管理员角色不可删除；仍有用户绑定的角色不可删除；调整菜单后立即失效相关用户权限缓存。</p>
 */
@Service
public class RoleAppService {

    private static final Logger log = LoggerFactory.getLogger(RoleAppService.class);
    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TokenStore tokenStore;

    public RoleAppService(RoleRepository roleRepository, UserRepository userRepository, TokenStore tokenStore) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
    }

    public PageResult<Role> page(String keyword, long page, long size) {
        return roleRepository.search(keyword, Math.max(1L, page), Math.min(Math.max(1L, size), 100L));
    }

    public List<Role> list() {
        return roleRepository.findAll();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(RoleSaveRequest request) {
        if (roleRepository.existsByCode(request.roleCode(), null)) {
            throw BizException.badRequest("角色编码已存在");
        }
        Role role = new Role();
        role.setRoleCode(request.roleCode().trim());
        role.setRoleName(request.roleName().trim());
        role.setDataScope(request.dataScope() == null ? 3 : request.dataScope());
        role.setRemark(request.remark());
        roleRepository.save(role);
        log.info("新增角色 roleId={} code={}", role.getId(), role.getRoleCode());
        return role.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RoleSaveRequest request) {
        Role existing = findOrThrow(id);
        if (!existing.getRoleCode().equals(request.roleCode())
                && roleRepository.existsByCode(request.roleCode(), id)) {
            throw BizException.badRequest("角色编码已存在");
        }
        if (SUPER_ADMIN_CODE.equals(existing.getRoleCode())
                && !SUPER_ADMIN_CODE.equals(request.roleCode())) {
            throw BizException.forbidden("内置超级管理员角色编码不允许修改");
        }
        Role patch = new Role();
        patch.setId(id);
        patch.setRoleCode(request.roleCode().trim());
        patch.setRoleName(request.roleName().trim());
        patch.setDataScope(request.dataScope());
        patch.setRemark(request.remark());
        roleRepository.update(patch);
        evictRoleUsers(id);
        log.info("修改角色 roleId={}", id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Role existing = findOrThrow(id);
        if (SUPER_ADMIN_CODE.equals(existing.getRoleCode())) {
            throw BizException.forbidden("内置超级管理员角色不允许删除");
        }
        long userCount = roleRepository.countUsers(id);
        if (userCount > 0) {
            throw BizException.badRequest("该角色下仍有 " + userCount + " 个用户，无法删除");
        }
        roleRepository.deleteById(id);
        log.warn("删除角色 roleId={} operator={}", id, UserContext.currentUsername());
    }

    public List<Long> menuIds(Long roleId) {
        findOrThrow(roleId);
        return roleRepository.findMenuIds(roleId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        Role role = findOrThrow(roleId);
        roleRepository.replaceMenus(roleId, menuIds);
        evictRoleUsers(roleId);
        log.info("分配角色菜单 roleId={} menuCount={}", roleId, menuIds == null ? 0 : menuIds.size());
        if (SUPER_ADMIN_CODE.equals(role.getRoleCode()) && (menuIds == null || menuIds.isEmpty())) {
            log.warn("超级管理员角色被清空菜单，将导致管理端无可用功能");
        }
    }

    private Role findOrThrow(Long id) {
        return roleRepository.findById(id).orElseThrow(() -> BizException.notFound("角色不存在"));
    }

    private void evictRoleUsers(Long roleId) {
        userRepository.findUserIdsByRole(roleId).forEach(tokenStore::evictPermissions);
    }
}
