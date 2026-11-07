package com.taxi.application.service;

import com.taxi.application.dto.AlarmHandleRequest;
import com.taxi.application.dto.AlarmView;
import com.taxi.application.dto.LiveView;
import com.taxi.application.event.AlarmEvent;
import com.taxi.application.port.TerminalCommandPort;
import com.taxi.application.support.ScopeGuard;
import com.taxi.common.enums.AlarmLevel;
import com.taxi.common.enums.AlarmType;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.alarm.AlarmRecord;
import com.taxi.domain.repository.AlarmRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 报警用例：808 报警位落单、三级处置、超时升级、联动抓拍与 1078 预览。
 *
 * <p>去重口径：同车同类型存在未结束（未处置/处置中）报警时，持续置位不重复建单；
 * 报警位消失后仍保留待处置记录。跨 30 分钟窗口才允许再建同类型单。</p>
 */
@Service
public class AlarmAppService {

    private static final Logger log = LoggerFactory.getLogger(AlarmAppService.class);
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final AlarmRepository alarmRepository;
    private final TerminalCommandPort terminalCommandPort;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${media.hls-base:http://127.0.0.1:8888}")
    private String hlsBase = "http://127.0.0.1:8888";

    @Value("${alarm.escalate.level1-seconds:600}")
    private long level1Seconds = 600;

    @Value("${alarm.escalate.level2-seconds:300}")
    private long level2Seconds = 300;

    public AlarmAppService(AlarmRepository alarmRepository,
                           TerminalCommandPort terminalCommandPort,
                           ApplicationEventPublisher eventPublisher) {
        this.alarmRepository = alarmRepository;
        this.terminalCommandPort = terminalCommandPort;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 0x0200 报警位落单。返回新建条数；同类型持续置位不重复建单。
     */
    public int ingestLocation(String terminalId, Long vehicleId, Long fleetId, long alarmFlag,
                              BigDecimal lng, BigDecimal lat, Integer speed,
                              LocalDateTime time, String rawHex) {
        if (alarmFlag == 0 || vehicleId == null) {
            return 0;
        }
        LocalDateTime alarmTime = time == null ? LocalDateTime.now().withNano(0) : time.withNano(0);
        int created = 0;
        for (int bit = 0; bit < 32; bit++) {
            if ((alarmFlag & (1L << bit)) == 0) {
                continue;
            }
            AlarmType type = AlarmType.ofBitIndex(bit);
            if (type == null) {
                log.debug("报警位 {} 无内部类型映射，忽略 vehicleId={}", bit, vehicleId);
                continue;
            }
            if (alarmRepository.existsOpenSince(vehicleId, type.name(), alarmTime.minusMinutes(30))) {
                continue;
            }
            Integer ruleLevel = alarmRepository.findRuleLevel(type.name(), vehicleId, fleetId);
            int level = ruleLevel == null ? type.getDefaultLevel().getCode() : ruleLevel;
            String flagHex = String.format("0x%08X", alarmFlag);
            AlarmRecord record = new AlarmRecord(null, nextAlarmNo(), type.name(), level, 1, flagHex,
                    null, null, vehicleId, null, terminalId, null, alarmTime, lng, lat,
                    speed == null ? null : BigDecimal.valueOf(speed), null, 0, null, null, null,
                    0, null, null, rawHex, null, fleetId);
            Long id = alarmRepository.insert(record);
            created++;
            AlarmRecord saved = alarmRepository.findById(id);
            if (saved != null) {
                eventPublisher.publishEvent(new AlarmEvent(toView(saved), false));
            }
            log.info("报警落单 vehicleId={} type={} level={} alarmNo={}", vehicleId, type.name(), level, record.alarmNo());
        }
        return created;
    }

    public PageResult<AlarmView> page(String alarmType, Integer level, Integer handleStatus, Long fleetId,
                                      LocalDateTime start, LocalDateTime end, int page, int size) {
        Long scope = ScopeGuard.fleetFilter(fleetId);
        PageResult<AlarmRecord> result = alarmRepository.page(alarmType, level, handleStatus, scope,
                start, end, page, size);
        List<AlarmView> views = new ArrayList<>(result.list().size());
        for (AlarmRecord record : result.list()) {
            views.add(toView(record));
        }
        return PageResult.of(result.total(), result.page(), result.size(), views);
    }

    public AlarmView detail(Long id) {
        AlarmRecord record = require(id);
        ScopeGuard.requireFleetReadable(record.fleetId());
        return toView(record);
    }

    /** 受理/处置/忽略。处置必须填写说明。 */
    @Transactional(rollbackFor = Exception.class)
    public AlarmView handle(Long id, AlarmHandleRequest request, Long userId) {
        AlarmRecord record = require(id);
        ScopeGuard.requireFleetWritable(record.fleetId());
        String action = request.action() == null ? "" : request.action().trim().toUpperCase();
        int from = record.handleStatus();
        int to;
        switch (action) {
            case "ACK" -> {
                if (from != 0) {
                    throw BizException.badRequest("仅未处置报警可受理");
                }
                to = 1;
            }
            case "HANDLE" -> {
                if (from != 0 && from != 1) {
                    throw BizException.badRequest("该报警已结束处置");
                }
                if (!StringUtils.hasText(request.remark())) {
                    throw BizException.badRequest("处置说明不能为空");
                }
                to = 2;
            }
            case "IGNORE" -> {
                if (from != 0 && from != 1) {
                    throw BizException.badRequest("该报警已结束处置");
                }
                if (!StringUtils.hasText(request.remark())) {
                    throw BizException.badRequest("忽略原因不能为空");
                }
                to = 3;
            }
            default -> throw BizException.badRequest("不支持的处置动作：" + request.action());
        }
        int updated = alarmRepository.changeHandleStatus(id, from, to, userId, request.remark());
        if (updated == 0) {
            throw BizException.badRequest("报警状态已变化，请刷新后重试");
        }
        return toView(require(id));
    }

    /** 0x8801 立即抓拍；终端离线直接报错，由前端提示。 */
    public boolean snapshot(Long id, int channel) {
        AlarmRecord record = require(id);
        ScopeGuard.requireFleetWritable(record.fleetId());
        if (!StringUtils.hasText(record.terminalId())) {
            throw BizException.badRequest("报警未关联终端，无法抓拍");
        }
        if (!terminalCommandPort.online(record.terminalId())) {
            throw BizException.badRequest("终端离线，抓拍指令未下发");
        }
        return terminalCommandPort.requestSnapshot(record.terminalId(), channel);
    }

    /** 0x9101 请求实时音视频，返回 MediaMTX HLS 地址。 */
    public LiveView live(Long id, int channel, int streamType) {
        AlarmRecord record = require(id);
        ScopeGuard.requireFleetWritable(record.fleetId());
        if (!StringUtils.hasText(record.terminalId())) {
            throw BizException.badRequest("报警未关联终端，无法预览");
        }
        if (!terminalCommandPort.online(record.terminalId())) {
            throw BizException.badRequest("终端离线，无法建立实时流");
        }
        boolean requested = terminalCommandPort.requestLive(record.terminalId(), channel, streamType);
        String url = hlsBase.replaceAll("/+$", "") + "/" + record.terminalId() + "/" + channel + "/index.m3u8";
        return new LiveView(id, record.terminalId(), channel, streamType, requested, url);
    }

    public void stopLive(Long id, int channel) {
        AlarmRecord record = require(id);
        ScopeGuard.requireFleetWritable(record.fleetId());
        terminalCommandPort.stopLive(record.terminalId(), channel);
    }

    /** 定时任务：未处置报警按级升级（一级→二级→三级），每次只升一级。 */
    public int escalateDue() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int count = 0;
        count += escalateLevel(1, 2, now.minusSeconds(level1Seconds));
        count += escalateLevel(2, 3, now.minusSeconds(level2Seconds));
        return count;
    }

    private int escalateLevel(int level, int nextLevel, LocalDateTime before) {
        List<AlarmRecord> due = alarmRepository.findEscalatable(level, before, 200);
        int count = 0;
        for (AlarmRecord record : due) {
            alarmRepository.escalate(record.id(), nextLevel, LocalDateTime.now().withNano(0));
            count++;
            AlarmRecord latest = alarmRepository.findById(record.id());
            if (latest != null) {
                eventPublisher.publishEvent(new AlarmEvent(toView(latest), true));
            }
            log.warn("报警超时升级 alarmNo={} {} -> {}", record.alarmNo(), level, nextLevel);
        }
        return count;
    }

    private AlarmRecord require(Long id) {
        AlarmRecord record = alarmRepository.findById(id);
        if (record == null) {
            throw BizException.notFound("报警不存在");
        }
        return record;
    }

    private String nextAlarmNo() {
        return "AL" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", SEQ.incrementAndGet() % 1000);
    }

    public static AlarmView toView(AlarmRecord r) {
        AlarmType type = AlarmType.of(r.alarmType());
        AlarmLevel level = AlarmLevel.of(r.alarmLevel());
        String statusDesc = switch (r.handleStatus()) {
            case 0 -> "未处置";
            case 1 -> "处置中";
            case 2 -> "已处置";
            case 3 -> "已忽略";
            default -> "未知";
        };
        return new AlarmView(r.id(), r.alarmNo(), r.alarmType(), type == null ? r.alarmType() : type.getDesc(),
                r.alarmLevel(), level.getDesc(), r.source(), r.alarmFlagHex(), r.bizAlarmId(),
                r.vehicleId(), r.plateNo(), r.fleetId(), r.driverId(), r.terminalId(), r.orderId(),
                r.alarmTime(), r.lng(), r.lat(), r.speed(), r.address(), r.handleStatus(), statusDesc,
                r.handleUserId(), r.handleTime(), r.handleRemark(), r.escalateFlag(), r.escalateTime());
    }
}
