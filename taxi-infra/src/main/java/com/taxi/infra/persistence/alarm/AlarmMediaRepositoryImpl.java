package com.taxi.infra.persistence.alarm;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.taxi.common.result.PageResult;
import com.taxi.domain.alarm.AlarmMedia;
import com.taxi.domain.repository.AlarmMediaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 报警媒体 JDBC 仓储。文件本身落本地/对象存储，表内只存相对路径与元数据。
 */
@Repository
public class AlarmMediaRepositoryImpl implements AlarmMediaRepository {

    private static final String SELECT = """
            SELECT m.id, m.alarm_id, m.alarm_no, m.media_type, m.channel_no, m.stream_type, m.media_id,
                   m.file_url, m.file_size, m.duration_sec, m.start_time, m.end_time, m.snapshot_time,
                   m.upload_status, m.create_time
            FROM t_alarm_media m
            """;

    private static final RowMapper<AlarmMedia> MAPPER = (rs, rowNum) -> map(rs);

    private final JdbcTemplate jdbcTemplate;

    public AlarmMediaRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static AlarmMedia map(ResultSet rs) throws SQLException {
        return new AlarmMedia(
                rs.getLong("id"),
                rs.getLong("alarm_id"),
                rs.getString("alarm_no"),
                rs.getInt("media_type"),
                nullableInt(rs, "channel_no"),
                nullableInt(rs, "stream_type"),
                nullableLong(rs, "media_id"),
                rs.getString("file_url"),
                nullableLong(rs, "file_size"),
                nullableInt(rs, "duration_sec"),
                rs.getObject("start_time", LocalDateTime.class),
                rs.getObject("end_time", LocalDateTime.class),
                rs.getObject("snapshot_time", LocalDateTime.class),
                rs.getInt("upload_status"),
                rs.getObject("create_time", LocalDateTime.class));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    @Override
    public Long insert(AlarmMedia media) {
        long id = IdWorker.getId();
        jdbcTemplate.update("""
                INSERT INTO t_alarm_media (id, alarm_id, alarm_no, media_type, channel_no, stream_type,
                    media_id, file_url, file_size, duration_sec, start_time, end_time, snapshot_time, upload_status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, media.alarmId(), media.alarmNo(), media.mediaType(), media.channelNo(),
                media.streamType(), media.mediaId(), media.fileUrl(), media.fileSize(), media.durationSec(),
                media.startTime(), media.endTime(), media.snapshotTime(), media.uploadStatus());
        return id;
    }

    @Override
    public AlarmMedia findById(Long id) {
        List<AlarmMedia> list = jdbcTemplate.query(SELECT + " WHERE m.id = ? AND m.deleted = 0", MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<AlarmMedia> findByAlarm(Long alarmId) {
        return jdbcTemplate.query(SELECT + " WHERE m.alarm_id = ? AND m.deleted = 0 ORDER BY m.create_time DESC",
                MAPPER, alarmId);
    }

    @Override
    public PageResult<AlarmMedia> page(Long fleetId, Integer mediaType, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE m.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (fleetId != null) {
            where.append(" AND v.fleet_id = ? ");
            args.add(fleetId);
        }
        if (mediaType != null) {
            where.append(" AND m.media_type = ? ");
            args.add(mediaType);
        }
        String joins = " LEFT JOIN t_alarm_record a ON a.id = m.alarm_id "
                + "LEFT JOIN t_vehicle v ON v.id = a.vehicle_id ";
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_alarm_media m" + joins + where,
                Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        List<AlarmMedia> list = jdbcTemplate.query(SELECT + joins + where
                + " ORDER BY m.create_time DESC LIMIT ? OFFSET ?", MAPPER, args.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, list);
    }

    @Override
    public void updateUploadStatus(Long id, int status, String fileUrl, Long fileSize) {
        jdbcTemplate.update("""
                UPDATE t_alarm_media SET upload_status = ?, file_url = COALESCE(?, file_url),
                       file_size = COALESCE(?, file_size)
                WHERE id = ? AND deleted = 0
                """, status, fileUrl, fileSize, id);
    }
}
