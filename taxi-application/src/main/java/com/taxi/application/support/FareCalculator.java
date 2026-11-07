package com.taxi.application.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 计价引擎：起步价 + 阶梯里程 + 等候 + 夜间加成 + 燃油附加。
 * <p>纯函数，便于单测；金额最后统一四舍五入到分。里程区间左闭右开，最后一段无上限。</p>
 */
public final class FareCalculator {

    private FareCalculator() {
    }

    public record Plan(BigDecimal baseFare, BigDecimal baseMileage, BigDecimal perKm,
                       BigDecimal waitingPerMin, BigDecimal nightRate, BigDecimal fuelSurcharge) {
    }

    public record Tier(BigDecimal start, BigDecimal end, BigDecimal unitPrice) {
    }

    public record Trip(BigDecimal mileage, int waitingSeconds, boolean night, BigDecimal discount) {
    }

    public record Line(String name, BigDecimal amount) {
    }

    public record Result(List<Line> lines, BigDecimal total, BigDecimal payable) {
    }

    public static Result calculate(Plan plan, List<Tier> tiers, Trip trip) {
        BigDecimal mileage = nz(trip.mileage());
        BigDecimal baseMileage = nz(plan.baseMileage());
        List<Line> lines = new ArrayList<>();

        lines.add(new Line("起步价", money(plan.baseFare())));
        BigDecimal distance = mileage.subtract(baseMileage).max(BigDecimal.ZERO);
        if (distance.signum() > 0) {
            BigDecimal fee = BigDecimal.ZERO;
            if (tiers == null || tiers.isEmpty()) {
                fee = distance.multiply(nz(plan.perKm()));
            } else {
                List<Tier> sorted = new ArrayList<>(tiers);
                sorted.sort(Comparator.comparing(Tier::start));
                for (Tier tier : sorted) {
                    BigDecimal start = nz(tier.start()).max(baseMileage);
                    BigDecimal end = tier.end() == null ? mileage : tier.end().min(mileage);
                    if (end.compareTo(start) > 0) {
                        fee = fee.add(end.subtract(start).multiply(nz(tier.unitPrice())));
                    }
                }
            }
            lines.add(new Line("里程费", money(fee)));
        }

        if (trip.waitingSeconds() > 0 && nz(plan.waitingPerMin()).signum() > 0) {
            BigDecimal minutes = BigDecimal.valueOf((trip.waitingSeconds() + 59) / 60);
            lines.add(new Line("等候费", money(minutes.multiply(plan.waitingPerMin()))));
        }

        BigDecimal subtotal = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (trip.night() && nz(plan.nightRate()).signum() > 0) {
            lines.add(new Line("夜间加成", money(subtotal.multiply(plan.nightRate()))));
        }
        if (nz(plan.fuelSurcharge()).signum() > 0) {
            lines.add(new Line("燃油附加", money(plan.fuelSurcharge())));
        }

        BigDecimal total = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal payable = total.subtract(nz(trip.discount())).max(BigDecimal.ZERO);
        return new Result(lines, money(total), money(payable));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP);
    }
}
