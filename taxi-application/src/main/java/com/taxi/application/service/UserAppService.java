package com.taxi.application.service;

import com.taxi.application.dto.ChangePasswordRequest;
import com.taxi.application.dto.UserSaveRequest;
import com.taxi.application.dto.UserView;
import com.taxi.application.support.UserAssembler;
import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.common.security.PasswordHasher;
import com.taxi.domain.entity.User;
import com.taxi.domain.repository.UserRepository;
import com.taxi.infra.cache.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户管理用例。
 * <p>数据权限：非全局范围账号只能看到本车队用户，且不允许把用户改到其他车队。</p>
 */
@Service
public class UserAppService {

    private static final Logger log = LoggerFactory.getLogger(UserAppService.class);
    private static final long BUILT_IN_ADMIN_ID = 1L;

    private final UserRepository userRepository;
    private final TokenStore tokenStore;

    public UserAppService(UserRepository userRepository, TokenStore tokenStore) {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
    }

    /** 分页查询，自动收敛数据权限 */
    public PageResult<UserView> page(String keyword, Long fleetId, Integer status, long page, long size) {
        long safePage = Math.max(1L, page);
        long safeSize = Math.min(Math.max(1L, size), 100L);
        LoginUser current = UserContext.require();
        Long effectiveFleet = fleetId;
        if (!current.isGlobalScope()) {
            if (current.fleetId() == null) {
                throw BizException.forbidden("当前账号未绑定车队，无法查看用户数据");
            }
            effectiveFleet = current.fleetId();
        }
        PageResult<User> result = userRepository.search(keyword, effectiveFleet, status, safePage, safeSize);
        List<UserView> views = result.list().stream().map(UserAssembler::toView).toList();
        return PageResult.of(result.total(), result.page(), result.size(), views);
    }

    public UserView detail(Long id) {
        return UserAssembler.toView(findOrThrow(id));
    }

    /** 新增用户并绑定角色 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserSaveRequest request) {
        if (userRepository.existsByUsername(request.username(), null)) {
            throw BizException.badRequest("用户名已存在");
        }
        if (!StringUtils.hasText(request.password())) {
            throw BizException.badRequest("新增用户必须设置初始口令");
        }
        checkFleetScope(request.fleetId());

        User user = new User();
        user.setUsername(request.username().trim());
        user.setPassword(PasswordHasher.encode(request.password()));
        user.setRealName(request.realName());
        user.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        user.setIdCard(StringUtils.hasText(request.idCard()) ? request.idCard().trim() : null);
        user.setEmail(request.email());
        user.setFleetId(request.fleetId());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setRemark(request.remark());
        userRepository.save(user);

        if (request.roleIds() != null && !request.roleIds().isEmpty()) {
            userRepository.replaceRoles(user.getId(), request.roleIds());
        }
        log.info("新增用户 uid={} username={}", user.getId(), user.getUsername());
        return user.getId();
    }

    /** 修改用户资料，未传的字段保持原值（口令不在此接口修改） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserSaveRequest request) {
        User existing = findOrThrow(id);
        if (!existing.getUsername().equals(request.username())
                && userRepository.existsByUsername(request.username(), id)) {
            throw BizException.badRequest("用户名已存在");
        }
        checkFleetScope(request.fleetId());

        User patch = new User();
        patch.setId(id);
        patch.setUsername(request.username().trim());
        patch.setRealName(request.realName());
        patch.setEmail(request.email());
        patch.setFleetId(request.fleetId());
        patch.setStatus(request.status());
        patch.setRemark(request.remark());
        if (StringUtils.hasText(request.phone())) {
            patch.setPhone(request.phone().trim());
        }
        if (StringUtils.hasText(request.idCard())) {
            patch.setIdCard(request.idCard().trim());
        }
        userRepository.update(patch);

        if (request.roleIds() != null) {
            userRepository.replaceRoles(id, request.roleIds());
            tokenStore.evictPermissions(id);
        }
        log.info("修改用户 uid={}", id);
    }

    /** 删除用户（软删除）：禁止删除自己与内置管理员 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        LoginUser current = UserContext.require();
        if (current.userId().equals(id)) {
            throw BizException.badRequest("不能删除当前登录账号");
        }
        if (BUILT_IN_ADMIN_ID == id) {
            throw BizException.forbidden("内置管理员账号不允许删除");
        }
        findOrThrow(id);
        userRepository.deleteById(id);
        userRepository.replaceRoles(id, List.of());
        tokenStore.evictPermissions(id);
        log.warn("删除用户 uid={} operator={}", id, current.username());
    }

    /** 管理员重置他人口令 */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        findOrThrow(id);
        userRepository.updatePassword(id, PasswordHasher.encode(newPassword));
        tokenStore.evictPermissions(id);
        log.warn("重置用户口令 uid={} operator={}", id, UserContext.currentUsername());
    }

    /** 调整角色后立即清空权限缓存，下次请求重新加载 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        findOrThrow(id);
        userRepository.replaceRoles(id, roleIds);
        tokenStore.evictPermissions(id);
        log.info("调整用户角色 uid={} roles={}", id, roleIds);
    }

    /** 当前登录用户自助修改口令：改完强制重新登录 */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        LoginUser current = UserContext.require();
        User user = findOrThrow(current.userId());
        if (!PasswordHasher.matches(request.oldPassword(), user.getPassword())) {
            throw BizException.badRequest("原口令不正确");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw BizException.badRequest("新口令不能与原口令相同");
        }
        userRepository.updatePassword(user.getId(), PasswordHasher.encode(request.newPassword()));
        tokenStore.evictPermissions(user.getId());
        if (current.jti() != null) {
            tokenStore.blacklist(current.jti(), java.time.Duration.ofHours(2));
        }
        log.info("用户修改口令 uid={}", user.getId());
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("用户不存在"));
    }

    /** 非全局账号只能把用户放到自己车队 */
    private void checkFleetScope(Long fleetId) {
        LoginUser current = UserContext.require();
        if (current.isGlobalScope()) {
            return;
        }
        if (fleetId == null || !fleetId.equals(current.fleetId())) {
            throw BizException.forbidden("只能在本车队范围内维护用户");
        }
    }
}
