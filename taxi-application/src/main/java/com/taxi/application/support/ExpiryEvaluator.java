package com.taxi.application.support;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 到期状态判定。纯函数，便于单测覆盖边界。
 */
public final class ExpiryEvaluator {

    private ExpiryEvaluator() {
    }

    /**
     * 计算预警状态。
     *
     * @param expireDate 到期日，null 视为无到期概念
     * @param warnDays   提前预警天数
     * @param today      参照日
     * @return 0 正常；1 预警（剩余天数 ≤ warnDays）；2 过期
     */
    public static int warnStatus(LocalDate expireDate, int warnDays, LocalDate today) {
        if (expireDate == null) {
            return 0;
        }
        if (expireDate.isBefore(today)) {
            return 2;
        }
        if (!expireDate.isAfter(today.plusDays(Math.max(warnDays, 0)))) {
            return 1;
        }
        return 0;
    }

    /**
     * 距到期天数，负数表示已过期。
     */
    public static long daysLeft(LocalDate expireDate, LocalDate today) {
        if (expireDate == null) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(today, expireDate);
    }
}
