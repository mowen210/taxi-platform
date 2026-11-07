package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.DeviceBinding;
import com.taxi.domain.repository.DeviceBindingRepository;
import com.taxi.infra.persistence.mapper.DeviceBindingMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 设备绑定仓储实现。
 * <p>并发重复绑定由表上生成列 `active_key` 的唯一索引兜底，此处只做业务前置校验。</p>
 */
@Repository
public class DeviceBindingRepositoryImpl implements DeviceBindingRepository {

    private static final Pattern NUMERIC = Pattern.compile("^\\d{1,18}$");

    private final DeviceBindingMapper bindingMapper;

    public DeviceBindingRepositoryImpl(DeviceBindingMapper bindingMapper) {
        this.bindingMapper = bindingMapper;
    }

    @Override
    public DeviceBinding findById(Long id) {
        return bindingMapper.selectById(id);
    }

    @Override
    public DeviceBinding findActiveByVehicle(Long vehicleId) {
        List<DeviceBinding> list = bindingMapper.selectList(Wrappers.<DeviceBinding>lambdaQuery()
                .eq(DeviceBinding::getVehicleId, vehicleId)
                .eq(DeviceBinding::getBindStatus, 1)
                .orderByDesc(DeviceBinding::getBindTime)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public DeviceBinding findActiveByTerminal(String terminalId) {
        if (!StringUtils.hasText(terminalId)) {
            return null;
        }
        String value = terminalId.trim();
        List<DeviceBinding> list = bindingMapper.selectList(Wrappers.<DeviceBinding>lambdaQuery()
                .eq(DeviceBinding::getTerminalId, value)
                .eq(DeviceBinding::getBindStatus, 1)
                .orderByDesc(DeviceBinding::getBindTime)
                .last("LIMIT 1"));
        if (!list.isEmpty()) {
            return list.get(0);
        }
        String numeric = value.replaceFirst("^0+", "");
        return bindingMapper.selectActiveByNumericTerminalId(numeric.isEmpty() ? value : numeric);
    }

    @Override
    public List<DeviceBinding> listByVehicle(Long vehicleId) {
        return bindingMapper.selectList(Wrappers.<DeviceBinding>lambdaQuery()
                .eq(DeviceBinding::getVehicleId, vehicleId)
                .orderByDesc(DeviceBinding::getBindTime));
    }

    @Override
    public PageResult<DeviceBinding> search(String keyword, Integer bindStatus, int page, int size) {
        LambdaQueryWrapper<DeviceBinding> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            Long vehicleId = NUMERIC.matcher(trimmed).matches() ? Long.valueOf(trimmed) : null;
            wrapper.and(w -> {
                w.like(DeviceBinding::getTerminalId, trimmed)
                        .or().like(DeviceBinding::getTaximeterSn, trimmed);
                if (vehicleId != null) {
                    w.or().eq(DeviceBinding::getVehicleId, vehicleId);
                }
            });
        }
        if (bindStatus != null) {
            wrapper.eq(DeviceBinding::getBindStatus, bindStatus);
        }
        wrapper.orderByDesc(DeviceBinding::getBindTime);
        Page<DeviceBinding> result = bindingMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public void save(DeviceBinding binding) {
        bindingMapper.insert(binding);
    }

    @Override
    public void update(DeviceBinding binding) {
        bindingMapper.updateById(binding);
    }

    @Override
    public int unbindActiveByVehicle(Long vehicleId, LocalDateTime unbindTime, Long operatorId, String remark) {
        return bindingMapper.update(null, Wrappers.<DeviceBinding>lambdaUpdate()
                .eq(DeviceBinding::getVehicleId, vehicleId)
                .eq(DeviceBinding::getBindStatus, 1)
                .set(DeviceBinding::getBindStatus, 0)
                .set(DeviceBinding::getUnbindTime, unbindTime)
                .set(DeviceBinding::getOperatorId, operatorId)
                .set(DeviceBinding::getRemark, remark));
    }
}
