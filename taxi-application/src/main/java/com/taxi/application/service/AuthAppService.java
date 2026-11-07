package com.taxi.application.service;

import com.taxi.application.dto.LoginRequest;
import com.taxi.application.dto.LoginResult;
import com.taxi.application.dto.UserProfile;
import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.crypto.MaskUtil;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.ResultCode;
import com.taxi.common.security.JwtTokenProvider;
import com.taxi.common.security.PasswordHasher;
import com.taxi.domain.entity.User;
import com.taxi.domain.repository.UserRepository;
import com.taxi.infra.cache.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

/**
 * 认证用例：登录、注销、当前用户档案。
 * <p>口径：口令错误与用户不存在返回同一提示，避免用户名枚举；连续失败按用户名计数并临时锁定。</p>
 */
@Service
public class AuthAppService {

    private static final Logger log = LoggerFactory.getLogger(AuthAppService.class);

    private final UserRepository userRepository;
    private final TokenStore tokenStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final int maxLoginFail;
    private final int lockMinutes;

    public AuthAppService(UserRepository userRepository,
                          TokenStore tokenStore,
                          JwtTokenProvider jwtTokenProvider,
                          @Value("${security.login.max-fail:5}") int maxLoginFail,
                          @Value("${security.login.lock-minutes:15}") int lockMinutes) {
        this.userRepository = userRepository;
        this.tokenStore = tokenStore;
        this.jwtTokenProvider = jwtTokenProvider;
        this.maxLoginFail = maxLoginFail;
        this.lockMinutes = lockMinutes;
    }

    /**
     * 登录。
     *
     * @param request  用户名与口令
     * @param clientIp 客户端 IP，仅用于日志审计
     */
    public LoginResult login(LoginRequest request, String clientIp) {
        String username = request.username().trim();
        long failCount = tokenStore.getLoginFail(username);
        if (failCount >= maxLoginFail) {
            log.warn("登录被锁定 username={} ip={} failCount={}", username, clientIp, failCount);
            throw new BizException(ResultCode.TOO_MANY_REQUESTS,
                    "登录失败次数过多，请 " + lockMinutes + " 分钟后重试");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> BizException.unauthorized("用户名或口令错误"));
        if (!PasswordHasher.matches(request.password(), user.getPassword())) {
            long current = tokenStore.increaseLoginFail(username, Duration.ofMinutes(lockMinutes));
            log.warn("登录失败 username={} ip={} failCount={}", username, clientIp, current);
            throw BizException.unauthorized("用户名或口令错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw BizException.forbidden("账号已停用，请联系管理员");
        }
        tokenStore.clearLoginFail(username);

        List<Long> roleIds = userRepository.findRoleIds(user.getId());
        List<String> permissions = userRepository.findPermissionCodes(user.getId());
        Integer dataScope = userRepository.findMaxDataScope(user.getId());

        LoginUser principal = new LoginUser(user.getId(), user.getUsername(), user.getRealName(),
                user.getFleetId(), dataScope, new HashSet<>(permissions), null);
        JwtTokenProvider.JwtIssue issue = jwtTokenProvider.issue(principal);
        tokenStore.cachePermissions(user.getId(), permissions, Duration.ofSeconds(jwtTokenProvider.getTtlSeconds()));

        LocalDateTime loginTime = LocalDateTime.now();
        userRepository.updateLastLogin(user.getId(), loginTime);
        log.info("登录成功 username={} uid={} ip={}", username, user.getId(), clientIp);

        UserProfile profile = new UserProfile(user.getId(), user.getUsername(), user.getRealName(),
                MaskUtil.maskPhone(user.getPhone()), user.getEmail(), user.getFleetId(), user.getStatus(),
                dataScope, roleIds, permissions, loginTime);
        return new LoginResult(issue.token(), "Bearer", jwtTokenProvider.getTtlSeconds(), profile);
    }

    /** 注销：把当前令牌的 jti 加入黑名单，权限缓存保留（同一用户其他会话仍可用） */
    public void logout() {
        LoginUser current = UserContext.get();
        if (current == null || current.jti() == null) {
            return;
        }
        tokenStore.blacklist(current.jti(), Duration.ofSeconds(jwtTokenProvider.getTtlSeconds()));
        log.info("用户注销 uid={} jti={}", current.userId(), current.jti());
    }

    /** 当前登录用户档案，权限码实时从库读取，避免角色调整后前端权限不刷新 */
    public UserProfile currentProfile() {
        LoginUser current = UserContext.require();
        User user = userRepository.findById(current.userId())
                .orElseThrow(() -> BizException.unauthorized("用户不存在或已被删除"));
        List<Long> roleIds = userRepository.findRoleIds(user.getId());
        List<String> permissions = userRepository.findPermissionCodes(user.getId());
        Integer dataScope = userRepository.findMaxDataScope(user.getId());
        return new UserProfile(user.getId(), user.getUsername(), user.getRealName(),
                MaskUtil.maskPhone(user.getPhone()), user.getEmail(), user.getFleetId(), user.getStatus(),
                dataScope, roleIds, permissions, user.getLastLoginTime());
    }
}
