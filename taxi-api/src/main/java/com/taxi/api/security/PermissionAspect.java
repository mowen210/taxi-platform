package com.taxi.api.security;

import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 权限校验切面（RBAC 按钮级权限）。
 * <p>权限码来源：角色 -> 菜单 perm_code，登录时缓存到 Redis，角色调整后即时失效。</p>
 */
@Aspect
@Component
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    @Before("@annotation(requiresPerm)")
    public void check(JoinPoint joinPoint, RequiresPerm requiresPerm) {
        LoginUser user = UserContext.get();
        if (user == null) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        String[] required = requiresPerm.value();
        if (required.length == 0) {
            return;
        }
        for (String perm : required) {
            if (!user.hasPermission(perm)) {
                log.warn("权限校验失败 uid={} required={} owned={}", user.userId(),
                        Arrays.toString(required), user.permissions().size());
                throw BizException.forbidden("缺少权限：" + perm);
            }
        }
    }
}
