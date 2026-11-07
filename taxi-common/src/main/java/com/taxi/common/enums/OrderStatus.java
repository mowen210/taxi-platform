package com.taxi.common.enums;

/**
 * 订单状态机状态集合。
 * <p><b>契约冻结：只允许以下 10 个状态，新增状态必须走变更评审</b>（见需求文档第七节）。
 * 状态流转规则由 {@code OrderStateMachine}（阶段 3 交付）实现，本枚举只提供定义与判定。</p>
 */
public enum OrderStatus {

    /** 已创建：电召订单落库初始态；巡游订单落库后同事务流转至 PICKUP */
    CREATED("已创建"),
    /** 已派单：电召订单已派给司机 */
    DISPATCHED("已派单"),
    /** 接到乘客（重车）：0x0B02 营运开始 */
    PICKUP("接到乘客重车"),
    /** 行程结束待计费：0x0B05 营运结束 */
    FINISHED("行程结束待计费"),
    /** 费用核算完成：计价引擎已生成费用明细 */
    CALCULATED("费用核算完成"),
    /** 待开票：已生成待开票记录 */
    INVOICE_PENDING("待开票"),
    /** 开票成功 */
    INVOICE_SUCCESS("开票成功"),
    /** 开票失败：可重试，超过上限转 ABNORMAL */
    INVOICE_FAIL("开票失败"),
    /** 已取消：仅限未载客阶段 */
    CANCELLED("已取消"),
    /** 异常订单：人工复核恢复或作废 */
    ABNORMAL("异常订单");

    private final String desc;

    OrderStatus(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    /** 是否终态（终态不允许再流转） */
    public boolean isFinalStatus() {
        return this == INVOICE_SUCCESS || this == CANCELLED;
    }

    /** 是否已载客（载客后禁止取消，只能转异常单人工处理） */
    public boolean isPassengerOnBoard() {
        return this == PICKUP || this == FINISHED || this == CALCULATED
                || this == INVOICE_PENDING || this == INVOICE_SUCCESS || this == INVOICE_FAIL;
    }

    public static OrderStatus of(String name) {
        if (name == null) {
            return null;
        }
        for (OrderStatus status : values()) {
            if (status.name().equalsIgnoreCase(name)) {
                return status;
            }
        }
        return null;
    }
}
