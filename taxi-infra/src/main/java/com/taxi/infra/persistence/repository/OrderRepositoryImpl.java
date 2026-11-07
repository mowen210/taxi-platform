package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Order;
import com.taxi.domain.repository.OrderRepository;
import com.taxi.infra.crypto.SensitiveFieldCodec;
import com.taxi.infra.persistence.mapper.OrderMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单仓储实现。乘客手机号加密入库，读库解密。
 */
@Repository
public class OrderRepositoryImpl implements OrderRepository {

    private static final List<String> ACTIVE_STATUSES = List.of("DISPATCHED", "PICKUP");

    private final OrderMapper orderMapper;
    private final SensitiveFieldCodec codec;

    public OrderRepositoryImpl(OrderMapper orderMapper, SensitiveFieldCodec codec) {
        this.orderMapper = orderMapper;
        this.codec = codec;
    }

    @Override
    public Order findById(Long id) {
        return decrypt(orderMapper.selectById(id));
    }

    @Override
    public Order findByOrderNo(String orderNo) {
        return decrypt(orderMapper.selectOne(Wrappers.<Order>lambdaQuery().eq(Order::getOrderNo, orderNo)));
    }

    @Override
    public Order findByBusinessId(String businessId) {
        if (!StringUtils.hasText(businessId)) {
            return null;
        }
        List<Order> list = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getBusinessId, businessId.trim())
                .orderByAsc(Order::getId));
        return list.isEmpty() ? null : decrypt(list.get(0));
    }

    @Override
    public PageResult<Order> search(String keyword, String status, Long fleetId, Long driverId,
                                    LocalDateTime startTime, LocalDateTime endTime, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(Order::getOrderNo, trimmed).or().like(Order::getPassengerName, trimmed));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Order::getStatus, status.trim());
        }
        if (fleetId != null) {
            wrapper.eq(Order::getFleetId, fleetId);
        }
        if (driverId != null) {
            wrapper.eq(Order::getDriverId, driverId);
        }
        if (startTime != null) {
            wrapper.ge(Order::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(Order::getCreateTime, endTime);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> result = orderMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::decrypt);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public void save(Order order) {
        Order persist = toPersist(order);
        orderMapper.insert(persist);
        order.setId(persist.getId());
    }

    @Override
    public void update(Order order) {
        orderMapper.updateById(toPersist(order));
    }

    @Override
    public void clearDispatch(Long id, String status) {
        orderMapper.update(null, Wrappers.<Order>lambdaUpdate()
                .eq(Order::getId, id)
                .set(Order::getStatus, status)
                .set(Order::getVehicleId, null)
                .set(Order::getDriverId, null)
                .set(Order::getTerminalId, null)
                .set(Order::getTaximeterSn, null)
                .set(Order::getDispatchTime, null));
    }

    @Override
    public long countActiveByDriver(Long driverId) {
        return orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getDriverId, driverId)
                .in(Order::getStatus, ACTIVE_STATUSES));
    }

    @Override
    public long countActiveByVehicle(Long vehicleId) {
        return orderMapper.selectCount(Wrappers.<Order>lambdaQuery()
                .eq(Order::getVehicleId, vehicleId)
                .in(Order::getStatus, ACTIVE_STATUSES));
    }

    private Order decrypt(Order order) {
        if (order != null) {
            order.setPassengerPhone(codec.decrypt(order.getPassengerPhone()));
        }
        return order;
    }

    private Order toPersist(Order source) {
        Order target = new Order();
        BeanUtils.copyProperties(source, target);
        target.setPassengerPhone(codec.encryptOrNull(source.getPassengerPhone()));
        return target;
    }
}
