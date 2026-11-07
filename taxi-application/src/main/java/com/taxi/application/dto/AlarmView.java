package com.taxi.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报警列表/详情视图。类型、等级、处置状态均附中文描述，前端无需内置字典。
 */
public record AlarmView(
        Long id,
        String alarmNo,
        String alarmType,
        String alarmTypeDesc,
        int alarmLevel,
        String alarmLevelDesc,
        int source,
        String alarmFlagHex,
        String bizAlarmId,
        Long vehicleId,
        String plateNo,
        Long fleetId,
        Long driverId,
        String terminalId,
        Long orderId,
        LocalDateTime alarmTime,
        BigDecimal lng,
        BigDecimal lat,
        BigDecimal speed,
        String address,
        int handleStatus,
        String handleStatusDesc,
        Long handleUserId,
        LocalDateTime handleTime,
        String handleRemark,
        int escalateFlag,
        LocalDateTime escalateTime) {
}
