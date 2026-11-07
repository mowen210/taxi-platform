package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taxi.domain.entity.OrderStatusLog;
import com.taxi.domain.repository.OrderStatusLogRepository;
import com.taxi.infra.persistence.mapper.OrderStatusLogMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 订单状态流转日志仓储实现。
 */
@Repository
public class OrderStatusLogRepositoryImpl implements OrderStatusLogRepository {

    private final OrderStatusLogMapper logMapper;

    public OrderStatusLogRepositoryImpl(OrderStatusLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    @Override
    public void save(OrderStatusLog log) {
        logMapper.insert(log);
    }

    @Override
    public List<OrderStatusLog> listByOrder(Long orderId) {
        return logMapper.selectList(Wrappers.<OrderStatusLog>lambdaQuery()
                .eq(OrderStatusLog::getOrderId, orderId)
                .orderByAsc(OrderStatusLog::getId));
    }
}
