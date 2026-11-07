package com.taxi.domain.repository;

import com.taxi.domain.entity.DriverCertificate;

import java.time.LocalDate;
import java.util.List;

/**
 * 司机证件仓储。
 */
public interface DriverCertificateRepository {

    DriverCertificate findById(Long id);

    List<DriverCertificate> findByDriverId(Long driverId);

    void save(DriverCertificate certificate);

    void update(DriverCertificate certificate);

    void delete(Long id);

    /** 更新预警状态与最近预警时间（预警任务专用） */
    void updateWarnStatus(Long id, Integer warnStatus, java.time.LocalDateTime warnTime);

    /**
     * 到期日在 deadline（含）之前的证件，供预警任务扫描。
     * 已软删除的记录不返回。
     */
    List<DriverCertificate> findExpiringBefore(LocalDate deadline);
}
