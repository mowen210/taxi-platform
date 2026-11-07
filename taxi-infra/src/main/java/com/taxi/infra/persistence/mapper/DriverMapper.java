package com.taxi.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taxi.domain.entity.Driver;

/**
 * 司机 Mapper。仅仓储层可调用，避免绕过加解密逻辑。
 */
public interface DriverMapper extends BaseMapper<Driver> {
}
