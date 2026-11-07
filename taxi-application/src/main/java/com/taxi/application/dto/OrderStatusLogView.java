package com.taxi.application.dto;

import java.time.LocalDateTime;

/**
 * 订单状态流转记录（时间线节点）。
 */
public record OrderStatusLogView(
        Long id,
        String fromStatus,
        String fromStatusDesc,
        String toStatus,
        String toStatusDesc,
        String action,
        Long operatorId,
        String operatorName,
        Integer source,
        String remark,
        LocalDateTime createTime) {
}
