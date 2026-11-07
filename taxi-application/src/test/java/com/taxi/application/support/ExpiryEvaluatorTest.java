package com.taxi.application.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 到期状态判定边界用例。
 */
class ExpiryEvaluatorTest {

    private final LocalDate today = LocalDate.of(2026, 11, 7);

    @Test
    @DisplayName("无到期日视为正常")
    void shouldTreatNullAsNormal() {
        assertEquals(0, ExpiryEvaluator.warnStatus(null, 30, today));
        assertEquals(0L, ExpiryEvaluator.daysLeft(null, today));
    }

    @Test
    @DisplayName("剩余天数大于阈值不预警")
    void shouldStayNormalWhenFarFromExpiry() {
        assertEquals(0, ExpiryEvaluator.warnStatus(today.plusDays(31), 30, today));
    }

    @Test
    @DisplayName("恰好等于阈值当天进入预警")
    void shouldWarnOnThresholdBoundary() {
        assertEquals(1, ExpiryEvaluator.warnStatus(today.plusDays(30), 30, today));
        assertEquals(1, ExpiryEvaluator.warnStatus(today, 30, today));
    }

    @Test
    @DisplayName("到期日早于今天判定为过期")
    void shouldMarkExpired() {
        assertEquals(2, ExpiryEvaluator.warnStatus(today.minusDays(1), 30, today));
        assertEquals(-1L, ExpiryEvaluator.daysLeft(today.minusDays(1), today));
    }

    @Test
    @DisplayName("阈值为 0 时只在到期当天预警")
    void shouldWarnOnlyTodayWhenZeroDays() {
        assertEquals(1, ExpiryEvaluator.warnStatus(today, 0, today));
        assertEquals(0, ExpiryEvaluator.warnStatus(today.plusDays(1), 0, today));
    }
}
