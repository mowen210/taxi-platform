package com.taxi.common.context;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

/**
 * 登录态载体（随 JWT 解析结果放入 {@link UserContext}）。
 *
 * @param userId      用户主键
 * @param username    登录名
 * @param realName    姓名
 * @param fleetId     所属车队，用于数据权限过滤；为空表示全局
 * @param dataScope   数据权限范围：1=全部 2=本车队 3=本人
 * @param permissions 权限码集合，例如 system:user:list
 * @param jti         令牌唯一标识，用于注销黑名单
 */
public record LoginUser(
        Long userId,
        String username,
        String realName,
        Long fleetId,
        Integer dataScope,
        Set<String> permissions,
        String jti) implements Serializable {

    public LoginUser {
        permissions = permissions == null ? Collections.emptySet() : Set.copyOf(permissions);
    }

    /** 是否全局数据权限 */
    public boolean isGlobalScope() {
        return dataScope != null && dataScope == 1;
    }

    /** 是否具备指定权限码 */
    public boolean hasPermission(String perm) {
        return permissions.contains(perm);
    }
}
