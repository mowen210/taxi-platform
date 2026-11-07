package com.taxi.infra.persistence.track;

import com.taxi.domain.track.TrackPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TdengineSqlTest {

    private static final LocalDateTime TS = LocalDateTime.of(2026, 11, 7, 23, 10, 5);

    private static TrackPoint point() {
        return new TrackPoint(1L, "0138000001", "沪A00001", TS,
                new BigDecimal("121.473701"), new BigDecimal("31.230416"),
                40, 90, 10, 12.5, 1, 0L, 12L, "TO20261107001");
    }

    @Test
    @DisplayName("写入 SQL：子表按车辆 ID 建，TAGS 与列顺序固定")
    void insertSql() {
        String sql = TdengineTrackRepository.insertSql("taxi_track", point());
        assertTrue(sql.startsWith("INSERT INTO `taxi_track`.`t_track_1` USING `taxi_track`.`st_track` TAGS (1,"));
        assertTrue(sql.contains("'2026-11-07 23:10:05.000'"));
        assertTrue(sql.contains("121.473701"));
        assertTrue(sql.contains("'TO20261107001'"));
    }

    @Test
    @DisplayName("轨迹查询 SQL：按车辆与时间过滤、按时间升序、限制条数")
    void historySql() {
        String sql = TdengineTrackRepository.historySql("taxi_track", 1L,
                TS.minusHours(1), TS, 9999);
        assertTrue(sql.contains("vehicle_id = 1"));
        assertTrue(sql.contains("ts >= '2026-11-07 22:10:05'"));
        assertTrue(sql.contains("ORDER BY ts ASC LIMIT 5000"));
    }

    @Test
    @DisplayName("最新点 SQL 取倒序一条")
    void latestSql() {
        String sql = TdengineTrackRepository.latestSql("taxi_track", 7L);
        assertTrue(sql.contains("vehicle_id = 7"));
        assertTrue(sql.endsWith("ORDER BY ts DESC LIMIT 1"));
    }

    @Test
    @DisplayName("单引号转义，避免车牌/订单号注入")
    void escapeQuote() {
        TrackPoint p = new TrackPoint(1L, "t'1", "沪A'00001", TS,
                BigDecimal.ONE, BigDecimal.ONE, 0, 0, 0, null, null, 0, 0, "a'b");
        String sql = TdengineTrackRepository.insertSql("taxi_track", p);
        assertTrue(sql.contains("'沪A''00001'"));
        assertTrue(sql.contains("'a''b'"));
    }

    @Test
    @DisplayName("TDengine 返回的 ISO 时间可解析为本地时间")
    void parseTimestamp() {
        assertEquals(TS, TdengineTrackRepository.parseTs("2026-11-07T23:10:05.000Z"));
        assertEquals(TS, TdengineTrackRepository.parseTs("2026-11-07 23:10:05"));
    }
}
