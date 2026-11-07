package com.taxi.domain.statemachine;

/**
 * 订单状态机动作。动作与状态解耦，接口、808 报文、定时任务统一走动作驱动。
 */
public enum OrderAction {

    /** 电召派单 */
    DISPATCH("派单"),
    /** 撤单回退（改派前先撤回） */
    UNDISPATCH("撤回派单"),
    /** 接到乘客（808 0x0B02 或人工确认） */
    PICKUP("接到乘客"),
    /** 行程结束（808 0x0B05） */
    FINISH("行程结束"),
    /** 费用核算完成 */
    CALCULATE("费用核算"),
    /** 提交开票（阶段 7 调用） */
    SUBMIT_INVOICE("提交开票"),
    /** 开票成功（阶段 7 调用） */
    INVOICE_SUCCESS("开票成功"),
    /** 开票失败（阶段 7 调用） */
    INVOICE_FAIL("开票失败"),
    /** 取消订单 */
    CANCEL("取消订单"),
    /** 转异常订单 */
    MARK_ABNORMAL("转异常");

    private final String label;

    OrderAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
