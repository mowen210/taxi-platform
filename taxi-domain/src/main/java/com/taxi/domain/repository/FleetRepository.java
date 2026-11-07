package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Fleet;

import java.util.List;

/**
 * 车队仓储。
 */
public interface FleetRepository {

    Fleet findById(Long id);

    Fleet findByCode(String fleetCode);

    PageResult<Fleet> search(String keyword, Integer status, int page, int size);

    List<Fleet> listAllEnabled();

    void save(Fleet fleet);

    void update(Fleet fleet);

    void delete(Long id);

    /** 车队下车辆数，用于删除前校验 */
    long countVehicles(Long fleetId);

    /** 车队下司机数，用于删除前校验 */
    long countDrivers(Long fleetId);
}
