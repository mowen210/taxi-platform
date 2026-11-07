package com.taxi.application.service;

import com.taxi.application.dto.BindRequest;
import com.taxi.application.dto.BindingView;
import com.taxi.application.dto.TerminalSaveRequest;
import com.taxi.application.dto.TerminalView;
import com.taxi.application.support.ScopeGuard;
import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.DeviceBinding;
import com.taxi.domain.entity.TerminalDevice;
import com.taxi.domain.entity.Vehicle;
import com.taxi.domain.repository.DeviceBindingRepository;
import com.taxi.domain.repository.TerminalDeviceRepository;
import com.taxi.domain.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 终端档案与「车辆-终端-计价器」绑定用例。
 * <p>绑定关系是一车一终端一计价器：同一时刻车辆、终端、计价器各自只能有一条生效绑定，
 * 业务校验之外由表上 active_key 唯一索引兜底并发。</p>
 */
@Service
public class TerminalAppService {

    private static final Logger log = LoggerFactory.getLogger(TerminalAppService.class);

    private final TerminalDeviceRepository terminalRepository;
    private final DeviceBindingRepository bindingRepository;
    private final VehicleRepository vehicleRepository;

    public TerminalAppService(TerminalDeviceRepository terminalRepository,
                              DeviceBindingRepository bindingRepository,
                              VehicleRepository vehicleRepository) {
        this.terminalRepository = terminalRepository;
        this.bindingRepository = bindingRepository;
        this.vehicleRepository = vehicleRepository;
    }

    public PageResult<TerminalView> page(String keyword, Integer onlineStatus, long page, long size) {
        long safePage = Math.max(1L, page);
        long safeSize = Math.min(Math.max(1L, size), 100L);
        PageResult<TerminalDevice> result = terminalRepository.search(keyword, onlineStatus,
                (int) safePage, (int) safeSize);
        Long scopeFleet = currentScopeFleet();
        List<TerminalView> views = result.list().stream()
                .map(t -> toView(t, scopeFleet))
                .filter(v -> v != null)
                .toList();
        return PageResult.of(result.total(), result.page(), result.size(), views);
    }

    public TerminalView detail(Long id) {
        TerminalDevice device = findOrThrow(id);
        TerminalView view = toView(device, currentScopeFleet());
        if (view == null) {
            throw BizException.forbidden("无权查看其他车队的终端");
        }
        return view;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(TerminalSaveRequest request) {
        if (!ScopeGuard.isGlobal()) {
            throw BizException.forbidden("仅平台管理员可维护终端档案");
        }
        String terminalId = request.terminalId().trim();
        if (terminalRepository.findByTerminalId(terminalId) != null) {
            throw BizException.badRequest("终端手机号已存在：" + terminalId);
        }
        TerminalDevice device = new TerminalDevice();
        device.setTerminalId(terminalId);
        device.setTerminalCode(request.terminalCode());
        device.setSimNo(request.simNo());
        device.setManufacturerId(request.manufacturerId());
        device.setTerminalModel(request.terminalModel());
        device.setTerminalSn(request.terminalSn());
        device.setProtocolVersion(StringUtils.hasText(request.protocolVersion())
                ? request.protocolVersion().trim() : "2019");
        device.setAuthCode(request.authCode());
        device.setFirmwareVersion(request.firmwareVersion());
        device.setOnlineStatus(0);
        terminalRepository.save(device);
        log.info("新增终端 id={} terminalId={}", device.getId(), terminalId);
        return device.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TerminalSaveRequest request) {
        if (!ScopeGuard.isGlobal()) {
            throw BizException.forbidden("仅平台管理员可维护终端档案");
        }
        TerminalDevice existing = findOrThrow(id);
        String terminalId = request.terminalId().trim();
        TerminalDevice byTerminalId = terminalRepository.findByTerminalId(terminalId);
        if (byTerminalId != null && !byTerminalId.getId().equals(id)) {
            throw BizException.badRequest("终端手机号已存在：" + terminalId);
        }
        if (!existing.getTerminalId().equals(terminalId)
                && bindingRepository.findActiveByTerminal(existing.getTerminalId()) != null) {
            throw BizException.badRequest("终端处于生效绑定状态，不能修改终端手机号，请先解绑");
        }
        TerminalDevice patch = new TerminalDevice();
        patch.setId(id);
        patch.setTerminalId(terminalId);
        patch.setTerminalCode(request.terminalCode());
        patch.setSimNo(request.simNo());
        patch.setManufacturerId(request.manufacturerId());
        patch.setTerminalModel(request.terminalModel());
        patch.setTerminalSn(request.terminalSn());
        patch.setProtocolVersion(StringUtils.hasText(request.protocolVersion())
                ? request.protocolVersion().trim() : null);
        patch.setAuthCode(request.authCode());
        patch.setFirmwareVersion(request.firmwareVersion());
        terminalRepository.update(patch);
        log.warn("修改终端 id={} terminalId={} operator={}", id, terminalId, UserContext.currentUsername());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (!ScopeGuard.isGlobal()) {
            throw BizException.forbidden("仅平台管理员可维护终端档案");
        }
        TerminalDevice existing = findOrThrow(id);
        DeviceBinding active = bindingRepository.findActiveByTerminal(existing.getTerminalId());
        if (active != null) {
            throw BizException.badRequest("终端仍绑定车辆 ID " + active.getVehicleId() + "，请先解绑");
        }
        terminalRepository.delete(id);
        log.warn("删除终端 id={} terminalId={} operator={}", id, existing.getTerminalId(),
                UserContext.currentUsername());
    }

    /** 人工绑定：默认 bind_source=3 */
    @Transactional(rollbackFor = Exception.class)
    public BindingView bind(BindRequest request) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId());
        if (vehicle == null) {
            throw BizException.notFound("车辆不存在：id=" + request.vehicleId());
        }
        ScopeGuard.requireFleetWritable(vehicle.getFleetId());

        String terminalId = request.terminalId().trim();
        TerminalDevice terminal = terminalRepository.findByTerminalId(terminalId);
        if (terminal == null) {
            throw BizException.badRequest("终端不存在：" + terminalId);
        }
        String taximeterSn = request.taximeterSn().trim();

        DeviceBinding current = bindingRepository.findActiveByVehicle(vehicle.getId());
        if (current != null) {
            if (current.getTerminalId().equals(terminalId) && current.getTaximeterSn().equals(taximeterSn)) {
                throw BizException.badRequest("该车辆已绑定同一终端与计价器，无需重复绑定");
            }
            throw BizException.badRequest("车辆已绑定终端 " + current.getTerminalId() + "，请先解绑");
        }
        DeviceBinding terminalBinding = bindingRepository.findActiveByTerminal(terminalId);
        if (terminalBinding != null && !terminalBinding.getVehicleId().equals(vehicle.getId())) {
            throw BizException.badRequest("终端 " + terminalId + " 已绑定其他车辆，请先解绑");
        }

        LoginUser operator = UserContext.get();
        DeviceBinding binding = new DeviceBinding();
        binding.setVehicleId(vehicle.getId());
        binding.setTerminalId(terminalId);
        binding.setTaximeterSn(taximeterSn);
        binding.setBindStatus(1);
        binding.setBindSource(3);
        binding.setBindTime(LocalDateTime.now());
        binding.setOperatorId(operator == null ? null : operator.userId());
        binding.setOperatorName(operator == null ? null : operator.realName());
        binding.setRemark(request.remark());
        try {
            bindingRepository.save(binding);
        } catch (DuplicateKeyException e) {
            // 并发下唯一索引兜底：active_key 冲突
            throw BizException.badRequest("绑定冲突：该车-终端-计价器组合已存在生效绑定");
        }
        log.info("绑定设备 vehicle={} terminal={} taximeter={}", vehicle.getPlateNo(), terminalId, taximeterSn);
        return toBindingView(binding, vehicle.getPlateNo());
    }

    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long vehicleId, String remark) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId);
        if (vehicle == null) {
            throw BizException.notFound("车辆不存在：id=" + vehicleId);
        }
        ScopeGuard.requireFleetWritable(vehicle.getFleetId());
        LoginUser operator = UserContext.get();
        int rows = bindingRepository.unbindActiveByVehicle(vehicleId, LocalDateTime.now(),
                operator == null ? null : operator.userId(), remark);
        if (rows == 0) {
            throw BizException.badRequest("该车辆当前没有生效绑定");
        }
        log.warn("解绑设备 plate={} rows={} operator={}", vehicle.getPlateNo(), rows,
                UserContext.currentUsername());
    }

    public List<BindingView> history(Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId);
        if (vehicle == null) {
            throw BizException.notFound("车辆不存在：id=" + vehicleId);
        }
        ScopeGuard.requireFleetReadable(vehicle.getFleetId());
        return bindingRepository.listByVehicle(vehicleId).stream()
                .map(b -> toBindingView(b, vehicle.getPlateNo()))
                .toList();
    }

    public PageResult<BindingView> bindings(String keyword, Integer bindStatus, long page, long size) {
        long safePage = Math.max(1L, page);
        long safeSize = Math.min(Math.max(1L, size), 100L);
        PageResult<DeviceBinding> result = bindingRepository.search(keyword, bindStatus,
                (int) safePage, (int) safeSize);
        Long scopeFleet = currentScopeFleet();
        Map<Long, String> plateCache = new HashMap<>();
        Map<Long, Boolean> allowed = new HashMap<>();
        List<BindingView> views = result.list().stream()
                .filter(b -> {
                    boolean ok = allowed.computeIfAbsent(b.getVehicleId(), vid -> {
                        Vehicle v = vehicleRepository.findById(vid);
                        if (v != null) {
                            plateCache.put(vid, v.getPlateNo());
                        }
                        return scopeFleet == null || (v != null && scopeFleet.equals(v.getFleetId()));
                    });
                    return ok;
                })
                .map(b -> toBindingView(b, plateCache.get(b.getVehicleId())))
                .toList();
        return PageResult.of(result.total(), result.page(), result.size(), views);
    }

    private TerminalView toView(TerminalDevice device, Long scopeFleet) {
        DeviceBinding binding = bindingRepository.findActiveByTerminal(device.getTerminalId());
        Long boundVehicleId = null;
        String boundPlateNo = null;
        String boundTaximeter = null;
        if (binding != null) {
            Vehicle vehicle = vehicleRepository.findById(binding.getVehicleId());
            if (scopeFleet != null && (vehicle == null || !scopeFleet.equals(vehicle.getFleetId()))) {
                return null;
            }
            boundVehicleId = binding.getVehicleId();
            boundPlateNo = vehicle == null ? null : vehicle.getPlateNo();
            boundTaximeter = binding.getTaximeterSn();
        } else if (scopeFleet != null) {
            // 未绑定终端的归属无法判定，非全局账号一律不返回，避免越权看到全域终端
            return null;
        }
        return new TerminalView(device.getId(), device.getTerminalId(), device.getTerminalCode(), device.getSimNo(),
                device.getManufacturerId(), device.getTerminalModel(), device.getTerminalSn(),
                device.getProtocolVersion(), device.getFirmwareVersion(), device.getOnlineStatus(),
                device.getLastHeartbeatTime(), device.getLastOnlineTime(), device.getRegisterTime(),
                boundVehicleId, boundPlateNo, boundTaximeter, device.getCreateTime());
    }

    private BindingView toBindingView(DeviceBinding binding, String plateNo) {
        return new BindingView(binding.getId(), binding.getVehicleId(), plateNo, binding.getTerminalId(),
                binding.getTaximeterSn(), binding.getBindStatus(), binding.getBindSource(), binding.getBindTime(),
                binding.getUnbindTime(), binding.getOperatorId(), binding.getOperatorName(), binding.getRemark());
    }

    /** 非全局账号的车队 ID；全局账号返回 null */
    private Long currentScopeFleet() {
        LoginUser current = UserContext.require();
        return current.isGlobalScope() ? null : current.fleetId();
    }

    private TerminalDevice findOrThrow(Long id) {
        TerminalDevice device = terminalRepository.findById(id);
        if (device == null) {
            throw BizException.notFound("终端不存在：id=" + id);
        }
        return device;
    }
}
