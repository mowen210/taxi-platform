package com.taxi.infra.persistence.raw;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 905 原始报文落库。
 * <p>表按日分表（t_taximeter_raw_YYYYMMDD），首次写入当天表时按模板表复制结构；
 * 原始 HEX 一律留痕，解析失败也写入并把原因放 parse_error，便于与厂商对账。</p>
 */
@Component
public class TaximeterRawWriter {

    private static final Logger log = LoggerFactory.getLogger(TaximeterRawWriter.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String TEMPLATE_TABLE = "t_taximeter_raw_20260101";

    private static final String INSERT_SQL = """
            INSERT INTO %s (id, order_no, terminal_id, vehicle_id, taximeter_sn, biz_type, msg_seq,
                            business_id, start_time, end_time, pickup_lng, pickup_lat, dropoff_lng, dropoff_lat,
                            amount, billing_mileage, waiting_duration, raw_hex, parse_status, parse_error, report_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();

    public TaximeterRawWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 写入一条留痕记录；同一 business_id + biz_type 重复上报按唯一索引忽略 */
    public void write(TaximeterRawEntry entry) {
        String table = "t_taximeter_raw_" + DAY.format(entry.reportTime());
        ensureTable(table);
        try {
            jdbcTemplate.update(INSERT_SQL.formatted(table),
                    IdWorker.getId(),
                    entry.orderNo(),
                    entry.terminalId(),
                    entry.vehicleId(),
                    entry.taximeterSn(),
                    entry.bizType(),
                    entry.msgSeq(),
                    entry.businessId(),
                    entry.startTime(),
                    entry.endTime(),
                    entry.pickupLng(),
                    entry.pickupLat(),
                    entry.dropoffLng(),
                    entry.dropoffLat(),
                    entry.amount(),
                    entry.billingMileage(),
                    entry.waitingDuration(),
                    entry.rawHex(),
                    entry.parseStatus(),
                    entry.parseError(),
                    entry.reportTime());
        } catch (DuplicateKeyException e) {
            log.info("905 原始报文重复上报，已按唯一键忽略：terminal={} businessId={} bizType={}",
                    entry.terminalId(), entry.businessId(), entry.bizType());
        }
    }

    private void ensureTable(String table) {
        if (ensuredTables.contains(table)) {
            return;
        }
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `" + table + "` LIKE `" + TEMPLATE_TABLE + "`");
        ensuredTables.add(table);
    }
}
