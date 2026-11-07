package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Vehicle;
import com.taxi.domain.repository.VehicleRepository;
import com.taxi.infra.persistence.mapper.VehicleMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * 车辆仓储实现。
 */
@Repository
public class VehicleRepositoryImpl implements VehicleRepository {

    private final VehicleMapper vehicleMapper;

    public VehicleRepositoryImpl(VehicleMapper vehicleMapper) {
        this.vehicleMapper = vehicleMapper;
    }

    @Override
    public Vehicle findById(Long id) {
        return vehicleMapper.selectById(id);
    }

    @Override
    public Vehicle findByPlateNo(String plateNo) {
        return vehicleMapper.selectOne(Wrappers.<Vehicle>lambdaQuery().eq(Vehicle::getPlateNo, plateNo));
    }

    @Override
    public PageResult<Vehicle> search(String keyword, Long fleetId, Integer status, int page, int size) {
        LambdaQueryWrapper<Vehicle> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(Vehicle::getPlateNo, trimmed)
                    .or().like(Vehicle::getVin, trimmed)
                    .or().like(Vehicle::getOwnerName, trimmed));
        }
        if (fleetId != null) {
            wrapper.eq(Vehicle::getFleetId, fleetId);
        }
        if (status != null) {
            wrapper.eq(Vehicle::getStatus, status);
        }
        wrapper.orderByDesc(Vehicle::getCreateTime);
        Page<Vehicle> result = vehicleMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public void save(Vehicle vehicle) {
        vehicleMapper.insert(vehicle);
    }

    @Override
    public void update(Vehicle vehicle) {
        vehicleMapper.updateById(vehicle);
    }

    @Override
    public void delete(Long id) {
        vehicleMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        vehicleMapper.update(null, Wrappers.<Vehicle>lambdaUpdate()
                .eq(Vehicle::getId, id)
                .set(Vehicle::getStatus, status));
    }

    @Override
    public long countByDriver(Long driverId) {
        return vehicleMapper.selectCount(Wrappers.<Vehicle>lambdaQuery().eq(Vehicle::getDriverId, driverId));
    }

    @Override
    public List<Vehicle> findExpiringBefore(LocalDate deadline) {
        LambdaQueryWrapper<Vehicle> wrapper = Wrappers.<Vehicle>lambdaQuery()
                .and(w -> w.le(Vehicle::getInsuranceExpireDate, deadline)
                        .or().le(Vehicle::getAnnualInspectExpireDate, deadline));
        return vehicleMapper.selectList(wrapper);
    }
}
