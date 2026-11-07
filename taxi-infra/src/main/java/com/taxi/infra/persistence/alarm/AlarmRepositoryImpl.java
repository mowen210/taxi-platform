package com.taxi.infra.persistence.alarm;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.taxi.common.result.PageResult;
import com.taxi.domain.alarm.AlarmRecord;
import com.taxi.domain.repository.AlarmRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 报警记录 JDBC 仓储。查询统一联车辆表，带出车牌与车队，供列表展示与数据权限过滤。
 */
@Repository
public class AlarmRepositoryImpl implements AlarmRepository {

    private static final String SELECT = """
            SELECT a.id, a.alarm_no, a.alarm_type, a.alarm_level, a.source, a.alarm_flag_hex,
                   a.ext_alarm_code, a.biz_alarm_id, a.vehicle_id, a.driver_id, a.terminal_id,
                   a.order_id, a.alarm_time, a.lng, a.lat, a.speed, a.address, a.handle_status,
                   a.handle_user_id, a.handle_time, a.handle_remark, a.escalate_flag, a.escalate_time,
                   a.score_deducted, a.raw_hex, v.plate_no, v.fleet_id
            FROM t_alarm_record a
            LEFT JOIN t_vehicle v ON v.id = a.vehicle_id
            """;

    private static final RowMapper<AlarmRecord> MAPPER = (rs, rowNum) -> map(rs);

    private final JdbcTemplate jdbcTemplate;

    public AlarmRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static AlarmRecord map(ResultSet rs) throws SQLException {
        return new AlarmRecord(
                rs.getLong("id"),
                rs.getString("alarm_no"),
                rs.getString("alarm_type"),
                rs.getInt("alarm_level"),
                rs.getInt("source"),
                rs.getString("alarm_flag_hex"),
                rs.getString("ext_alarm_code"),
                rs.getString("biz_alarm_id"),
                nullableLong(rs, "vehicle_id"),
                nullableLong(rs, "driver_id"),
                rs.getString("terminal_id"),
                nullableLong(rs, "order_id"),
                rs.getObject("alarm_time", LocalDateTime.class),
                rs.getBigDecimal("lng"),
                rs.getBigDecimal("lat"),
                rs.getBigDecimal("speed"),
                rs.getString("address"),
                rs.getInt("handle_status"),
                nullableLong(rs, "handle_user_id"),
                rs.getObject("handle_time", LocalDateTime.class),
                rs.getString("handle_remark"),
                rs.getInt("escalate_flag"),
                rs.getObject("escalate_time", LocalDateTime.class),
                rs.getBigDecimal("score_deducted"),
                rs.getString("raw_hex"),
                rs.getString("plate_no"),
                nullableLong(rs, "fleet_id"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    @Override
    public Long insert(AlarmRecord alarm) {
        long id = IdWorker.getId();
        jdbcTemplate.update("""
                INSERT INTO t_alarm_record (id, alarm_no, alarm_type, alarm_level, source, alarm_flag_hex,
                    ext_alarm_code, biz_alarm_id, vehicle_id, driver_id, terminal_id, order_id, alarm_time,
                    lng, lat, speed, address, handle_status, escalate_flag, raw_hex)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, alarm.alarmNo(), alarm.alarmType(), alarm.alarmLevel(), alarm.source(),
                alarm.alarmFlagHex(), alarm.extAlarmCode(), alarm.bizAlarmId(), alarm.vehicleId(),
                alarm.driverId(), alarm.terminalId(), alarm.orderId(), alarm.alarmTime(), alarm.lng(),
                alarm.lat(), alarm.speed(), alarm.address(), alarm.handleStatus(), alarm.escalateFlag(),
                alarm.rawHex());
        return id;
    }

    @Override
    public boolean existsOpenSince(Long vehicleId, String alarmType, LocalDateTime since) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_alarm_record
                WHERE vehicle_id = ? AND alarm_type = ? AND deleted = 0
                  AND handle_status IN (0, 1) AND alarm_time >= ?
                """, Integer.class, vehicleId, alarmType, since);
        return count != null && count > 0;
    }

    @Override
    public AlarmRecord findById(Long id) {
        List<AlarmRecord> list = jdbcTemplate.query(SELECT + " WHERE a.id = ? AND a.deleted = 0", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AlarmRecord findLatestOpenByVehicle(Long vehicleId, LocalDateTime since) {
        List<AlarmRecord> list = jdbcTemplate.query(SELECT + """
                WHERE a.vehicle_id = ? AND a.deleted = 0 AND a.handle_status IN (0, 1)
                  AND a.alarm_time >= ? ORDER BY a.alarm_time DESC LIMIT 1
                """, MAPPER, vehicleId, since);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AlarmRecord findByBizAlarmId(String terminalId, String bizAlarmId) {
        List<AlarmRecord> list = jdbcTemplate.query(SELECT + """
                WHERE a.terminal_id = ? AND a.biz_alarm_id = ? AND a.deleted = 0
                ORDER BY a.alarm_time DESC LIMIT 1
                """, MAPPER, terminalId, bizAlarmId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public PageResult<AlarmRecord> page(String alarmType, Integer level, Integer handleStatus, Long fleetId,
                                        LocalDateTime start, LocalDateTime end, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE a.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (alarmType != null && !alarmType.isBlank()) {
            where.append(" AND a.alarm_type = ? ");
            args.add(alarmType.trim());
        }
        if (level != null) {
            where.append(" AND a.alarm_level = ? ");
            args.add(level);
        }
        if (handleStatus != null) {
            where.append(" AND a.handle_status = ? ");
            args.add(handleStatus);
        }
        if (fleetId != null) {
            where.append(" AND v.fleet_id = ? ");
            args.add(fleetId);
        }
        if (start != null) {
            where.append(" AND a.alarm_time >= ? ");
            args.add(start);
        }
        if (end != null) {
            where.append(" AND a.alarm_time <= ? ");
            args.add(end);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_alarm_record a "
                + "LEFT JOIN t_vehicle v ON v.id = a.vehicle_id" + where, Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        List<AlarmRecord> list = jdbcTemplate.query(SELECT + where
                + " ORDER BY a.alarm_time DESC, a.id DESC LIMIT ? OFFSET ?", MAPPER, args.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, list);
    }

    @Override
    public int changeHandleStatus(Long id, int fromStatus, int toStatus, Long userId, String remark) {
        return jdbcTemplate.update("""
                UPDATE t_alarm_record
                SET handle_status = ?, handle_user_id = ?, handle_time = NOW(), handle_remark = ?
                WHERE id = ? AND handle_status = ? AND deleted = 0
                """, toStatus, userId, remark, id, fromStatus);
    }

    @Override
    public List<AlarmRecord> findEscalatable(int level, LocalDateTime before, int limit) {
        return jdbcTemplate.query(SELECT + """
                WHERE a.deleted = 0 AND a.handle_status = 0 AND a.alarm_level = ?
                  AND a.alarm_time <= ? ORDER BY a.alarm_time ASC LIMIT ?
                """, MAPPER, level, before, limit);
    }

    @Override
    public void escalate(Long id, int newLevel, LocalDateTime time) {
        jdbcTemplate.update("""
                UPDATE t_alarm_record
                SET alarm_level = ?, escalate_flag = 1, escalate_time = ?
                WHERE id = ? AND handle_status = 0 AND deleted = 0
                """, newLevel, time, id);
    }

    @Override
    public int countOpen(Long fleetId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM t_alarm_record a LEFT JOIN t_vehicle v ON v.id = a.vehicle_id
                WHERE a.deleted = 0 AND a.handle_status IN (0, 1)
                  AND (? IS NULL OR v.fleet_id = ?)
                """, Integer.class, fleetId, fleetId);
        return count == null ? 0 : count;
    }

    @Override
    public Integer findRuleLevel(String alarmType, Long vehicleId, Long fleetId) {
        List<Integer> levels = jdbcTemplate.query("""
                SELECT alarm_level FROM t_alarm_rule_config
                WHERE alarm_type = ? AND enabled = 1 AND deleted = 0
                  AND ((scope_type = 3 AND scope_id = ?)
                    OR (scope_type = 2 AND scope_id = ?)
                    OR scope_type = 1)
                ORDER BY scope_type DESC, id ASC LIMIT 1
                """, (rs, rowNum) -> rs.getInt(1), alarmType, vehicleId, fleetId == null ? -1L : fleetId);
        return levels.isEmpty() ? null : levels.get(0);
    }
}
