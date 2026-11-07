package com.taxi.application.support;

import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 数据权限收敛规则用例。
 */
class ScopeGuardTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void login(Long fleetId, int dataScope) {
        UserContext.set(new LoginUser(1L, "tester", "测试", fleetId, dataScope, Set.of("vehicle:list"), "jti"));
    }

    @Test
    @DisplayName("全局账号保留请求的车队过滤条件")
    void shouldKeepRequestedFleetForGlobalUser() {
        login(null, 1);
        assertEquals(99L, ScopeGuard.fleetFilter(99L));
        assertNull(ScopeGuard.fleetFilter(null));
    }

    @Test
    @DisplayName("车队账号强制收敛到自己车队")
    void shouldForceOwnFleetForFleetUser() {
        login(7L, 2);
        assertEquals(7L, ScopeGuard.fleetFilter(null));
        assertEquals(7L, ScopeGuard.fleetFilter(7L));
        BizException e = assertThrows(BizException.class, () -> ScopeGuard.fleetFilter(8L));
        assertEquals(403, e.getCode());
    }

    @Test
    @DisplayName("车队账号不能写入其他车队数据")
    void shouldRejectWritableOtherFleet() {
        login(7L, 2);
        ScopeGuard.requireFleetWritable(7L);
        BizException e = assertThrows(BizException.class, () -> ScopeGuard.requireFleetWritable(8L));
        assertEquals(403, e.getCode());
        assertEquals(403, assertThrows(BizException.class, () -> ScopeGuard.requireFleetWritable(null)).getCode());
    }

    @Test
    @DisplayName("未绑定车队的账号不能查看数据")
    void shouldRejectFleetUserWithoutFleet() {
        login(null, 3);
        assertEquals(403, assertThrows(BizException.class, () -> ScopeGuard.fleetFilter(null)).getCode());
    }
}
