package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.alarm.AlarmRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报警记录仓储。写入发生在上行协议线程，查询/处置发生在 Web 线程。
 */
public interface AlarmRepository {

    Long insert(AlarmRecord alarm);

    boolean existsOpenSince(Long vehicleId, String alarmType, LocalDateTime since);

    AlarmRecord findById(Long id);

    AlarmRecord findLatestOpenByVehicle(Long vehicleId, LocalDateTime since);

    AlarmRecord findByBizAlarmId(String terminalId, String bizAlarmId);

    PageResult<AlarmRecord> page(String alarmType, Integer level, Integer handleStatus, Long fleetId,
                                 LocalDateTime start, LocalDateTime end, int page, int size);

    int changeHandleStatus(Long id, int fromStatus, int toStatus, Long userId, String remark);

    List<AlarmRecord> findEscalatable(int level, LocalDateTime before, int limit);

    void escalate(Long id, int newLevel, LocalDateTime time);

    int countOpen(Long fleetId);

    /** 规则覆盖等级；无规则返回 null */
    Integer findRuleLevel(String alarmType, Long vehicleId, Long fleetId);
}
