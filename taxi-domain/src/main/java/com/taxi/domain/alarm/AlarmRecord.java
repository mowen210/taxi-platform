package com.taxi.domain.alarm;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 报警记录（t_alarm_record）读模型。plateNo / fleetId 来自车辆表联查，便于列表与数据权限。
 */
public record AlarmRecord(
        Long id,
        String alarmNo,
        String alarmType,
        int alarmLevel,
        int source,
        String alarmFlagHex,
        String extAlarmCode,
        String bizAlarmId,
        Long vehicleId,
        Long driverId,
        String terminalId,
        Long orderId,
        LocalDateTime alarmTime,
        BigDecimal lng,
        BigDecimal lat,
        BigDecimal speed,
        String address,
        int handleStatus,
        Long handleUserId,
        LocalDateTime handleTime,
        String handleRemark,
        int escalateFlag,
        LocalDateTime escalateTime,
        BigDecimal scoreDeducted,
        String rawHex,
        String plateNo,
        Long fleetId) {
}
