package com.taxi.application.dto;

import java.time.LocalDateTime;

/**
 * 绑定记录视图。
 */
public record BindingView(
        Long id,
        Long vehicleId,
        String plateNo,
        String terminalId,
        String taximeterSn,
        Integer bindStatus,
        Integer bindSource,
        LocalDateTime bindTime,
        LocalDateTime unbindTime,
        Long operatorId,
        String operatorName,
        String remark) {
}
