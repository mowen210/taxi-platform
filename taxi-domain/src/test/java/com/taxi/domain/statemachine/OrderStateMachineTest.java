package com.taxi.domain.statemachine;

import com.taxi.common.enums.OrderStatus;
import com.taxi.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单状态机 10 态流转用例。
 */
class OrderStateMachineTest {

    @Test
    @DisplayName("电召主流程：创建→派单→接客→结束→核算")
    void shouldWalkMainFlow() {
        OrderStatus s = OrderStatus.CREATED;
        s = OrderStateMachine.transit(s, OrderAction.DISPATCH);
        assertEquals(OrderStatus.DISPATCHED, s);
        s = OrderStateMachine.transit(s, OrderAction.PICKUP);
        assertEquals(OrderStatus.PICKUP, s);
        s = OrderStateMachine.transit(s, OrderAction.FINISH);
        assertEquals(OrderStatus.FINISHED, s);
        s = OrderStateMachine.transit(s, OrderAction.CALCULATE);
        assertEquals(OrderStatus.CALCULATED, s);
    }

    @Test
    @DisplayName("开票链路：核算→待开票→成功/失败→重试")
    void shouldWalkInvoiceFlow() {
        assertEquals(OrderStatus.INVOICE_PENDING,
                OrderStateMachine.transit(OrderStatus.CALCULATED, OrderAction.SUBMIT_INVOICE));
        assertEquals(OrderStatus.INVOICE_SUCCESS,
                OrderStateMachine.transit(OrderStatus.INVOICE_PENDING, OrderAction.INVOICE_SUCCESS));
        assertEquals(OrderStatus.INVOICE_FAIL,
                OrderStateMachine.transit(OrderStatus.INVOICE_PENDING, OrderAction.INVOICE_FAIL));
        assertEquals(OrderStatus.INVOICE_PENDING,
                OrderStateMachine.transit(OrderStatus.INVOICE_FAIL, OrderAction.SUBMIT_INVOICE));
    }

    @Test
    @DisplayName("未载客可取消，已载客只能转异常")
    void shouldCancelOnlyBeforePickup() {
        assertEquals(OrderStatus.CANCELLED,
                OrderStateMachine.transit(OrderStatus.CREATED, OrderAction.CANCEL));
        assertEquals(OrderStatus.CANCELLED,
                OrderStateMachine.transit(OrderStatus.DISPATCHED, OrderAction.CANCEL));

        BizException onBoard = assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.PICKUP, OrderAction.CANCEL));
        assertEquals(400, onBoard.getCode());
        assertTrue(onBoard.getMessage().contains("已载客"));

        assertEquals(OrderStatus.ABNORMAL,
                OrderStateMachine.transit(OrderStatus.PICKUP, OrderAction.MARK_ABNORMAL));
    }

    @Test
    @DisplayName("非法流转被拒绝并给出可读原因")
    void shouldRejectIllegalTransitions() {
        assertEquals(400, assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.CREATED, OrderAction.PICKUP)).getCode());
        assertEquals(400, assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.CREATED, OrderAction.FINISH)).getCode());
        assertEquals(400, assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.CALCULATED, OrderAction.DISPATCH)).getCode());
        assertEquals(400, assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.DISPATCHED, OrderAction.CALCULATE)).getCode());
    }

    @Test
    @DisplayName("终态不再流转")
    void shouldFreezeFinalStatus() {
        assertTrue(OrderStatus.INVOICE_SUCCESS.isFinalStatus());
        assertTrue(OrderStatus.CANCELLED.isFinalStatus());
        assertEquals(400, assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.CANCELLED, OrderAction.DISPATCH)).getCode());
        assertEquals(400, assertThrows(BizException.class,
                () -> OrderStateMachine.transit(OrderStatus.INVOICE_SUCCESS, OrderAction.MARK_ABNORMAL)).getCode());
    }

    @Test
    @DisplayName("撤回派单回到已创建")
    void shouldUndispatchBackToCreated() {
        assertEquals(OrderStatus.CREATED,
                OrderStateMachine.transit(OrderStatus.DISPATCHED, OrderAction.UNDISPATCH));
    }

    @Test
    @DisplayName("allowed() 供前端按钮置灰，不抛异常")
    void shouldReportAllowedActions() {
        assertTrue(OrderStateMachine.allowed(OrderStatus.CREATED, OrderAction.DISPATCH));
        assertTrue(OrderStateMachine.allowed(OrderStatus.CREATED, OrderAction.CANCEL));
        assertFalse(OrderStateMachine.allowed(OrderStatus.CREATED, OrderAction.FINISH));
        assertFalse(OrderStateMachine.allowed(OrderStatus.CANCELLED, OrderAction.DISPATCH));
        assertFalse(OrderStateMachine.allowed(null, OrderAction.DISPATCH));
    }
}
