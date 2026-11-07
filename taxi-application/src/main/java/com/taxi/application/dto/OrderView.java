package com.taxi.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图。乘客手机号只输出脱敏值，nextActions 供前端决定按钮可用性。
 */
public record OrderView(
        Long id,
        String orderNo,
        Integer orderSource,
        String status,
        String statusDesc,
        Long vehicleId,
        String plateNo,
        Long driverId,
        String driverName,
        Long fleetId,
        String fleetName,
        String terminalId,
        String taximeterSn,
        String passengerName,
        String passengerPhoneMask,
        String startAddress,
        BigDecimal startLng,
        BigDecimal startLat,
        String endAddress,
        BigDecimal endLng,
        BigDecimal endLat,
        LocalDateTime dispatchTime,
        LocalDateTime pickupTime,
        LocalDateTime finishTime,
        LocalDateTime calcTime,
        BigDecimal mileage,
        Integer waitingDuration,
        Integer durationSec,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal payableAmount,
        String abnormalReason,
        String cancelReason,
        String remark,
        List<String> nextActions,
        LocalDateTime createTime) {
}
