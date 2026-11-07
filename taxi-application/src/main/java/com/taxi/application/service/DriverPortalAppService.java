package com.taxi.application.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.taxi.common.enums.OrderStatus;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.statemachine.OrderAction;
import com.taxi.domain.statemachine.OrderStateMachine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 司机端（H5）：账号绑定、我的行程、接到乘客。与后台司机档案服务分开，避免职责混杂。
 */
@Service
public class DriverPortalAppService {

    private final JdbcTemplate jdbcTemplate;

    public DriverPortalAppService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record DriverView(Long driverId, String driverCode, String name, String phone, Long fleetId,
                             String fleetName, Integer onlineStatus, Integer status) {
    }

    public DriverView me(Long userId) {
        List<DriverView> list = jdbcTemplate.query("""
                SELECT d.id, d.driver_code, d.name, d.phone, d.fleet_id, f.fleet_name,
                       d.online_status, d.status
                FROM t_driver d LEFT JOIN t_fleet f ON f.id = d.fleet_id
                WHERE d.user_id = ? AND d.deleted = 0
                """, (rs, i) -> new DriverView(rs.getLong("id"), rs.getString("driver_code"),
                rs.getString("name"), rs.getString("phone"), rs.getObject("fleet_id", Long.class),
                rs.getString("fleet_name"), rs.getInt("online_status"), rs.getInt("status")), userId);
        if (list.isEmpty()) {
            throw BizException.forbidden("当前账号未绑定司机档案");
        }
        return list.get(0);
    }

    public PageResult<Map<String, Object>> orders(Long userId, String status, int page, int size) {
        DriverView driver = me(userId);
        StringBuilder where = new StringBuilder(" WHERE o.deleted = 0 AND o.driver_id = ? ");
        List<Object> args = new ArrayList<>(List.of(driver.driverId()));
        if (status != null && !status.isBlank()) {
            where.append(" AND o.status = ? ");
            args.add(status.trim());
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_order o" + where,
                Long.class, args.toArray());
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> list = jdbcTemplate.queryForList("""
                SELECT o.id, o.order_no, o.order_source, o.status, o.passenger_name, o.start_address,
                       o.end_address, o.pickup_time, o.finish_time, o.payable_amount, v.plate_no
                FROM t_order o LEFT JOIN t_vehicle v ON v.id = o.vehicle_id
                """ + where + " ORDER BY o.id DESC LIMIT ? OFFSET ?", args.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, list);
    }

    /** 司机点击「接到乘客」：仅可流转状态才允许，写状态日志 source=3。 */
    @Transactional(rollbackFor = Exception.class)
    public void pickup(Long userId, Long orderId) {
        DriverView driver = me(userId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, order_no FROM t_order WHERE id = ? AND driver_id = ? AND deleted = 0",
                orderId, driver.driverId());
        if (rows.isEmpty()) {
            throw BizException.notFound("订单不存在或不属于当前司机");
        }
        String status = (String) rows.get(0).get("status");
        OrderStatus next = OrderStateMachine.transit(OrderStatus.of(status), OrderAction.PICKUP);
        int updated = jdbcTemplate.update("""
                UPDATE t_order SET status = ?, pickup_time = NOW()
                WHERE id = ? AND driver_id = ? AND status = ?
                """, next.name(), orderId, driver.driverId(), status);
        if (updated == 0) {
            throw BizException.badRequest("订单状态已变化，请刷新");
        }
        jdbcTemplate.update("""
                INSERT INTO t_order_status_log (id, order_id, order_no, from_status, to_status, action,
                    operator_id, operator_name, source, remark)
                VALUES (?,?,?,?,?,?,?,?,3,'司机端确认接到乘客')
                """, IdWorker.getId(), orderId, rows.get(0).get("order_no"), status, next.name(),
                OrderAction.PICKUP.name(), userId, driver.name());
    }
}
