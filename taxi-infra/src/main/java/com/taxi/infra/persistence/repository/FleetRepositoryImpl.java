package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Driver;
import com.taxi.domain.entity.Fleet;
import com.taxi.domain.entity.Vehicle;
import com.taxi.domain.repository.FleetRepository;
import com.taxi.infra.crypto.SensitiveFieldCodec;
import com.taxi.infra.persistence.mapper.DriverMapper;
import com.taxi.infra.persistence.mapper.FleetMapper;
import com.taxi.infra.persistence.mapper.VehicleMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 车队仓储实现。联系电话加密入库，读库解密。
 */
@Repository
public class FleetRepositoryImpl implements FleetRepository {

    private final FleetMapper fleetMapper;
    private final VehicleMapper vehicleMapper;
    private final DriverMapper driverMapper;
    private final SensitiveFieldCodec codec;

    public FleetRepositoryImpl(FleetMapper fleetMapper,
                               VehicleMapper vehicleMapper,
                               DriverMapper driverMapper,
                               SensitiveFieldCodec codec) {
        this.fleetMapper = fleetMapper;
        this.vehicleMapper = vehicleMapper;
        this.driverMapper = driverMapper;
        this.codec = codec;
    }

    @Override
    public Fleet findById(Long id) {
        return decrypt(fleetMapper.selectById(id));
    }

    @Override
    public Fleet findByCode(String fleetCode) {
        return decrypt(fleetMapper.selectOne(Wrappers.<Fleet>lambdaQuery().eq(Fleet::getFleetCode, fleetCode)));
    }

    @Override
    public PageResult<Fleet> search(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<Fleet> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(Fleet::getFleetCode, trimmed)
                    .or().like(Fleet::getFleetName, trimmed)
                    .or().like(Fleet::getContact, trimmed));
        }
        if (status != null) {
            wrapper.eq(Fleet::getStatus, status);
        }
        wrapper.orderByAsc(Fleet::getFleetCode);
        Page<Fleet> result = fleetMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::decrypt);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public List<Fleet> listAllEnabled() {
        List<Fleet> list = fleetMapper.selectList(Wrappers.<Fleet>lambdaQuery()
                .eq(Fleet::getStatus, 1)
                .orderByAsc(Fleet::getFleetCode));
        list.forEach(this::decrypt);
        return list;
    }

    @Override
    public void save(Fleet fleet) {
        Fleet persist = toPersist(fleet);
        fleetMapper.insert(persist);
        fleet.setId(persist.getId());
    }

    @Override
    public void update(Fleet fleet) {
        fleetMapper.updateById(toPersist(fleet));
    }

    @Override
    public void delete(Long id) {
        fleetMapper.deleteById(id);
    }

    @Override
    public long countVehicles(Long fleetId) {
        return vehicleMapper.selectCount(Wrappers.<Vehicle>lambdaQuery().eq(Vehicle::getFleetId, fleetId));
    }

    @Override
    public long countDrivers(Long fleetId) {
        return driverMapper.selectCount(Wrappers.<Driver>lambdaQuery().eq(Driver::getFleetId, fleetId));
    }

    private Fleet decrypt(Fleet fleet) {
        if (fleet != null) {
            fleet.setPhone(codec.decrypt(fleet.getPhone()));
        }
        return fleet;
    }

    private Fleet toPersist(Fleet source) {
        Fleet target = new Fleet();
        BeanUtils.copyProperties(source, target);
        target.setPhone(codec.encryptOrNull(source.getPhone()));
        return target;
    }
}
