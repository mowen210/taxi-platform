package com.taxi.application.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营统计：总览、趋势、车队排行、报警分布。直接聚合业务表，避免额外汇总表带来的口径漂移。
 */
@Service
public class StatsAppService {

    private final JdbcTemplate jdbcTemplate;

    public StatsAppService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayOrders", count("""
                SELECT COUNT(*) FROM t_order WHERE deleted = 0 AND create_time >= CURDATE()
                """));
        data.put("todayRevenue", decimal("""
                SELECT COALESCE(SUM(payable_amount),0) FROM t_order WHERE deleted = 0
                  AND finish_time >= CURDATE() AND status IN ('CALCULATED','INVOICE_PENDING','INVOICE_SUCCESS')
                """));
        data.put("onlineVehicles", count("""
                SELECT COUNT(*) FROM t_device_binding b JOIN t_terminal_device t ON t.terminal_id = b.terminal_id
                WHERE b.bind_status = 1 AND t.online_status = 1 AND t.deleted = 0
                """));
        data.put("openAlarms", count("""
                SELECT COUNT(*) FROM t_alarm_record WHERE deleted = 0 AND handle_status IN (0,1)
                """));
        data.put("fleetCount", count("SELECT COUNT(*) FROM t_fleet WHERE deleted = 0"));
        data.put("driverCount", count("SELECT COUNT(*) FROM t_driver WHERE deleted = 0 AND status = 1"));
        return data;
    }

    /** 近 N 天订单量与营收，缺失日期补 0。 */
    public List<Map<String, Object>> trend(int days) {
        int n = Math.max(1, Math.min(days, 90));
        Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
        for (int i = n - 1; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", day.toString());
            row.put("orders", 0);
            row.put("revenue", 0);
            byDay.put(day.toString(), row);
        }
        jdbcTemplate.query("""
                SELECT DATE(COALESCE(finish_time, create_time)) d, COUNT(*) c,
                       COALESCE(SUM(CASE WHEN status IN ('CALCULATED','INVOICE_PENDING','INVOICE_SUCCESS')
                           THEN payable_amount ELSE 0 END),0) amount
                FROM t_order WHERE deleted = 0 AND COALESCE(finish_time, create_time) >= ?
                GROUP BY DATE(COALESCE(finish_time, create_time)) ORDER BY d
                """, rs -> {
            String day = rs.getDate("d").toLocalDate().toString();
            Map<String, Object> row = byDay.get(day);
            if (row != null) {
                row.put("orders", rs.getInt("c"));
                row.put("revenue", rs.getBigDecimal("amount"));
            }
        }, LocalDate.now().minusDays(n - 1).atStartOfDay());
        return new ArrayList<>(byDay.values());
    }

    public List<Map<String, Object>> fleetRanking(int days) {
        int n = Math.max(1, Math.min(days, 90));
        return jdbcTemplate.queryForList("""
                SELECT f.id fleetId, f.fleet_name fleetName, COUNT(o.id) orders,
                       COALESCE(SUM(o.payable_amount),0) revenue
                FROM t_fleet f LEFT JOIN t_order o ON o.fleet_id = f.id AND o.deleted = 0
                  AND o.finish_time >= ? AND o.status IN ('CALCULATED','INVOICE_PENDING','INVOICE_SUCCESS')
                WHERE f.deleted = 0 GROUP BY f.id, f.fleet_name ORDER BY revenue DESC, orders DESC LIMIT 20
                """, LocalDate.now().minusDays(n - 1).atStartOfDay());
    }

    public List<Map<String, Object>> alarmDistribution(int days) {
        int n = Math.max(1, Math.min(days, 90));
        return jdbcTemplate.queryForList("""
                SELECT alarm_type type, COUNT(*) cnt,
                       SUM(CASE WHEN handle_status IN (0,1) THEN 1 ELSE 0 END) openCnt
                FROM t_alarm_record WHERE deleted = 0 AND alarm_time >= ?
                GROUP BY alarm_type ORDER BY cnt DESC
                """, LocalDate.now().minusDays(n - 1).atStartOfDay());
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private java.math.BigDecimal decimal(String sql) {
        java.math.BigDecimal value = jdbcTemplate.queryForObject(sql, java.math.BigDecimal.class);
        return value == null ? java.math.BigDecimal.ZERO : value;
    }
}
