package com.taxi.infra.persistence.track;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.taxi.domain.repository.TrackRepository;
import com.taxi.domain.track.TrackPoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 轨迹 MySQL 兜底实现（track.provider=mysql，默认）。
 * <p>生产环境用 TDengine（{@link TdengineTrackRepository}）；本实现保证开发机没有时序库时
 * 仍可完整联调定位、监控与轨迹回放。表结构见 05_stage5_monitor.sql。</p>
 */
@Repository
@ConditionalOnProperty(name = "track.provider", havingValue = "mysql", matchIfMissing = true)
public class MysqlTrackRepository implements TrackRepository {

    private static final String INSERT_SQL = """
            INSERT INTO t_track (id, vehicle_id, terminal_id, plate_no, ts, lng, lat, speed, direction,
                                 altitude, mileage, acc_state, alarm_flag, status_flag, order_no)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String COLUMNS = "vehicle_id, terminal_id, plate_no, ts, lng, lat, speed, direction, "
            + "altitude, mileage, acc_state, alarm_flag, status_flag, order_no";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<TrackPoint> rowMapper = (rs, rowNum) -> new TrackPoint(
            rs.getLong("vehicle_id"),
            rs.getString("terminal_id"),
            rs.getString("plate_no"),
            rs.getTimestamp("ts").toLocalDateTime(),
            rs.getBigDecimal("lng"),
            rs.getBigDecimal("lat"),
            rs.getInt("speed"),
            rs.getInt("direction"),
            rs.getInt("altitude"),
            (Double) rs.getObject("mileage"),
            (Integer) rs.getObject("acc_state"),
            rs.getLong("alarm_flag"),
            rs.getLong("status_flag"),
            rs.getString("order_no"));

    public MysqlTrackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(TrackPoint p) {
        jdbcTemplate.update(INSERT_SQL, IdWorker.getId(), p.vehicleId(), p.terminalId(), p.plateNo(), p.ts(),
                p.lng(), p.lat(), p.speed(), p.direction(), p.altitude(), p.mileage(), p.accState(),
                p.alarmFlag(), p.statusFlag(), p.orderNo());
    }

    @Override
    public Optional<TrackPoint> latest(Long vehicleId) {
        List<TrackPoint> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM t_track WHERE vehicle_id = ? ORDER BY ts DESC LIMIT 1",
                rowMapper, vehicleId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public List<TrackPoint> history(Long vehicleId, LocalDateTime start, LocalDateTime end, int limit) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM t_track WHERE vehicle_id = ? AND ts >= ? AND ts <= ? "
                        + "ORDER BY ts ASC LIMIT ?",
                rowMapper, vehicleId, start, end, Math.max(1, Math.min(limit, 5000)));
    }

    @Override
    public String provider() {
        return "mysql";
    }
}
