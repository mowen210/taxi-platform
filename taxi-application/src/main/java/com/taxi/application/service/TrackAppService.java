package com.taxi.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.application.dto.TrackView;
import com.taxi.application.dto.VehiclePositionView;
import com.taxi.application.event.VehiclePositionEvent;
import com.taxi.application.support.ScopeGuard;
import com.taxi.application.support.TrackMath;
import com.taxi.common.constant.RedisKeys;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.DeviceBinding;
import com.taxi.domain.entity.Vehicle;
import com.taxi.domain.repository.DeviceBindingRepository;
import com.taxi.domain.repository.TrackRepository;
import com.taxi.domain.repository.VehicleRepository;
import com.taxi.domain.track.TrackPoint;
import com.taxi.infra.cache.TokenStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹与监控用例。
 *
 * <p>写入路径：0x0200 → {@link #record}（TDengine/MySQL + Redis 最新位置 + 事件）；
 * 查询路径：监控列表读 Redis（缺失回落仓储），轨迹回放读时序库并计算里程。</p>
 */
@Service
public class TrackAppService {

    private static final Logger log = LoggerFactory.getLogger(TrackAppService.class);
    private static final Duration POSITION_TTL = Duration.ofMinutes(30);
    private static final int MAX_VEHICLES = 500;
    private static final int MAX_TRACK_POINTS = 5000;

    private final TrackRepository trackRepository;
    private final VehicleRepository vehicleRepository;
    private final DeviceBindingRepository bindingRepository;
    private final StringRedisTemplate redisTemplate;
    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public TrackAppService(TrackRepository trackRepository,
                           VehicleRepository vehicleRepository,
                           DeviceBindingRepository bindingRepository,
                           StringRedisTemplate redisTemplate,
                           TokenStore tokenStore,
                           ObjectMapper objectMapper,
                           ApplicationEventPublisher eventPublisher) {
        this.trackRepository = trackRepository;
        this.vehicleRepository = vehicleRepository;
        this.bindingRepository = bindingRepository;
        this.redisTemplate = redisTemplate;
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 记录一个定位点。时序库失败只降级为日志，不影响 808 应答与 Redis 最新位置。
     */
    public void record(TrackPoint point) {
        if (point == null || !point.valid()) {
            return;
        }
        try {
            trackRepository.append(point);
        } catch (RuntimeException e) {
            log.warn("轨迹写入失败 provider={} vehicleId={} 原因={}",
                    trackRepository.provider(), point.vehicleId(), e.getMessage());
        }
        VehiclePositionView view = new VehiclePositionView(point.vehicleId(), point.plateNo(),
                point.terminalId(), 1, point.lng(), point.lat(), point.speed(), point.direction(),
                point.ts(), point.alarmFlag());
        try {
            redisTemplate.opsForValue().set(RedisKeys.vehiclePosition(point.vehicleId()),
                    objectMapper.writeValueAsString(view), POSITION_TTL);
        } catch (Exception e) {
            log.warn("最新位置缓存失败 vehicleId={} 原因={}", point.vehicleId(), e.getMessage());
        }
        eventPublisher.publishEvent(new VehiclePositionEvent(point));
    }

    /** 监控列表：当前数据范围内全部车辆，带最新位置与在线状态 */
    public List<VehiclePositionView> latestPositions(Long fleetId) {
        PageResult<Vehicle> page = vehicleRepository.search(null, fleetId, null, 1, MAX_VEHICLES);
        List<VehiclePositionView> result = new ArrayList<>(page.list().size());
        for (Vehicle vehicle : page.list()) {
            DeviceBinding binding = bindingRepository.findActiveByVehicle(vehicle.getId());
            String terminalId = binding == null ? null : binding.getTerminalId();
            boolean online = terminalId != null && tokenStore.hasSession(terminalId);
            VehiclePositionView cached = readCache(vehicle.getId());
            if (cached == null) {
                cached = trackRepository.latest(vehicle.getId())
                        .map(p -> new VehiclePositionView(vehicle.getId(), vehicle.getPlateNo(), p.terminalId(),
                                1, p.lng(), p.lat(), p.speed(), p.direction(), p.ts(), p.alarmFlag()))
                        .orElse(null);
            }
            if (cached == null) {
                result.add(new VehiclePositionView(vehicle.getId(), vehicle.getPlateNo(), terminalId,
                        online ? 1 : 0, null, null, null, null, null, 0L));
            } else {
                result.add(new VehiclePositionView(cached.vehicleId(), vehicle.getPlateNo(),
                        cached.terminalId(), online ? 1 : 0, cached.lng(), cached.lat(), cached.speed(),
                        cached.direction(), cached.ts(), cached.alarmFlag()));
            }
        }
        return result;
    }

    /** 监控快照 JSON，WebSocket 建连时下发 */
    public String latestJson(Long fleetId) {
        try {
            return objectMapper.writeValueAsString(latestPositions(fleetId));
        } catch (Exception e) {
            log.warn("监控快照序列化失败 原因={}", e.getMessage());
            return "[]";
        }
    }

    /** 轨迹回放：数据权限校验 + 里程/最高速度/时长汇总 */
    public TrackView track(Long vehicleId, LocalDateTime start, LocalDateTime end, Integer limit) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId);
        if (vehicle == null) {
            throw BizException.notFound("车辆不存在：id=" + vehicleId);
        }
        ScopeGuard.requireFleetReadable(vehicle.getFleetId());
        LocalDateTime to = end == null ? LocalDateTime.now().withNano(0) : end.withNano(0);
        LocalDateTime from = start == null ? to.minusHours(2) : start.withNano(0);
        if (from.isAfter(to)) {
            throw BizException.badRequest("开始时间不能晚于结束时间");
        }
        int max = limit == null ? 2000 : Math.max(1, Math.min(limit, MAX_TRACK_POINTS));
        List<TrackPoint> points = trackRepository.history(vehicleId, from, to, max);

        List<TrackView.Point> views = new ArrayList<>(points.size());
        int maxSpeed = 0;
        for (TrackPoint p : points) {
            views.add(new TrackView.Point(p.ts(), p.lng(), p.lat(), p.speed(), p.direction()));
            maxSpeed = Math.max(maxSpeed, p.speed());
        }
        double distance = Math.round(TrackMath.totalDistanceKm(points) * 1000d) / 1000d;
        long duration = 0;
        if (points.size() >= 2) {
            duration = Math.max(0, java.time.Duration.between(
                    points.get(0).ts(), points.get(points.size() - 1).ts()).getSeconds());
        }
        return new TrackView(vehicle.getId(), vehicle.getPlateNo(), trackRepository.provider(),
                from, to, views.size(), distance, maxSpeed, duration, views);
    }

    public String provider() {
        return trackRepository.provider();
    }

    private VehiclePositionView readCache(Long vehicleId) {
        try {
            String json = redisTemplate.opsForValue().get(RedisKeys.vehiclePosition(vehicleId));
            return json == null ? null : objectMapper.readValue(json, VehiclePositionView.class);
        } catch (Exception e) {
            log.debug("读取位置缓存失败 vehicleId={} 原因={}", vehicleId, e.getMessage());
            return null;
        }
    }
}
