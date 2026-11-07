package com.taxi.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taxi.domain.entity.Order;

/**
 * 订单 Mapper。仅仓储层可调用。
 */
public interface OrderMapper extends BaseMapper<Order> {
}
