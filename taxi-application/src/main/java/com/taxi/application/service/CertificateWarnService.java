package com.taxi.application.service;

import com.taxi.application.dto.WarnScanResult;
import com.taxi.application.support.ExpiryEvaluator;
import com.taxi.application.support.SmsSender;
import com.taxi.domain.entity.Driver;
import com.taxi.domain.entity.DriverCertificate;
import com.taxi.domain.entity.Notice;
import com.taxi.domain.entity.Vehicle;
import com.taxi.domain.repository.DriverCertificateRepository;
import com.taxi.domain.repository.DriverRepository;
import com.taxi.domain.repository.NoticeRepository;
import com.taxi.domain.repository.SysConfigRepository;
import com.taxi.domain.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 证件与车辆年检/保险到期预警。
 * <p>判定口径：到期日已过 → 过期(2)；剩余天数 ≤ 预警阈值 → 预警(1)；否则正常(0)。
 * 同一业务对象每天最多产生一条站内通知，避免定时任务刷屏。</p>
 */
@Service
public class CertificateWarnService {

    private static final Logger log = LoggerFactory.getLogger(CertificateWarnService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int DEFAULT_WARN_DAYS = 30;
    private static final String CONFIG_WARN_DAYS = "cert.warn.days";

    private final DriverCertificateRepository certificateRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final NoticeRepository noticeRepository;
    private final SysConfigRepository sysConfigRepository;
    private final SmsSender smsSender;

    public CertificateWarnService(DriverCertificateRepository certificateRepository,
                                  DriverRepository driverRepository,
                                  VehicleRepository vehicleRepository,
                                  NoticeRepository noticeRepository,
                                  SysConfigRepository sysConfigRepository,
                                  SmsSender smsSender) {
        this.certificateRepository = certificateRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.noticeRepository = noticeRepository;
        this.sysConfigRepository = sysConfigRepository;
        this.smsSender = smsSender;
    }

    /** 预警阈值天数，取系统参数 cert.warn.days，缺省 30 天 */
    public int warnDays() {
        String value = sysConfigRepository.resolveValue(CONFIG_WARN_DAYS, null, null);
        if (value == null || value.isBlank()) {
            return DEFAULT_WARN_DAYS;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? Math.min(parsed, 365) : DEFAULT_WARN_DAYS;
        } catch (NumberFormatException e) {
            log.warn("系统参数 {} 非法：{}，回退默认 {} 天", CONFIG_WARN_DAYS, value, DEFAULT_WARN_DAYS);
            return DEFAULT_WARN_DAYS;
        }
    }

    /** 计算预警状态：0 正常 1 预警 2 过期 */
    public int evaluateWarnStatus(LocalDate expireDate, int warnDays) {
        return ExpiryEvaluator.warnStatus(expireDate, warnDays, LocalDate.now());
    }

    /**
     * 执行一次扫描：刷新证件预警状态、生成站内通知、按需发短信。
     * <p>无登录态，可由定时任务或运维手动触发接口调用。</p>
     */
    public WarnScanResult scan() {
        int warnDays = warnDays();
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(warnDays);
        LocalDateTime now = LocalDateTime.now();

        int scannedCerts = 0;
        int warned = 0;
        int expired = 0;
        int notices = 0;
        int smsSent = 0;
        int smsSkipped = 0;

        List<DriverCertificate> certificates = certificateRepository.findExpiringBefore(deadline);
        for (DriverCertificate cert : certificates) {
            scannedCerts++;
            int status = evaluateWarnStatus(cert.getExpireDate(), warnDays);
            if (status == 1) {
                warned++;
            } else if (status == 2) {
                expired++;
            }
            certificateRepository.updateWarnStatus(cert.getId(), status, now);

            boolean warnedToday = noticeRepository.existsSince("CERT", "CERTIFICATE",
                    String.valueOf(cert.getId()), today.atStartOfDay());
            if (warnedToday) {
                continue;
            }
            Driver driver = driverRepository.findById(cert.getDriverId());
            if (driver == null) {
                continue;
            }
            long daysLeft = ChronoUnit.DAYS.between(today, cert.getExpireDate());
            String state = status == 2 ? "已过期" : "即将到期";
            String title = truncate("司机证件" + state + "：" + driver.getName(), 128);
            String content = truncate(String.format("%s（%s）的%s（证号 %s）于 %s %s，剩余 %d 天，请及时办理换证。",
                    driver.getName(), driver.getDriverCode(), certTypeName(cert.getCertType()),
                    cert.getCertNo() == null ? "-" : cert.getCertNo(), cert.getExpireDate().format(DATE),
                    state, daysLeft), 1000);

            noticeRepository.save(buildNotice("CERT", "SITE", "D:" + driver.getId(),
                    "CERTIFICATE", String.valueOf(cert.getId()), title, content, 1, now));
            notices++;

            if (driver.getPhone() != null && !driver.getPhone().isBlank()) {
                if (smsSender.send(driver.getPhone(), content)) {
                    smsSent++;
                    noticeRepository.save(buildNotice("CERT", "SMS", "D:" + driver.getId(),
                            "CERTIFICATE", String.valueOf(cert.getId()), title, content, 1, now));
                } else {
                    noticeRepository.save(buildNotice("CERT", "SMS", "D:" + driver.getId(),
                            "CERTIFICATE", String.valueOf(cert.getId()), title, content, 2, now));
                }
                notices++;
            } else {
                smsSkipped++;
            }
        }

        List<Vehicle> vehicles = vehicleRepository.findExpiringBefore(deadline);
        for (Vehicle vehicle : vehicles) {
            String vehicleNotice = vehicleNotice(vehicle, today, warnDays);
            if (vehicleNotice == null) {
                continue;
            }
            if (noticeRepository.existsSince("CERT", "VEHICLE", String.valueOf(vehicle.getId()),
                    today.atStartOfDay())) {
                continue;
            }
            noticeRepository.save(buildNotice("CERT", "SITE", "V:" + vehicle.getId(), "VEHICLE",
                    String.valueOf(vehicle.getId()), truncate("车辆证照临期：" + vehicle.getPlateNo(), 128),
                    vehicleNotice, 1, now));
            notices++;
        }

        log.info("到期预警完成：证件扫描 {} 张（预警 {}，过期 {}），车辆 {} 辆，通知 {} 条，短信 {} 条",
                scannedCerts, warned, expired, vehicles.size(), notices, smsSent);
        return new WarnScanResult(scannedCerts, warned, expired, vehicles.size(), notices, smsSent, smsSkipped);
    }

    private String vehicleNotice(Vehicle vehicle, LocalDate today, int warnDays) {
        StringBuilder sb = new StringBuilder();
        appendDateWarning(sb, "交强险", vehicle.getInsuranceExpireDate(), today, warnDays);
        appendDateWarning(sb, "年检", vehicle.getAnnualInspectExpireDate(), today, warnDays);
        return sb.isEmpty() ? null : sb.toString();
    }

    private void appendDateWarning(StringBuilder sb, String label, LocalDate date, LocalDate today, int warnDays) {
        if (date == null || date.isAfter(today.plusDays(warnDays))) {
            return;
        }
        long daysLeft = ChronoUnit.DAYS.between(today, date);
        sb.append(label).append(' ').append(date.format(DATE))
                .append(daysLeft < 0 ? "（已过期 " + (-daysLeft) + " 天）" : "（剩余 " + daysLeft + " 天）")
                .append('；');
    }

    private Notice buildNotice(String noticeType, String channel, String receiver, String bizType,
                               String bizId, String title, String content, int sendStatus, LocalDateTime sendTime) {
        Notice notice = new Notice();
        notice.setNoticeType(noticeType);
        notice.setChannel(channel);
        notice.setReceiver(receiver);
        notice.setBizType(bizType);
        notice.setBizId(bizId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setSendStatus(sendStatus);
        notice.setSendTime(sendTime);
        return notice;
    }

    private String certTypeName(Integer certType) {
        if (certType == null) {
            return "证件";
        }
        return switch (certType) {
            case 1 -> "驾驶证";
            case 2 -> "从业资格证";
            case 3 -> "身份证";
            case 4 -> "健康证";
            default -> "证件";
        };
    }

    private String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
