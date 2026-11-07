package com.taxi.domain.repository;

import com.taxi.domain.entity.OrderStatusLog;

import java.util.List;

/**
 * 订单状态流转日志仓储。
 */
public interface OrderStatusLogRepository {

    void save(OrderStatusLog log);

    List<OrderStatusLog> listByOrder(Long orderId);
}
