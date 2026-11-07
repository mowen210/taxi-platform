package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taxi.domain.entity.DriverCertificate;
import com.taxi.domain.repository.DriverCertificateRepository;
import com.taxi.infra.persistence.mapper.DriverCertificateMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 司机证件仓储实现。
 */
@Repository
public class DriverCertificateRepositoryImpl implements DriverCertificateRepository {

    private final DriverCertificateMapper certificateMapper;

    public DriverCertificateRepositoryImpl(DriverCertificateMapper certificateMapper) {
        this.certificateMapper = certificateMapper;
    }

    @Override
    public DriverCertificate findById(Long id) {
        return certificateMapper.selectById(id);
    }

    @Override
    public List<DriverCertificate> findByDriverId(Long driverId) {
        return certificateMapper.selectList(Wrappers.<DriverCertificate>lambdaQuery()
                .eq(DriverCertificate::getDriverId, driverId)
                .orderByAsc(DriverCertificate::getExpireDate));
    }

    @Override
    public void save(DriverCertificate certificate) {
        certificateMapper.insert(certificate);
    }

    @Override
    public void update(DriverCertificate certificate) {
        certificateMapper.updateById(certificate);
    }

    @Override
    public void delete(Long id) {
        certificateMapper.deleteById(id);
    }

    @Override
    public List<DriverCertificate> findExpiringBefore(LocalDate deadline) {
        return certificateMapper.selectList(Wrappers.<DriverCertificate>lambdaQuery()
                .isNotNull(DriverCertificate::getExpireDate)
                .le(DriverCertificate::getExpireDate, deadline)
                .orderByAsc(DriverCertificate::getExpireDate));
    }

    /**
     * 更新预警状态与最近预警时间（预警任务专用，避免覆盖业务字段）。
     */
    public void updateWarnStatus(Long id, Integer warnStatus, LocalDateTime warnTime) {
        certificateMapper.update(null, Wrappers.<DriverCertificate>lambdaUpdate()
                .eq(DriverCertificate::getId, id)
                .set(DriverCertificate::getWarnStatus, warnStatus)
                .set(DriverCertificate::getLastWarnTime, warnTime));
    }
}
