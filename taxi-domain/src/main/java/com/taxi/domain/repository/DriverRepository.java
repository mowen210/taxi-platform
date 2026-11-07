package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Driver;

/**
 * 司机仓储。
 */
public interface DriverRepository {

    Driver findById(Long id);

    Driver findByCode(String driverCode);

    PageResult<Driver> search(String keyword, Long fleetId, Integer status, int page, int size);

    void save(Driver driver);

    void update(Driver driver);

    void delete(Long id);

    long countByFleet(Long fleetId);
}
