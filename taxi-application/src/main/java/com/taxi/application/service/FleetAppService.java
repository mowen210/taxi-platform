package com.taxi.application.service;

import com.taxi.application.dto.FleetSaveRequest;
import com.taxi.application.dto.FleetView;
import com.taxi.application.support.ScopeGuard;
import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.crypto.MaskUtil;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Fleet;
import com.taxi.domain.repository.FleetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 车队管理用例。
 * <p>车队是数据权限的边界：非全局账号只能看到、维护自己的车队。</p>
 */
@Service
public class FleetAppService {

    private static final Logger log = LoggerFactory.getLogger(FleetAppService.class);

    private final FleetRepository fleetRepository;

    public FleetAppService(FleetRepository fleetRepository) {
        this.fleetRepository = fleetRepository;
    }

    public PageResult<FleetView> page(String keyword, Integer status, long page, long size) {
        long safePage = Math.max(1L, page);
        long safeSize = Math.min(Math.max(1L, size), 100L);
        LoginUser current = UserContext.require();
        if (current.isGlobalScope()) {
            PageResult<Fleet> result = fleetRepository.search(keyword, status, (int) safePage, (int) safeSize);
            return PageResult.of(result.total(), result.page(), result.size(),
                    result.list().stream().map(this::toView).toList());
        }
        // 非全局账号：结果集只有自己的车队一条，直接在内存里过滤
        Long fleetId = current.fleetId();
        if (fleetId == null) {
            throw BizException.forbidden("当前账号未绑定车队，无法查看车队数据");
        }
        Fleet own = fleetRepository.findById(fleetId);
        if (own == null) {
            return PageResult.empty(safePage, safeSize);
        }
        boolean matched = matches(own, keyword) && (status == null || status.equals(own.getStatus()));
        List<Fleet> list = matched ? List.of(own) : List.of();
        return PageResult.of(list.size(), safePage, safeSize, list.stream().map(this::toView).toList());
    }

    public FleetView detail(Long id) {
        Fleet fleet = findOrThrow(id);
        ScopeGuard.requireFleetReadable(fleet.getId());
        return toView(fleet);
    }

    /** 下拉选项：所有登录用户可用，仅暴露编码与名称 */
    public List<FleetView> options() {
        LoginUser current = UserContext.require();
        List<Fleet> fleets = fleetRepository.listAllEnabled();
        if (!current.isGlobalScope()) {
            fleets = fleets.stream().filter(f -> f.getId().equals(current.fleetId())).toList();
        }
        return fleets.stream().map(this::toView).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(FleetSaveRequest request) {
        if (!ScopeGuard.isGlobal()) {
            throw BizException.forbidden("仅平台管理员可新增车队");
        }
        String code = request.fleetCode().trim().toUpperCase(Locale.ROOT);
        if (fleetRepository.findByCode(code) != null) {
            throw BizException.badRequest("车队编码已存在");
        }
        Fleet fleet = new Fleet();
        fleet.setFleetCode(code);
        fleet.setFleetName(request.fleetName().trim());
        fleet.setTaxNo(StringUtils.hasText(request.taxNo()) ? request.taxNo().trim() : null);
        fleet.setContact(request.contact());
        fleet.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        fleet.setAddress(request.address());
        fleet.setStatus(request.status() == null ? 1 : request.status());
        fleetRepository.save(fleet);
        log.info("新增车队 id={} code={}", fleet.getId(), fleet.getFleetCode());
        return fleet.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FleetSaveRequest request) {
        Fleet existing = findOrThrow(id);
        ScopeGuard.requireFleetWritable(existing.getId());

        String code = request.fleetCode().trim().toUpperCase(Locale.ROOT);
        Fleet byCode = fleetRepository.findByCode(code);
        if (byCode != null && !byCode.getId().equals(id)) {
            throw BizException.badRequest("车队编码已存在");
        }
        Fleet patch = new Fleet();
        patch.setId(id);
        patch.setFleetCode(code);
        patch.setFleetName(request.fleetName().trim());
        patch.setTaxNo(StringUtils.hasText(request.taxNo()) ? request.taxNo().trim() : null);
        patch.setContact(request.contact());
        patch.setAddress(request.address());
        patch.setStatus(request.status());
        if (StringUtils.hasText(request.phone())) {
            // 脱敏值由编码器识别为「不修改」，不会覆盖已有密文
            patch.setPhone(request.phone().trim());
        }
        fleetRepository.update(patch);
        log.warn("修改车队 id={} operator={}", id, UserContext.currentUsername());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Fleet existing = findOrThrow(id);
        ScopeGuard.requireFleetWritable(existing.getId());
        long vehicles = fleetRepository.countVehicles(id);
        if (vehicles > 0) {
            throw BizException.badRequest("车队下还有 " + vehicles + " 辆车，请先转移或删除车辆");
        }
        long drivers = fleetRepository.countDrivers(id);
        if (drivers > 0) {
            throw BizException.badRequest("车队下还有 " + drivers + " 名司机，请先转移或删除司机");
        }
        fleetRepository.delete(id);
        log.warn("删除车队 id={} operator={}", id, UserContext.currentUsername());
    }

    public Fleet findOrThrow(Long id) {
        Fleet fleet = fleetRepository.findById(id);
        if (fleet == null) {
            throw BizException.notFound("车队不存在：id=" + id);
        }
        return fleet;
    }

    private boolean matches(Fleet fleet, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String k = keyword.trim().toLowerCase(Locale.ROOT);
        return (fleet.getFleetCode() != null && fleet.getFleetCode().toLowerCase(Locale.ROOT).contains(k))
                || (fleet.getFleetName() != null && fleet.getFleetName().toLowerCase(Locale.ROOT).contains(k));
    }

    private FleetView toView(Fleet fleet) {
        return new FleetView(fleet.getId(), fleet.getFleetCode(), fleet.getFleetName(), fleet.getTaxNo(),
                fleet.getContact(), MaskUtil.maskPhone(fleet.getPhone()), fleet.getAddress(),
                fleet.getStatus(), fleet.getCreateTime());
    }
}
