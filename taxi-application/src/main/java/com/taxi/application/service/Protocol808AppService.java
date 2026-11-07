package com.taxi.application.service;

import com.taxi.application.dto.ProtocolResult;
import com.taxi.common.constant.RedisKeys;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;
import com.taxi.domain.entity.DeviceBinding;
import com.taxi.domain.entity.Order;
import com.taxi.domain.entity.TerminalDevice;
import com.taxi.domain.entity.Vehicle;
import com.taxi.domain.repository.DeviceBindingRepository;
import com.taxi.domain.repository.TerminalDeviceRepository;
import com.taxi.domain.repository.VehicleRepository;
import com.taxi.domain.track.TrackPoint;
import com.taxi.infra.persistence.raw.TaximeterRawEntry;
import com.taxi.infra.persistence.raw.TaximeterRawWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 808 接入用例：注册、鉴权、心跳、位置、905 营运报文。
 *
 * <p>本类不依赖 Netty，只处理「已解析出业务字段」的调用；Netty 负责拆帧与回包。
 * 鉴权态存 Redis（terminal:session），过期后终端需重新 0x0102。</p>
 */
@Service
public class Protocol808AppService {

    private static final Logger log = LoggerFactory.getLogger(Protocol808AppService.class);
    private static final String AUTH_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration LOCATION_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final TerminalDeviceRepository terminalDeviceRepository;
    private final DeviceBindingRepository bindingRepository;
    private final VehicleRepository vehicleRepository;
    private final OrderAppService orderAppService;
    private final TrackAppService trackAppService;
    private final TaximeterRawWriter rawWriter;
    private final AlarmAppService alarmAppService;
    private final MediaAppService mediaAppService;

    @Value("${protocol.auth-ttl:7d}")
    private Duration authTtl = Duration.ofDays(7);

    @Value("${protocol.session-ttl:5m}")
    private Duration sessionTtl = Duration.ofMinutes(5);

    public Protocol808AppService(StringRedisTemplate redisTemplate,
                                 TerminalDeviceRepository terminalDeviceRepository,
                                 DeviceBindingRepository bindingRepository,
                                 VehicleRepository vehicleRepository,
                                 OrderAppService orderAppService,
                                 TrackAppService trackAppService,
                                 TaximeterRawWriter rawWriter,
                                 AlarmAppService alarmAppService,
                                 MediaAppService mediaAppService) {
        this.redisTemplate = redisTemplate;
        this.terminalDeviceRepository = terminalDeviceRepository;
        this.bindingRepository = bindingRepository;
        this.vehicleRepository = vehicleRepository;
        this.orderAppService = orderAppService;
        this.trackAppService = trackAppService;
        this.rawWriter = rawWriter;
        this.alarmAppService = alarmAppService;
        this.mediaAppService = mediaAppService;
    }

    /** 0x0100 注册：校验终端与车辆备案，下发鉴权码 */
    public ProtocolResult register(String terminalId, String manufacturer, String model,
                                   int plateColor, String plateNo) {
        TerminalDevice device = terminalDeviceRepository.findByTerminalId(terminalId);
        if (device == null) {
            log.warn("终端注册失败，档案不存在 terminal={}", terminalId);
            return ProtocolResult.fail(2, "数据库中无该终端：" + terminalId);
        }
        DeviceBinding binding = bindingRepository.findActiveByTerminal(terminalId);
        if (binding == null) {
            log.warn("终端注册失败，未绑定车辆 terminal={}", terminalId);
            return ProtocolResult.fail(2, "终端未绑定车辆：" + terminalId);
        }
        Vehicle vehicle = vehicleRepository.findById(binding.getVehicleId());
        if (vehicle == null) {
            return ProtocolResult.fail(2, "绑定车辆不存在：id=" + binding.getVehicleId());
        }
        if (StringUtils.hasText(plateNo) && !plateNo.equalsIgnoreCase(vehicle.getPlateNo())) {
            log.warn("终端注册失败，车牌不一致 terminal={} 上报={} 备案={}", terminalId, plateNo, vehicle.getPlateNo());
            return ProtocolResult.fail(2, "车牌与备案不一致：" + plateNo);
        }

        String authCode = randomAuthCode();
        TerminalDevice patch = new TerminalDevice();
        patch.setId(device.getId());
        patch.setAuthCode(authCode);
        patch.setRegisterTime(now());
        patch.setOnlineStatus(0);
        if (StringUtils.hasText(manufacturer)) {
            patch.setManufacturerId(manufacturer);
        }
        if (StringUtils.hasText(model)) {
            patch.setTerminalModel(model);
        }
        terminalDeviceRepository.update(patch);
        redisTemplate.opsForValue().set(RedisKeys.terminalAuth(terminalId), authCode, authTtl);
        log.info("终端注册成功 terminal={} plate={} authCode 已下发（有效期 {}）", terminalId, vehicle.getPlateNo(), authTtl);
        return ProtocolResult.ok(authCode, "注册成功");
    }

    /** 0x0102 鉴权：校验鉴权码并建立在线上会话 */
    public ProtocolResult auth(String terminalId, String authCode, String channelKey) {
        TerminalDevice device = terminalDeviceRepository.findByTerminalId(terminalId);
        if (device == null) {
            return ProtocolResult.fail("终端未备案：" + terminalId);
        }
        String expected = redisTemplate.opsForValue().get(RedisKeys.terminalAuth(terminalId));
        if (!StringUtils.hasText(expected)) {
            expected = device.getAuthCode();
        }
        if (!StringUtils.hasText(authCode) || !authCode.equals(expected)) {
            log.warn("终端鉴权失败 terminal={} 鉴权码不匹配", terminalId);
            return ProtocolResult.fail("鉴权码错误");
        }
        DeviceBinding binding = bindingRepository.findActiveByTerminal(terminalId);
        Long vehicleId = binding == null ? null : binding.getVehicleId();
        redisTemplate.opsForValue().set(RedisKeys.terminalSession(terminalId),
                channelKey + "|" + (vehicleId == null ? "" : vehicleId), sessionTtl);

        TerminalDevice patch = new TerminalDevice();
        patch.setId(device.getId());
        patch.setOnlineStatus(1);
        patch.setLastOnlineTime(now());
        terminalDeviceRepository.update(patch);
        log.info("终端鉴权成功 terminal={} vehicleId={}", terminalId, vehicleId);
        return ProtocolResult.ok("鉴权成功");
    }

    /** 0x0002 心跳：刷新会话与在线时间 */
    public ProtocolResult heartbeat(String terminalId, String channelKey) {
        if (!isAuthenticated(terminalId)) {
            return ProtocolResult.fail("终端未鉴权，请先发送 0x0102");
        }
        redisTemplate.expire(RedisKeys.terminalSession(terminalId), sessionTtl.toSeconds(), TimeUnit.SECONDS);
        TerminalDevice device = terminalDeviceRepository.findByTerminalId(terminalId);
        if (device != null) {
            TerminalDevice patch = new TerminalDevice();
            patch.setId(device.getId());
            patch.setOnlineStatus(1);
            patch.setLastHeartbeatTime(now());
            terminalDeviceRepository.update(patch);
        }
        redisTemplate.opsForValue().set(RedisKeys.terminalSession(terminalId) + ":hb",
                channelKey, sessionTtl);
        return ProtocolResult.ok("心跳正常");
    }

    /** 0x0200 位置汇报：写轨迹库 + 最新位置缓存 + 推送事件，报警位本阶段只记日志 */
    public ProtocolResult location(String terminalId, BigDecimal lng, BigDecimal lat, int speed,
                                   int direction, int altitude, long alarm, long status, LocalDateTime time,
                                   String rawHex) {
        if (!isAuthenticated(terminalId)) {
            return ProtocolResult.fail("终端未鉴权，请先发送 0x0102");
        }
        String value = lng + "|" + lat + "|" + speed + "|" + direction + "|" + now() + "|" + alarm;
        redisTemplate.opsForValue().set(RedisKeys.terminalLocation(terminalId), value, LOCATION_TTL);
        if (alarm != 0) {
            log.warn("终端上报报警位 terminal={} alarm=0x{} lng={} lat={}", terminalId,
                    Long.toHexString(alarm), lng, lat);
        }
        DeviceBinding binding = bindingRepository.findActiveByTerminal(terminalId);
        if (binding != null) {
            Vehicle vehicle = vehicleRepository.findById(binding.getVehicleId());
            if (vehicle != null) {
                trackAppService.record(new TrackPoint(vehicle.getId(), binding.getTerminalId(),
                        vehicle.getPlateNo(), time == null ? now() : time.withNano(0), lng, lat,
                        speed, direction, altitude, null, (int) (status & 0x01L), alarm, status, null));
                try {
                    alarmAppService.ingestLocation(terminalId, vehicle.getId(), vehicle.getFleetId(), alarm,
                            lng, lat, speed, time, rawHex);
                } catch (RuntimeException e) {
                    log.error("位置报警落单失败 terminal={} alarm=0x{}", terminalId, Long.toHexString(alarm), e);
                }
            }
        }
        return ProtocolResult.ok("位置已接收");
    }

    /** 0x0B02 营运开始：落单并流转至接到乘客 */
    public ProtocolResult operationStart(String terminalId, String businessId, LocalDateTime startTime,
                                         BigDecimal lng, BigDecimal lat, int msgSeq, String rawHex) {
        DeviceBinding binding = bindingRepository.findActiveByTerminal(terminalId);
        Long vehicleId = binding == null ? null : binding.getVehicleId();
        String taximeterSn = binding == null ? null : binding.getTaximeterSn();
        String entryTerminal = binding == null ? terminalId : binding.getTerminalId();
        LocalDateTime reportTime = now();
        try {
            if (!isAuthenticated(terminalId)) {
                throw BizException.badRequest("终端未鉴权，请先发送 0x0102");
            }
            Order order = orderAppService.ingestOperationStart(businessId, terminalId, startTime, lng, lat);
            safeWrite(new TaximeterRawEntry(order.getOrderNo(), entryTerminal, vehicleId, taximeterSn, 1, msgSeq,
                    businessId, startTime, null, lng, lat, null, null, null, null, null,
                    rawHex, 1, null, reportTime));
            return ProtocolResult.ok("营运开始已落单：" + order.getOrderNo());
        } catch (RuntimeException e) {
            safeWrite(new TaximeterRawEntry(null, entryTerminal, vehicleId, taximeterSn, 1, msgSeq,
                    businessId, startTime, null, lng, lat, null, null, null, null, null,
                    rawHex, 2, truncate(e.getMessage()), reportTime));
            log.warn("0x0B02 处理失败 terminal={} businessId={}：{}", terminalId, businessId, e.getMessage());
            return ProtocolResult.fail(e.getMessage());
        }
    }

    /** 0x0B05 营运结束：结束行程并核算金额；无对应订单时补建异常单 */
    public ProtocolResult operationEnd(String terminalId, String businessId, LocalDateTime endTime,
                                       BigDecimal lng, BigDecimal lat, BigDecimal mileageKm,
                                       int waitingSeconds, BigDecimal totalAmount, BigDecimal discountAmount,
                                       int msgSeq, String rawHex) {
        DeviceBinding binding = bindingRepository.findActiveByTerminal(terminalId);
        Long vehicleId = binding == null ? null : binding.getVehicleId();
        String taximeterSn = binding == null ? null : binding.getTaximeterSn();
        String entryTerminal = binding == null ? terminalId : binding.getTerminalId();
        LocalDateTime reportTime = now();
        try {
            if (!isAuthenticated(terminalId)) {
                throw BizException.badRequest("终端未鉴权，请先发送 0x0102");
            }
            Order order = orderAppService.ingestOperationEnd(businessId, terminalId, endTime, lng, lat,
                    mileageKm, waitingSeconds, totalAmount, discountAmount);
            safeWrite(new TaximeterRawEntry(order.getOrderNo(), entryTerminal, vehicleId, taximeterSn, 2, msgSeq,
                    businessId, null, endTime, null, null, lng, lat, totalAmount, mileageKm, waitingSeconds,
                    rawHex, 1, null, reportTime));
            return ProtocolResult.ok("营运结束已处理：" + order.getOrderNo() + " / " + order.getStatus());
        } catch (RuntimeException e) {
            safeWrite(new TaximeterRawEntry(null, entryTerminal, vehicleId, taximeterSn, 2, msgSeq,
                    businessId, null, endTime, null, null, lng, lat, totalAmount, mileageKm, waitingSeconds,
                    rawHex, 2, truncate(e.getMessage()), reportTime));
            log.warn("0x0B05 处理失败 terminal={} businessId={}：{}", terminalId, businessId, e.getMessage());
            return ProtocolResult.fail(e.getMessage());
        }
    }

    /** 0x0801 多媒体上传：鉴权、定位车辆、落盘并关联报警 */
    public ProtocolResult mediaUpload(String terminalId, long mediaId, int mediaType, int format,
                                      int event, int channel, byte[] data, LocalDateTime time) {
        if (!isAuthenticated(terminalId)) {
            return ProtocolResult.fail("终端未鉴权，请先发送 0x0102");
        }
        DeviceBinding binding = bindingRepository.findActiveByTerminal(terminalId);
        if (binding == null) {
            return ProtocolResult.fail("终端未绑定车辆");
        }
        try {
            mediaAppService.save808Media(terminalId, binding.getVehicleId(), mediaId, mediaType,
                    format, event, channel, data, time);
            return ProtocolResult.ok("多媒体已接收");
        } catch (RuntimeException e) {
            log.warn("0x0801 媒体处理失败 terminal={} mediaId={}：{}", terminalId, mediaId, e.getMessage());
            return ProtocolResult.fail(e.getMessage());
        }
    }

    /** 0x1210 报警附件信息 */
    public ProtocolResult attachmentInfo(String terminalId, String alarmKey, List<String> fileNames) {
        if (!isAuthenticated(terminalId)) {
            return ProtocolResult.fail("终端未鉴权，请先发送 0x0102");
        }
        mediaAppService.beginAttachment(terminalId, alarmKey, fileNames);
        return ProtocolResult.ok("附件信息已接收");
    }

    /** 0x1211 附件分片 */
    public ProtocolResult attachmentPacket(String terminalId, String fileName, long offset, byte[] data) {
        if (!isAuthenticated(terminalId)) {
            return ProtocolResult.fail("终端未鉴权，请先发送 0x0102");
        }
        mediaAppService.attachmentChunk(terminalId, fileName, offset, data);
        return ProtocolResult.ok("附件分片已接收");
    }

    /** 0x1212 附件完成 */
    public ProtocolResult attachmentComplete(String terminalId, String fileName, long size) {
        if (!isAuthenticated(terminalId)) {
            return ProtocolResult.fail("终端未鉴权，请先发送 0x0102");
        }
        mediaAppService.completeAttachment(terminalId, fileName, size);
        return ProtocolResult.ok("附件已归档");
    }

    /** 终端是否已鉴权（Redis 会话存在） */
    public boolean isAuthenticated(String terminalId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeys.terminalSession(terminalId)));
    }

    private void safeWrite(TaximeterRawEntry entry) {
        try {
            rawWriter.write(entry);
        } catch (RuntimeException e) {
            log.error("905 原始报文留痕失败 terminal={} businessId={}", entry.terminalId(), entry.businessId(), e);
        }
    }

    private String randomAuthCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(AUTH_CHARS.charAt(RANDOM.nextInt(AUTH_CHARS.length())));
        }
        return sb.toString();
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private LocalDateTime now() {
        return LocalDateTime.now().withNano(0);
    }
}
