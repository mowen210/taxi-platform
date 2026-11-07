package com.taxi.application.support;

import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;

/**
 * 数据权限守卫。
 * <p>规则与 RBAC 约定一致：data_scope=1 全局；=2 本车队；其余（3 本人）在车队维度按本车队处理，
 * 更细的「本人」收敛在具体业务用例里实现。</p>
 */
public final class ScopeGuard {

    private ScopeGuard() {
    }

    /** 查询时收敛车队过滤条件：非全局账号强制取自己的车队 */
    public static Long fleetFilter(Long requested) {
        LoginUser current = UserContext.require();
        if (current.isGlobalScope()) {
            return requested;
        }
        Long own = current.fleetId();
        if (own == null) {
            throw BizException.forbidden("当前账号未绑定车队，无法查看该数据");
        }
        if (requested != null && !requested.equals(own)) {
            throw BizException.forbidden("无权查看其他车队数据");
        }
        return own;
    }

    /** 写入时校验目标车队归属 */
    public static void requireFleetWritable(Long fleetId) {
        LoginUser current = UserContext.require();
        if (current.isGlobalScope()) {
            return;
        }
        if (fleetId == null || !fleetId.equals(current.fleetId())) {
            throw BizException.forbidden("无权操作其他车队数据");
        }
    }

    /** 读取单条数据时校验归属 */
    public static void requireFleetReadable(Long fleetId) {
        LoginUser current = UserContext.require();
        if (current.isGlobalScope() || current.fleetId() == null) {
            return;
        }
        if (fleetId == null || !fleetId.equals(current.fleetId())) {
            throw BizException.forbidden("无权查看其他车队数据");
        }
    }

    public static boolean isGlobal() {
        return UserContext.require().isGlobalScope();
    }
}
