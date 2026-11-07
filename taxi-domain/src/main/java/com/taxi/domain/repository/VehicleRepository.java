package com.taxi.domain.repository;

import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Vehicle;

import java.time.LocalDate;
import java.util.List;

/**
 * 车辆仓储。
 */
public interface VehicleRepository {

    Vehicle findById(Long id);

    Vehicle findByPlateNo(String plateNo);

    PageResult<Vehicle> search(String keyword, Long fleetId, Integer status, int page, int size);

    void save(Vehicle vehicle);

    void update(Vehicle vehicle);

    void delete(Long id);

    void updateStatus(Long id, Integer status);

    /** 某司机名下的车辆数，用于司机删除前校验 */
    long countByDriver(Long driverId);

    /** 保险或年检在 deadline 之前到期的车辆（含已过期） */
    List<Vehicle> findExpiringBefore(LocalDate deadline);
}
