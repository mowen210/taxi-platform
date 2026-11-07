package com.taxi.application.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.taxi.application.support.FareCalculator;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 计价与结算：方案读取、试算、按周期生成司机结算单。
 * <p>结算金额口径：订单应收合计 × 司机分成比例；订单置 settle_status=1 防重复结算。</p>
 */
@Service
public class BillingAppService {

    private static final DateTimeFormatter NO_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final JdbcTemplate jdbcTemplate;

    @Value("${billing.default-driver-share:0.8000}")
    private BigDecimal defaultDriverShare = new BigDecimal("0.8000");

    public BillingAppService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record PlanView(Long id, String planCode, String planName, String strategyType,
                           BigDecimal baseFare, BigDecimal baseMileage, BigDecimal perKm,
                           BigDecimal waitingPerMin, BigDecimal fuelSurcharge,
                           BigDecimal nightRate, BigDecimal driverShareRate, int enabled) {
    }

    public record QuoteRequest(Long planId, BigDecimal mileage, Integer waitingSeconds,
                               Boolean night, BigDecimal discount) {
    }

    public record SettleView(Long id, String settleNo, LocalDate periodStart, LocalDate periodEnd,
                             Long driverId, String driverName, Long fleetId, int orderCount,
                             BigDecimal totalAmount, BigDecimal platformFee, BigDecimal driverShare,
                             BigDecimal payableAmount, int status) {
    }

    public List<PlanView> plans() {
        return jdbcTemplate.query("""
                SELECT id, plan_code, plan_name, strategy_type, base_fare, base_mileage, per_km_price,
                       waiting_price_per_min, fuel_surcharge, night_rate_json, driver_share_rate, enabled
                FROM t_pricing_plan WHERE deleted = 0 ORDER BY priority DESC, id ASC
                """, (rs, i) -> new PlanView(rs.getLong("id"), rs.getString("plan_code"),
                rs.getString("plan_name"), rs.getString("strategy_type"), rs.getBigDecimal("base_fare"),
                rs.getBigDecimal("base_mileage"), rs.getBigDecimal("per_km_price"),
                rs.getBigDecimal("waiting_price_per_min"), rs.getBigDecimal("fuel_surcharge"),
                nightRate(rs.getString("night_rate_json")), rs.getBigDecimal("driver_share_rate"),
                rs.getInt("enabled")));
    }

    public FareCalculator.Result quote(QuoteRequest request) {
        Long planId = request.planId() == null ? 1L : request.planId();
        Map<String, Object> plan = jdbcTemplate.queryForMap("""
                SELECT base_fare, base_mileage, per_km_price, waiting_price_per_min, night_rate_json,
                       fuel_surcharge FROM t_pricing_plan WHERE id = ? AND deleted = 0
                """, planId);
        List<FareCalculator.Tier> tiers = jdbcTemplate.query("""
                SELECT seg_start, seg_end, unit_price FROM t_pricing_plan_item
                WHERE plan_id = ? AND item_type = 2 AND deleted = 0 ORDER BY seg_start
                """, (rs, i) -> new FareCalculator.Tier(rs.getBigDecimal("seg_start"),
                rs.getBigDecimal("seg_end"), rs.getBigDecimal("unit_price")), planId);
        FareCalculator.Plan p = new FareCalculator.Plan(
                (BigDecimal) plan.get("base_fare"), (BigDecimal) plan.get("base_mileage"),
                (BigDecimal) plan.get("per_km_price"), (BigDecimal) plan.get("waiting_price_per_min"),
                nightRate((String) plan.get("night_rate_json")), (BigDecimal) plan.get("fuel_surcharge"));
        return FareCalculator.calculate(p, tiers, new FareCalculator.Trip(
                request.mileage(), request.waitingSeconds() == null ? 0 : request.waitingSeconds(),
                Boolean.TRUE.equals(request.night()), request.discount()));
    }

    /** 按周期生成结算单；同一司机同周期已存在则跳过。 */
    @Transactional(rollbackFor = Exception.class)
    public int generate(LocalDate start, LocalDate end, Long fleetId) {
        if (start == null || end == null || end.isBefore(start)) {
            throw BizException.badRequest("结算周期不合法");
        }
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        StringBuilder sql = new StringBuilder("""
                SELECT driver_id, COUNT(*) cnt, COALESCE(SUM(payable_amount),0) total
                FROM t_order WHERE deleted = 0 AND settle_status = 0
                  AND status IN ('CALCULATED','INVOICE_PENDING','INVOICE_SUCCESS','INVOICE_FAIL')
                  AND finish_time >= ? AND finish_time < ? AND driver_id IS NOT NULL
                """);
        List<Object> args = new ArrayList<>(List.of(from, to));
        if (fleetId != null) {
            sql.append(" AND fleet_id = ?");
            args.add(fleetId);
        }
        sql.append(" GROUP BY driver_id");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        int created = 0;
        for (Map<String, Object> row : rows) {
            Long driverId = ((Number) row.get("driver_id")).longValue();
            Integer exists = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM t_settle_record WHERE driver_id = ? AND period_start = ?
                      AND period_end = ? AND deleted = 0
                    """, Integer.class, driverId, start, end);
            if (exists != null && exists > 0) {
                continue;
            }
            int count = ((Number) row.get("cnt")).intValue();
            BigDecimal total = (BigDecimal) row.get("total");
            BigDecimal share = driverShare(driverId, fleetId);
            BigDecimal driverAmount = total.multiply(share).setScale(2, RoundingMode.HALF_UP);
            BigDecimal platformFee = total.subtract(driverAmount);
            Long fleet = fleetId;
            if (fleet == null) {
                fleet = jdbcTemplate.queryForObject(
                        "SELECT fleet_id FROM t_driver WHERE id = ?", Long.class, driverId);
            }
            String settleNo = "ST" + LocalDateTime.now().format(NO_TS) + driverId;
            jdbcTemplate.update("""
                    INSERT INTO t_settle_record (id, settle_no, period_start, period_end, driver_id, fleet_id,
                        order_count, total_amount, platform_fee, driver_share_amount, payable_amount, status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,1)
                    """, IdWorker.getId(), settleNo, start, end, driverId, fleet, count,
                    total, platformFee, driverAmount, driverAmount);
            created++;
        }
        jdbcTemplate.update("""
                UPDATE t_order SET settle_status = 1
                WHERE deleted = 0 AND settle_status = 0 AND driver_id IS NOT NULL
                  AND status IN ('CALCULATED','INVOICE_PENDING','INVOICE_SUCCESS','INVOICE_FAIL')
                  AND finish_time >= ? AND finish_time < ? AND (? IS NULL OR fleet_id = ?)
                """, from, to, fleetId, fleetId);
        return created;
    }

    public PageResult<SettleView> page(LocalDate start, LocalDate end, Long fleetId, Integer status,
                                       int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE s.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (start != null) {
            where.append(" AND s.period_start >= ? ");
            args.add(start);
        }
        if (end != null) {
            where.append(" AND s.period_end <= ? ");
            args.add(end);
        }
        if (fleetId != null) {
            where.append(" AND s.fleet_id = ? ");
            args.add(fleetId);
        }
        if (status != null) {
            where.append(" AND s.status = ? ");
            args.add(status);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_settle_record s" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<SettleView> list = jdbcTemplate.query("""
                SELECT s.id, s.settle_no, s.period_start, s.period_end, s.driver_id, d.name driver_name,
                       s.fleet_id, s.order_count, s.total_amount, s.platform_fee, s.driver_share_amount,
                       s.payable_amount, s.status
                FROM t_settle_record s LEFT JOIN t_driver d ON d.id = s.driver_id
                """ + where + " ORDER BY s.id DESC LIMIT ? OFFSET ?", (rs, i) -> new SettleView(
                rs.getLong("id"), rs.getString("settle_no"), rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class), rs.getLong("driver_id"),
                rs.getString("driver_name"), rs.getObject("fleet_id", Long.class), rs.getInt("order_count"),
                rs.getBigDecimal("total_amount"), rs.getBigDecimal("platform_fee"),
                rs.getBigDecimal("driver_share_amount"), rs.getBigDecimal("payable_amount"),
                rs.getInt("status")), pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, list);
    }

    public void changeStatus(Long id, int from, int to) {
        int updated = jdbcTemplate.update("""
                UPDATE t_settle_record SET status = ?, confirm_time = IF(? = 2, NOW(), confirm_time),
                       pay_time = IF(? = 3, NOW(), pay_time)
                WHERE id = ? AND status = ? AND deleted = 0
                """, to, to, to, id, from);
        if (updated == 0) {
            throw BizException.badRequest("结算单状态已变化，请刷新后重试");
        }
    }

    public List<SettleView> export(LocalDate start, LocalDate end, Long fleetId) {
        return page(start, end, fleetId, null, 1, 10000).list();
    }

    private BigDecimal driverShare(Long driverId, Long fleetId) {
        List<BigDecimal> shares = jdbcTemplate.query("""
                SELECT driver_share_rate FROM t_pricing_plan
                WHERE deleted = 0 AND enabled = 1 AND (scope_type = 1 OR (scope_type = 2 AND scope_id = ?)
                    OR (scope_type = 3 AND scope_id = ?))
                ORDER BY scope_type DESC, priority DESC LIMIT 1
                """, (rs, i) -> rs.getBigDecimal(1), fleetId == null ? -1 : fleetId, driverId);
        return shares.isEmpty() || shares.get(0) == null ? defaultDriverShare : shares.get(0);
    }

    private static BigDecimal nightRate(String json) {
        if (json == null || json.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            int idx = json.indexOf("\"rate\"");
            if (idx < 0) {
                return BigDecimal.ZERO;
            }
            String tail = json.substring(idx + 6).replace(":", "").replace("}", "").replace("\"", "").trim();
            return new BigDecimal(tail.split("[,}]")[0].trim());
        } catch (RuntimeException e) {
            return BigDecimal.ZERO;
        }
    }
}
