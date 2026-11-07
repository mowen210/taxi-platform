package com.taxi.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约类枚举单测：锁定订单状态数量与 808 报警位映射，防止后续迭代擅自改动协议语义。
 */
class EnumContractTest {

    @Test
    @DisplayName("订单状态必须严格为 10 个，禁止新增")
    void orderStatusShouldHaveExactlyTenValues() {
        assertEquals(10, OrderStatus.values().length);
        assertNotNull(OrderStatus.of("CREATED"));
        assertNotNull(OrderStatus.of("invoice_success"));
        assertNull(OrderStatus.of("PAID"));
    }

    @Test
    @DisplayName("终态与载客态判定")
    void orderStatusFlagShouldBeCorrect() {
        assertTrue(OrderStatus.INVOICE_SUCCESS.isFinalStatus());
        assertTrue(OrderStatus.CANCELLED.isFinalStatus());
        assertFalse(OrderStatus.FINISHED.isFinalStatus());

        assertFalse(OrderStatus.DISPATCHED.isPassengerOnBoard());
        assertTrue(OrderStatus.PICKUP.isPassengerOnBoard());
        assertTrue(OrderStatus.INVOICE_PENDING.isPassengerOnBoard());
    }

    @Test
    @DisplayName("808 报警标志位映射固定：bit0 SOS、bit1 超速、bit2 疲劳")
    void alarmBitMappingShouldMatchJt808() {
        assertEquals(AlarmType.SOS, AlarmType.ofBitIndex(0));
        assertEquals(AlarmType.OVERSPEED, AlarmType.ofBitIndex(1));
        assertEquals(AlarmType.FATIGUE, AlarmType.ofBitIndex(2));
        assertEquals(AlarmType.BLIND_SPOT_RIGHT, AlarmType.ofBitIndex(17));
        assertNull(AlarmType.ofBitIndex(19));

        assertEquals(0, AlarmType.SOS.getBitIndex());
        assertNull(AlarmType.DSM_SMOKING.getBitIndex());
    }

    @Test
    @DisplayName("三级报警默认等级：SOS 三级、超速二级、设备类一级")
    void alarmDefaultLevelShouldMatchDesign() {
        assertEquals(AlarmLevel.LEVEL_3, AlarmType.SOS.getDefaultLevel());
        assertEquals(AlarmLevel.LEVEL_2, AlarmType.OVERSPEED.getDefaultLevel());
        assertEquals(AlarmLevel.LEVEL_1, AlarmType.DEVICE_GNSS_FAULT.getDefaultLevel());
        assertEquals(AlarmLevel.LEVEL_2, AlarmType.DSM_SMOKING.getDefaultLevel());
        assertEquals(AlarmLevel.LEVEL_2, AlarmType.DSM_HANDS_OFF_WHEEL.getDefaultLevel());
    }
}
