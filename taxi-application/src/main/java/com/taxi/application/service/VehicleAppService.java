package com.taxi.application.service;

import com.taxi.application.dto.VehicleSaveRequest;
import com.taxi.application.dto.VehicleStatusRequest;
import com.taxi.application.dto.VehicleView;
import com.taxi.application.support.ScopeGuard;
import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.DeviceBinding;
import com.taxi.domain.entity.Driver;
import com.taxi.domain.entity.Fleet;
import com.taxi.domain.entity.Vehicle;
import com.taxi.domain.repository.DeviceBindingRepository;
import com.taxi.domain.repository.DriverRepository;
import com.taxi.domain.repository.FleetRepository;
import com.taxi.domain.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 车辆档案用例。
 */
@Service
public class VehicleAppService {

    private static final Logger log = LoggerFactory.getLogger(VehicleAppService.class);

    private final VehicleRepository vehicleRepository;
    private final FleetRepository fleetRepository;
    private final DriverRepository driverRepository;
    private final DeviceBindingRepository bindingRepository;

    public VehicleAppService(VehicleRepository vehicleRepository,
                             FleetRepository fleetRepository,
                             DriverRepository driverRepository,
                             DeviceBindingRepository bindingRepository) {
        this.vehicleRepository = vehicleRepository;
        this.fleetRepository = fleetRepository;
        this.driverRepository = driverRepository;
        this.bindingRepository = bindingRepository;
    }

    public PageResult<VehicleView> page(String keyword, Long fleetId, Integer status, long page, long size) {
        long safePage = Math.max(1L, page);
        long safeSize = Math.min(Math.max(1L, size), 100L);
        Long effectiveFleet = ScopeGuard.fleetFilter(fleetId);
        PageResult<Vehicle> result = vehicleRepository.search(keyword, effectiveFleet, status,
                (int) safePage, (int) safeSize);
        return PageResult.of(result.total(), result.page(), result.size(), toViews(result.list()));
    }

    public VehicleView detail(Long id) {
        Vehicle vehicle = findOrThrow(id);
        ScopeGuard.requireFleetReadable(vehicle.getFleetId());
        return toViews(List.of(vehicle)).get(0);
    }

    /** 保险 / 年检临期车辆，供预警页与定时任务复用 */
    public List<VehicleView> expiring(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        Long effectiveFleet = ScopeGuard.fleetFilter(null);
        LocalDate deadline = LocalDate.now().plusDays(safeDays);
        List<Vehicle> vehicles = vehicleRepository.findExpiringBefore(deadline).stream()
                .filter(v -> effectiveFleet == null || effectiveFleet.equals(v.getFleetId()))
                .toList();
        return toViews(vehicles);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(VehicleSaveRequest request) {
        String plateNo = normalizePlate(request.plateNo());
        if (vehicleRepository.findByPlateNo(plateNo) != null) {
            throw BizException.badRequest("车牌号已存在：" + plateNo);
        }
        Long fleetId = resolveFleetId(request.fleetId());
        checkFleetExists(fleetId);
        checkDriverExists(request.driverId());

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNo(plateNo);
        vehicle.setPlateColor(request.plateColor());
        vehicle.setVehicleType(request.vehicleType());
        vehicle.setVin(request.vin());
        vehicle.setEngineNo(request.engineNo());
        vehicle.setOwnerName(request.ownerName());
        vehicle.setFleetId(fleetId);
        vehicle.setDriverId(request.driverId());
        vehicle.setStatus(request.status() == null ? 1 : request.status());
        vehicle.setInsuranceExpireDate(request.insuranceExpireDate());
        vehicle.setAnnualInspectExpireDate(request.annualInspectExpireDate());
        vehicle.setPurchaseDate(request.purchaseDate());
        vehicle.setRemark(request.remark());
        vehicleRepository.save(vehicle);
        log.info("新增车辆 id={} plate={}", vehicle.getId(), plateNo);
        return vehicle.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, VehicleSaveRequest request) {
        Vehicle existing = findOrThrow(id);
        ScopeGuard.requireFleetWritable(existing.getFleetId());

        String plateNo = normalizePlate(request.plateNo());
        Vehicle byPlate = vehicleRepository.findByPlateNo(plateNo);
        if (byPlate != null && !byPlate.getId().equals(id)) {
            throw BizException.badRequest("车牌号已存在：" + plateNo);
        }
        Long fleetId = resolveFleetId(request.fleetId());
        checkFleetExists(fleetId);
        checkDriverExists(request.driverId());

        Vehicle patch = new Vehicle();
        patch.setId(id);
        patch.setPlateNo(plateNo);
        patch.setPlateColor(request.plateColor());
        patch.setVehicleType(request.vehicleType());
        patch.setVin(request.vin());
        patch.setEngineNo(request.engineNo());
        patch.setOwnerName(request.ownerName());
        patch.setFleetId(fleetId);
        patch.setDriverId(request.driverId());
        patch.setStatus(request.status());
        patch.setInsuranceExpireDate(request.insuranceExpireDate());
        patch.setAnnualInspectExpireDate(request.annualInspectExpireDate());
        patch.setPurchaseDate(request.purchaseDate());
        patch.setRemark(request.remark());
        vehicleRepository.update(patch);
        log.warn("修改车辆 id={} plate={} operator={}", id, plateNo, UserContext.currentUsername());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, VehicleStatusRequest request) {
        Vehicle existing = findOrThrow(id);
        ScopeGuard.requireFleetWritable(existing.getFleetId());
        if (request.status().equals(existing.getStatus())) {
            return;
        }
        if (request.status() == 2) {
            // 营运中必须有生效的终端-计价器绑定，否则平台无法采集计价与轨迹
            if (bindingRepository.findActiveByVehicle(id) == null) {
                throw BizException.badRequest("车辆未绑定终端与计价器，不能置为营运中");
            }
        }
        vehicleRepository.updateStatus(id, request.status());
        log.info("车辆状态变更 id={} {} -> {} operator={}", id, existing.getStatus(), request.status(),
                UserContext.currentUsername());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Vehicle existing = findOrThrow(id);
        ScopeGuard.requireFleetWritable(existing.getFleetId());
        DeviceBinding active = bindingRepository.findActiveByVehicle(id);
        if (active != null) {
            throw BizException.badRequest("车辆仍绑定终端 " + active.getTerminalId() + "，请先解绑");
        }
        vehicleRepository.delete(id);
        log.warn("删除车辆 id={} plate={} operator={}", id, existing.getPlateNo(), UserContext.currentUsername());
    }

    public Vehicle findOrThrow(Long id) {
        Vehicle vehicle = vehicleRepository.findById(id);
        if (vehicle == null) {
            throw BizException.notFound("车辆不存在：id=" + id);
        }
        return vehicle;
    }

    private List<VehicleView> toViews(List<Vehicle> vehicles) {
        Map<Long, String> fleetNames = new HashMap<>();
        Map<Long, String> driverNames = new HashMap<>();
        LocalDate today = LocalDate.now();
        return vehicles.stream().map(v -> {
            String fleetName = null;
            if (v.getFleetId() != null) {
                fleetName = fleetNames.computeIfAbsent(v.getFleetId(), fid -> {
                    Fleet fleet = fleetRepository.findById(fid);
                    return fleet == null ? null : fleet.getFleetName();
                });
            }
            String driverName = null;
            if (v.getDriverId() != null) {
                driverName = driverNames.computeIfAbsent(v.getDriverId(), did -> {
                    Driver driver = driverRepository.findById(did);
                    return driver == null ? null : driver.getName();
                });
            }
            Long insuranceDays = v.getInsuranceExpireDate() == null ? null
                    : ChronoUnit.DAYS.between(today, v.getInsuranceExpireDate());
            Long inspectDays = v.getAnnualInspectExpireDate() == null ? null
                    : ChronoUnit.DAYS.between(today, v.getAnnualInspectExpireDate());
            return new VehicleView(v.getId(), v.getPlateNo(), v.getPlateColor(), v.getVehicleType(), v.getVin(),
                    v.getEngineNo(), v.getOwnerName(), v.getFleetId(), fleetName, v.getDriverId(), driverName,
                    v.getStatus(), v.getInsuranceExpireDate(), insuranceDays, v.getAnnualInspectExpireDate(),
                    inspectDays, v.getPurchaseDate(), v.getRemark(), v.getCreateTime());
        }).toList();
    }

    private String normalizePlate(String plateNo) {
        return plateNo.trim().replace(" ", "").toUpperCase(Locale.ROOT);
    }

    /** 非全局账号强制落到自己的车队 */
    private Long resolveFleetId(Long requested) {
        LoginUser current = UserContext.require();
        if (current.isGlobalScope()) {
            return requested;
        }
        Long own = current.fleetId();
        if (own == null) {
            throw BizException.forbidden("当前账号未绑定车队，无法维护车辆");
        }
        if (requested != null && !requested.equals(own)) {
            throw BizException.forbidden("无权把车辆放到其他车队");
        }
        return own;
    }

    private void checkFleetExists(Long fleetId) {
        if (fleetId != null && fleetRepository.findById(fleetId) == null) {
            throw BizException.badRequest("车队不存在：id=" + fleetId);
        }
    }

    private void checkDriverExists(Long driverId) {
        if (driverId != null && driverRepository.findById(driverId) == null) {
            throw BizException.badRequest("司机不存在：id=" + driverId);
        }
    }
}
