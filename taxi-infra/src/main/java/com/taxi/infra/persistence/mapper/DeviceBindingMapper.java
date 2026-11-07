package com.taxi.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taxi.domain.entity.DeviceBinding;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 设备绑定 Mapper。仅仓储层可调用。
 */
public interface DeviceBindingMapper extends BaseMapper<DeviceBinding> {

    /** 按去前导 0 的终端号查当前生效绑定（808 12 位 BCD 与档案 10 位号码对齐） */
    @Select("SELECT * FROM t_device_binding WHERE deleted = 0 AND bind_status = 1 "
            + "AND TRIM(LEADING '0' FROM terminal_id) = #{terminalId} "
            + "ORDER BY bind_time DESC LIMIT 1")
    DeviceBinding selectActiveByNumericTerminalId(@Param("terminalId") String terminalId);
}
