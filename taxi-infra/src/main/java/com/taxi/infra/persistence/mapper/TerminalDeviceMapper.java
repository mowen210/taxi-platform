package com.taxi.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taxi.domain.entity.TerminalDevice;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 终端档案 Mapper。仅仓储层可调用。
 */
public interface TerminalDeviceMapper extends BaseMapper<TerminalDevice> {

    /**
     * 按「去前导 0」的终端号查询。
     * <p>808 消息头是 12 位 BCD 手机号（如 000138000001），档案里可能是 10 位（0138000001），
     * 两者数值相等，用去零比较避免因位数不一致找不到终端。</p>
     */
    @Select("SELECT * FROM t_terminal_device WHERE deleted = 0 "
            + "AND TRIM(LEADING '0' FROM terminal_id) = #{terminalId} LIMIT 1")
    TerminalDevice selectByNumericTerminalId(@Param("terminalId") String terminalId);
}
