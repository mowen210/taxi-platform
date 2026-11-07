package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Order;

import java.time.LocalDateTime;

/**
 * 订单仓储。
 */
public interface OrderRepository {

    Order findById(Long id);

    Order findByOrderNo(String orderNo);

    /** 按 905 营运 ID 查订单（巡游单幂等键） */
    Order findByBusinessId(String businessId);

    PageResult<Order> search(String keyword, String status, Long fleetId, Long driverId,
                             LocalDateTime startTime, LocalDateTime endTime, int page, int size);

    void save(Order order);

    void update(Order order);

    /** 撤回派单：清空车辆/司机/终端/计价器与派单时间（updateById 不会置空，需显式 UPDATE） */
    void clearDispatch(Long id, String status);

    /** 司机当前进行中的订单数（已派单 + 已载客），用于派单冲突校验 */
    long countActiveByDriver(Long driverId);

    /** 车辆当前进行中的订单数 */
    long countActiveByVehicle(Long vehicleId);
}
