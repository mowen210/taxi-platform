package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.DeviceBinding;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车辆-终端-计价器绑定仓储。
 */
public interface DeviceBindingRepository {

    DeviceBinding findById(Long id);

    /** 车辆当前生效的绑定 */
    DeviceBinding findActiveByVehicle(Long vehicleId);

    /** 终端当前生效的绑定 */
    DeviceBinding findActiveByTerminal(String terminalId);

    /** 车辆绑定历史（含已解绑） */
    List<DeviceBinding> listByVehicle(Long vehicleId);

    PageResult<DeviceBinding> search(String keyword, Integer bindStatus, int page, int size);

    void save(DeviceBinding binding);

    void update(DeviceBinding binding);

    /** 解绑车辆当前生效绑定，返回受影响行数 */
    int unbindActiveByVehicle(Long vehicleId, LocalDateTime unbindTime, Long operatorId, String remark);
}
