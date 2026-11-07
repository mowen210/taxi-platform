package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Driver;
import com.taxi.domain.repository.DriverRepository;
import com.taxi.infra.crypto.SensitiveFieldCodec;
import com.taxi.infra.persistence.mapper.DriverMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 司机仓储实现。手机号/身份证加密入库，支持手机号 HMAC 等值检索。
 */
@Repository
public class DriverRepositoryImpl implements DriverRepository {

    private static final Pattern MOBILE = Pattern.compile("^1\\d{10}$");

    private final DriverMapper driverMapper;
    private final SensitiveFieldCodec codec;

    public DriverRepositoryImpl(DriverMapper driverMapper, SensitiveFieldCodec codec) {
        this.driverMapper = driverMapper;
        this.codec = codec;
    }

    @Override
    public Driver findById(Long id) {
        return decrypt(driverMapper.selectById(id));
    }

    @Override
    public Driver findByCode(String driverCode) {
        return decrypt(driverMapper.selectOne(Wrappers.<Driver>lambdaQuery().eq(Driver::getDriverCode, driverCode)));
    }

    @Override
    public PageResult<Driver> search(String keyword, Long fleetId, Integer status, int page, int size) {
        LambdaQueryWrapper<Driver> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            String phoneHash = MOBILE.matcher(trimmed).matches() ? codec.phoneHashOrNull(trimmed) : null;
            wrapper.and(w -> {
                w.like(Driver::getDriverCode, trimmed)
                        .or().like(Driver::getName, trimmed)
                        .or().like(Driver::getLicenseNo, trimmed);
                if (phoneHash != null) {
                    w.or().eq(Driver::getPhoneHash, phoneHash);
                }
            });
        }
        if (fleetId != null) {
            wrapper.eq(Driver::getFleetId, fleetId);
        }
        if (status != null) {
            wrapper.eq(Driver::getStatus, status);
        }
        wrapper.orderByDesc(Driver::getCreateTime);
        Page<Driver> result = driverMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::decrypt);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public void save(Driver driver) {
        Driver persist = toPersist(driver);
        driverMapper.insert(persist);
        driver.setId(persist.getId());
    }

    @Override
    public void update(Driver driver) {
        driverMapper.updateById(toPersist(driver));
    }

    @Override
    public void delete(Long id) {
        driverMapper.deleteById(id);
    }

    @Override
    public long countByFleet(Long fleetId) {
        return driverMapper.selectCount(Wrappers.<Driver>lambdaQuery().eq(Driver::getFleetId, fleetId));
    }

    private Driver decrypt(Driver driver) {
        if (driver != null) {
            driver.setPhone(codec.decrypt(driver.getPhone()));
            driver.setIdCard(codec.decrypt(driver.getIdCard()));
        }
        return driver;
    }

    private Driver toPersist(Driver source) {
        Driver target = new Driver();
        BeanUtils.copyProperties(source, target);
        target.setPhone(codec.encryptOrNull(source.getPhone()));
        target.setPhoneHash(codec.phoneHashOrNull(source.getPhone()));
        target.setIdCard(codec.encryptOrNull(source.getIdCard()));
        target.setIdCardMask(codec.idCardMaskOrNull(source.getIdCard()));
        return target;
    }
}
