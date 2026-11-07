package com.taxi.application.service;

import com.taxi.application.dto.CertificateSaveRequest;
import com.taxi.application.dto.CertificateView;
import com.taxi.application.dto.DriverSaveRequest;
import com.taxi.application.dto.DriverView;
import com.taxi.application.support.ScopeGuard;
import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.crypto.MaskUtil;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Driver;
import com.taxi.domain.entity.DriverCertificate;
import com.taxi.domain.entity.Fleet;
import com.taxi.domain.repository.DriverCertificateRepository;
import com.taxi.domain.repository.DriverRepository;
import com.taxi.domain.repository.FleetRepository;
import com.taxi.domain.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 司机档案与证件用例。
 * <p>驾驶证/从业资格证在司机表上有冗余到期日，保存证件时同步回写，保证列表与预警口径一致。</p>
 */
@Service
public class DriverAppService {

    private static final Logger log = LoggerFactory.getLogger(DriverAppService.class);

    /** 默认提前预警天数，可被系统参数 cert.warn.days 覆盖 */
    private static final int DEFAULT_WARN_DAYS = 30;

    private final DriverRepository driverRepository;
    private final DriverCertificateRepository certificateRepository;
    private final FleetRepository fleetRepository;
    private final VehicleRepository vehicleRepository;
    private final CertificateWarnService warnService;

    public DriverAppService(DriverRepository driverRepository,
                            DriverCertificateRepository certificateRepository,
                            FleetRepository fleetRepository,
                            VehicleRepository vehicleRepository,
                            CertificateWarnService warnService) {
        this.driverRepository = driverRepository;
        this.certificateRepository = certificateRepository;
        this.fleetRepository = fleetRepository;
        this.vehicleRepository = vehicleRepository;
        this.warnService = warnService;
    }

    public PageResult<DriverView> page(String keyword, Long fleetId, Integer status, long page, long size) {
        long safePage = Math.max(1L, page);
        long safeSize = Math.min(Math.max(1L, size), 100L);
        Long effectiveFleet = ScopeGuard.fleetFilter(fleetId);
        PageResult<Driver> result = driverRepository.search(keyword, effectiveFleet, status,
                (int) safePage, (int) safeSize);
        return PageResult.of(result.total(), result.page(), result.size(), toViews(result.list()));
    }

    public DriverView detail(Long id) {
        Driver driver = findOrThrow(id);
        ScopeGuard.requireFleetReadable(driver.getFleetId());
        return toViews(List.of(driver)).get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(DriverSaveRequest request) {
        String code = normalizeCode(request.driverCode());
        if (driverRepository.findByCode(code) != null) {
            throw BizException.badRequest("司机编号已存在：" + code);
        }
        Long fleetId = resolveFleetId(request.fleetId());
        checkFleetExists(fleetId);

        Driver driver = new Driver();
        driver.setDriverCode(code);
        driver.setName(request.name().trim());
        driver.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        driver.setIdCard(StringUtils.hasText(request.idCard()) ? request.idCard().trim() : null);
        driver.setGender(request.gender());
        driver.setLicenseNo(request.licenseNo());
        driver.setLicenseClass(request.licenseClass());
        driver.setLicenseExpireDate(request.licenseExpireDate());
        driver.setQualificationNo(request.qualificationNo());
        driver.setQualificationExpireDate(request.qualificationExpireDate());
        driver.setFleetId(fleetId);
        driver.setOnlineStatus(0);
        driver.setStatus(request.status() == null ? 1 : request.status());
        driver.setEntryDate(request.entryDate());
        driver.setRemark(request.remark());
        driverRepository.save(driver);
        log.info("新增司机 id={} code={}", driver.getId(), code);
        return driver.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DriverSaveRequest request) {
        Driver existing = findOrThrow(id);
        ScopeGuard.requireFleetWritable(existing.getFleetId());

        String code = normalizeCode(request.driverCode());
        Driver byCode = driverRepository.findByCode(code);
        if (byCode != null && !byCode.getId().equals(id)) {
            throw BizException.badRequest("司机编号已存在：" + code);
        }
        Long fleetId = resolveFleetId(request.fleetId());
        checkFleetExists(fleetId);

        Driver patch = new Driver();
        patch.setId(id);
        patch.setDriverCode(code);
        patch.setName(request.name().trim());
        patch.setGender(request.gender());
        patch.setLicenseNo(request.licenseNo());
        patch.setLicenseClass(request.licenseClass());
        patch.setLicenseExpireDate(request.licenseExpireDate());
        patch.setQualificationNo(request.qualificationNo());
        patch.setQualificationExpireDate(request.qualificationExpireDate());
        patch.setFleetId(fleetId);
        patch.setStatus(request.status());
        patch.setEntryDate(request.entryDate());
        patch.setRemark(request.remark());
        if (StringUtils.hasText(request.phone())) {
            patch.setPhone(request.phone().trim());
        }
        if (StringUtils.hasText(request.idCard())) {
            patch.setIdCard(request.idCard().trim());
        }
        driverRepository.update(patch);
        log.warn("修改司机 id={} code={} operator={}", id, code, UserContext.currentUsername());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Driver existing = findOrThrow(id);
        ScopeGuard.requireFleetWritable(existing.getFleetId());
        long vehicles = vehicleRepository.countByDriver(id);
        if (vehicles > 0) {
            throw BizException.badRequest("司机仍绑定 " + vehicles + " 辆车，请先更换默认司机");
        }
        for (DriverCertificate cert : certificateRepository.findByDriverId(id)) {
            certificateRepository.delete(cert.getId());
        }
        driverRepository.delete(id);
        log.warn("删除司机 id={} code={} operator={}", id, existing.getDriverCode(), UserContext.currentUsername());
    }

    public List<CertificateView> certificates(Long driverId) {
        Driver driver = findOrThrow(driverId);
        ScopeGuard.requireFleetReadable(driver.getFleetId());
        int warnDays = warnService.warnDays();
        LocalDate today = LocalDate.now();
        return certificateRepository.findByDriverId(driverId).stream()
                .map(c -> new CertificateView(c.getId(), c.getDriverId(), driver.getName(), c.getCertType(),
                        certTypeName(c.getCertType()), c.getCertNo(), c.getIssueDate(), c.getExpireDate(),
                        c.getExpireDate() == null ? null : ChronoUnit.DAYS.between(today, c.getExpireDate()),
                        c.getFileUrl(), c.getWarnStatus(), c.getLastWarnTime()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addCertificate(Long driverId, CertificateSaveRequest request) {
        Driver driver = findOrThrow(driverId);
        ScopeGuard.requireFleetWritable(driver.getFleetId());
        validateDates(request);
        String certNo = StringUtils.hasText(request.certNo()) ? request.certNo().trim() : null;
        boolean duplicated = certificateRepository.findByDriverId(driverId).stream()
                .anyMatch(c -> c.getCertType().equals(request.certType())
                        && ((certNo == null && c.getCertNo() == null)
                        || (certNo != null && certNo.equals(c.getCertNo()))));
        if (duplicated) {
            throw BizException.badRequest("同一司机该类型证件已存在，请修改原记录");
        }

        DriverCertificate cert = new DriverCertificate();
        cert.setDriverId(driverId);
        cert.setCertType(request.certType());
        cert.setCertNo(certNo);
        cert.setIssueDate(request.issueDate());
        cert.setExpireDate(request.expireDate());
        cert.setFileUrl(request.fileUrl());
        cert.setWarnStatus(warnService.evaluateWarnStatus(request.expireDate(), warnService.warnDays()));
        cert.setLastWarnTime(cert.getWarnStatus() > 0 ? LocalDateTime.now() : null);
        certificateRepository.save(cert);
        syncDriverExpireDate(driver, request.certType(), request.expireDate());
        return cert.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCertificate(Long driverId, Long certId, CertificateSaveRequest request) {
        Driver driver = findOrThrow(driverId);
        ScopeGuard.requireFleetWritable(driver.getFleetId());
        DriverCertificate existing = certificateRepository.findById(certId);
        if (existing == null || !existing.getDriverId().equals(driverId)) {
            throw BizException.notFound("证件不存在：id=" + certId);
        }
        validateDates(request);

        DriverCertificate patch = new DriverCertificate();
        patch.setId(certId);
        patch.setCertType(request.certType());
        patch.setCertNo(StringUtils.hasText(request.certNo()) ? request.certNo().trim() : null);
        patch.setIssueDate(request.issueDate());
        patch.setExpireDate(request.expireDate());
        patch.setFileUrl(request.fileUrl());
        patch.setWarnStatus(warnService.evaluateWarnStatus(request.expireDate(), warnService.warnDays()));
        certificateRepository.update(patch);
        syncDriverExpireDate(driver, request.certType(), request.expireDate());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCertificate(Long driverId, Long certId) {
        Driver driver = findOrThrow(driverId);
        ScopeGuard.requireFleetWritable(driver.getFleetId());
        DriverCertificate existing = certificateRepository.findById(certId);
        if (existing == null || !existing.getDriverId().equals(driverId)) {
            throw BizException.notFound("证件不存在：id=" + certId);
        }
        certificateRepository.delete(certId);
    }

    public Driver findOrThrow(Long id) {
        Driver driver = driverRepository.findById(id);
        if (driver == null) {
            throw BizException.notFound("司机不存在：id=" + id);
        }
        return driver;
    }

    private void validateDates(CertificateSaveRequest request) {
        if (request.issueDate() != null && request.expireDate().isBefore(request.issueDate())) {
            throw BizException.badRequest("到期日期不能早于发证日期");
        }
    }

    /** 证件到期日回写到司机档案的冗余字段 */
    private void syncDriverExpireDate(Driver driver, Integer certType, LocalDate expireDate) {
        Driver patch = new Driver();
        patch.setId(driver.getId());
        if (certType == 1) {
            patch.setLicenseExpireDate(expireDate);
        } else if (certType == 2) {
            patch.setQualificationExpireDate(expireDate);
        } else {
            return;
        }
        driverRepository.update(patch);
    }

    private List<DriverView> toViews(List<Driver> drivers) {
        Map<Long, String> fleetNames = new HashMap<>();
        return drivers.stream().map(d -> {
            String fleetName = null;
            if (d.getFleetId() != null) {
                fleetName = fleetNames.computeIfAbsent(d.getFleetId(), fid -> {
                    Fleet fleet = fleetRepository.findById(fid);
                    return fleet == null ? null : fleet.getFleetName();
                });
            }
            return new DriverView(d.getId(), d.getDriverCode(), d.getName(), MaskUtil.maskPhone(d.getPhone()),
                    d.getIdCardMask(), d.getGender(), d.getLicenseNo(), d.getLicenseClass(),
                    d.getLicenseExpireDate(), d.getQualificationNo(), d.getQualificationExpireDate(),
                    d.getFleetId(), fleetName, d.getOnlineStatus(), d.getSafetyScore(), d.getStatus(),
                    d.getEntryDate(), d.getRemark(), d.getCreateTime());
        }).toList();
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private Long resolveFleetId(Long requested) {
        LoginUser current = UserContext.require();
        if (current.isGlobalScope()) {
            return requested;
        }
        Long own = current.fleetId();
        if (own == null) {
            throw BizException.forbidden("当前账号未绑定车队，无法维护司机");
        }
        if (requested != null && !requested.equals(own)) {
            throw BizException.forbidden("无权把司机放到其他车队");
        }
        return own;
    }

    private void checkFleetExists(Long fleetId) {
        if (fleetId != null && fleetRepository.findById(fleetId) == null) {
            throw BizException.badRequest("车队不存在：id=" + fleetId);
        }
    }

    private String certTypeName(Integer certType) {
        if (certType == null) {
            return "未知";
        }
        return switch (certType) {
            case 1 -> "驾驶证";
            case 2 -> "从业资格证";
            case 3 -> "身份证";
            case 4 -> "健康证";
            default -> "其他";
        };
    }

    /** 临期/过期证件总览，供预警页与首页看板使用 */
    public List<CertificateView> expiring(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        LocalDate today = LocalDate.now();
        Long scopeFleet = ScopeGuard.fleetFilter(null);
        Map<Long, Driver> driverCache = new HashMap<>();
        List<CertificateView> views = new java.util.ArrayList<>();
        for (DriverCertificate c : certificateRepository.findExpiringBefore(today.plusDays(safeDays))) {
            Driver driver = driverCache.computeIfAbsent(c.getDriverId(), driverRepository::findById);
            if (driver == null) {
                continue;
            }
            if (scopeFleet != null && !scopeFleet.equals(driver.getFleetId())) {
                continue;
            }
            views.add(new CertificateView(c.getId(), c.getDriverId(), driver.getName(), c.getCertType(),
                    certTypeName(c.getCertType()), c.getCertNo(), c.getIssueDate(), c.getExpireDate(),
                    ChronoUnit.DAYS.between(today, c.getExpireDate()), c.getFileUrl(),
                    c.getWarnStatus(), c.getLastWarnTime()));
        }
        return views;
    }

    /** 供预警任务使用的默认预警天数 */
    public int defaultWarnDays() {
        return DEFAULT_WARN_DAYS;
    }
}
