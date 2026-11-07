package com.taxi.application.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FareCalculatorTest {

    private static final FareCalculator.Plan PLAN = new FareCalculator.Plan(
            new BigDecimal("14.00"), new BigDecimal("3.00"), new BigDecimal("2.50"),
            new BigDecimal("0.50"), new BigDecimal("0.30"), new BigDecimal("1.00"));

    private static final List<FareCalculator.Tier> TIERS = List.of(
            new FareCalculator.Tier(new BigDecimal("3.00"), new BigDecimal("10.00"), new BigDecimal("2.50")),
            new FareCalculator.Tier(new BigDecimal("10.00"), null, new BigDecimal("3.20")));

    @Test
    @DisplayName("阶梯里程 + 等候费")
    void tiersAndWaiting() {
        FareCalculator.Result result = FareCalculator.calculate(PLAN, TIERS,
                new FareCalculator.Trip(new BigDecimal("12.0"), 180, false, BigDecimal.ZERO));
        // 14 + (7*2.5=17.5) + (2*3.2=6.4) + (3分钟*0.5=1.5) + 燃油1
        assertEquals(new BigDecimal("40.40"), result.total());
        assertEquals(new BigDecimal("40.40"), result.payable());
        assertEquals(4, result.lines().size());
    }

    @Test
    @DisplayName("夜间加成按小计比例计算")
    void nightSurcharge() {
        FareCalculator.Result result = FareCalculator.calculate(PLAN, TIERS,
                new FareCalculator.Trip(new BigDecimal("12.0"), 180, true, BigDecimal.ZERO));
        // (14+17.5+6.4+1.5)=39.4，夜间 30%=11.82，燃油 1
        assertEquals(new BigDecimal("52.22"), result.total());
    }

    @Test
    @DisplayName("无阶梯时按统一里程单价")
    void fallbackPerKm() {
        FareCalculator.Result result = FareCalculator.calculate(PLAN, List.of(),
                new FareCalculator.Trip(new BigDecimal("5.0"), 0, false, BigDecimal.ZERO));
        // 14 + (2*2.5=5) + 燃油1
        assertEquals(new BigDecimal("20.00"), result.total());
    }

    @Test
    @DisplayName("优惠不超过应收，最低为 0")
    void discountFlooredAtZero() {
        FareCalculator.Result result = FareCalculator.calculate(PLAN, TIERS,
                new FareCalculator.Trip(new BigDecimal("3.0"), 0, false, new BigDecimal("100")));
        assertEquals(new BigDecimal("0.00"), result.payable());
    }

    @Test
    @DisplayName("不足一分钟按一分钟等候")
    void waitingCeil() {
        FareCalculator.Result result = FareCalculator.calculate(PLAN, List.of(),
                new FareCalculator.Trip(BigDecimal.ZERO, 1, false, BigDecimal.ZERO));
        assertEquals(new BigDecimal("15.50"), result.total());
    }
}
