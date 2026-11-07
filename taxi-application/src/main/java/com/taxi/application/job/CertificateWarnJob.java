package com.taxi.application.job;

import com.taxi.application.dto.WarnScanResult;
import com.taxi.application.service.CertificateWarnService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 证件到期预警定时任务。
 * <p>默认每日 08:00 执行，cron 可通过 {@code job.cert-warn.cron} 覆盖；
 * 无登录态运行，异常只记录日志不影响应用。</p>
 */
@Component
public class CertificateWarnJob {

    private static final Logger log = LoggerFactory.getLogger(CertificateWarnJob.class);

    private final CertificateWarnService warnService;

    public CertificateWarnJob(CertificateWarnService warnService) {
        this.warnService = warnService;
    }

    @Scheduled(cron = "${job.cert-warn.cron:0 0 8 * * ?}")
    public void run() {
        try {
            WarnScanResult result = warnService.scan();
            log.info("定时到期扫描完成：{}", result);
        } catch (RuntimeException e) {
            log.error("定时到期扫描失败", e);
        }
    }
}
