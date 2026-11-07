package com.taxi.application.job;

import com.taxi.application.service.AlarmAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 报警超时升级任务：每分钟扫描未处置报警，一级 10 分钟升二级，二级 5 分钟升三级。
 * <p>阈值可用 alarm.escalate.level1-seconds / level2-seconds 覆盖。</p>
 */
@Component
public class AlarmEscalationJob {

    private static final Logger log = LoggerFactory.getLogger(AlarmEscalationJob.class);

    private final AlarmAppService alarmAppService;

    public AlarmEscalationJob(AlarmAppService alarmAppService) {
        this.alarmAppService = alarmAppService;
    }

    @Scheduled(fixedDelayString = "${alarm.escalate.scan-ms:60000}",
            initialDelayString = "${alarm.escalate.initial-ms:30000}")
    public void scan() {
        try {
            int count = alarmAppService.escalateDue();
            if (count > 0) {
                log.info("报警升级扫描完成，升级 {} 条", count);
            }
        } catch (RuntimeException e) {
            log.error("报警升级扫描失败", e);
        }
    }
}
