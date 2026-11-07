package com.taxi.domain.statemachine;

import com.taxi.common.enums.OrderStatus;
import com.taxi.common.exception.BizException;

/**
 * 订单状态机（阶段 3 核心）。
 *
 * <pre>
 * CREATED ──派单──> DISPATCHED ──接到乘客──> PICKUP ──行程结束──> FINISHED ──核算──> CALCULATED
 *    │                  │                                            │                 │
 *    └──取消──> CANCELLED └──取消──> CANCELLED（未载客）               └──转异常──> ABNORMAL ──核算──> CALCULATED
 *                                                                                       └──取消──> CANCELLED
 * CALCULATED ──提交开票──> INVOICE_PENDING ──成功──> INVOICE_SUCCESS（终态）
 *                                        └──失败──> INVOICE_FAIL ──重试──> INVOICE_PENDING
 * </pre>
 *
 * 规则：终态（INVOICE_SUCCESS / CANCELLED）不再流转；已载客订单只能转异常，不能取消。
 */
public final class OrderStateMachine {

    private OrderStateMachine() {
    }

    /**
     * 计算下一状态，非法流转直接抛业务异常（400）。
     *
     * @param current 当前状态
     * @param action  动作
     * @return 目标状态
     */
    public static OrderStatus transit(OrderStatus current, OrderAction action) {
        if (current == null) {
            throw BizException.badRequest("订单状态缺失，无法流转");
        }
        if (current.isFinalStatus()) {
            throw BizException.badRequest("订单已处于终态「" + current.getDesc() + "」，不允许再" + action.getLabel());
        }
        return switch (action) {
            case DISPATCH -> {
                if (current == OrderStatus.CREATED || current == OrderStatus.ABNORMAL) {
                    yield OrderStatus.DISPATCHED;
                }
                throw illegal(current, action, "仅「已创建」或「异常订单」可派单");
            }
            case UNDISPATCH -> {
                if (current == OrderStatus.DISPATCHED) {
                    yield OrderStatus.CREATED;
                }
                throw illegal(current, action, "仅「已派单」可撤回");
            }
            case PICKUP -> {
                if (current == OrderStatus.DISPATCHED) {
                    yield OrderStatus.PICKUP;
                }
                throw illegal(current, action, "仅「已派单」可确认接到乘客");
            }
            case FINISH -> {
                if (current == OrderStatus.PICKUP) {
                    yield OrderStatus.FINISHED;
                }
                throw illegal(current, action, "仅「接到乘客」可结束行程");
            }
            case CALCULATE -> {
                if (current == OrderStatus.FINISHED || current == OrderStatus.ABNORMAL) {
                    yield OrderStatus.CALCULATED;
                }
                throw illegal(current, action, "仅「行程结束」或「异常订单」可核算费用");
            }
            case SUBMIT_INVOICE -> {
                if (current == OrderStatus.CALCULATED || current == OrderStatus.INVOICE_FAIL) {
                    yield OrderStatus.INVOICE_PENDING;
                }
                throw illegal(current, action, "仅「费用核算完成」或「开票失败」可提交开票");
            }
            case INVOICE_SUCCESS -> {
                if (current == OrderStatus.INVOICE_PENDING) {
                    yield OrderStatus.INVOICE_SUCCESS;
                }
                throw illegal(current, action, "仅「待开票」可标记开票成功");
            }
            case INVOICE_FAIL -> {
                if (current == OrderStatus.INVOICE_PENDING) {
                    yield OrderStatus.INVOICE_FAIL;
                }
                throw illegal(current, action, "仅「待开票」可标记开票失败");
            }
            case CANCEL -> {
                if (current.isPassengerOnBoard()) {
                    throw BizException.badRequest("订单已载客，不能取消；请转异常订单由人工复核");
                }
                if (current == OrderStatus.CREATED || current == OrderStatus.DISPATCHED
                        || current == OrderStatus.ABNORMAL) {
                    yield OrderStatus.CANCELLED;
                }
                throw illegal(current, action, "当前状态不允许取消");
            }
            case MARK_ABNORMAL -> {
                if (current == OrderStatus.ABNORMAL) {
                    throw BizException.badRequest("订单已是异常状态");
                }
                yield OrderStatus.ABNORMAL;
            }
        };
    }

    /** 是否允许该流转，用于前端按钮置灰，不做异常抛出 */
    public static boolean allowed(OrderStatus current, OrderAction action) {
        try {
            transit(current, action);
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    private static BizException illegal(OrderStatus current, OrderAction action, String hint) {
        return BizException.badRequest("订单当前状态「" + current.getDesc() + "」不允许"
                + action.getLabel() + "：" + hint);
    }
}
