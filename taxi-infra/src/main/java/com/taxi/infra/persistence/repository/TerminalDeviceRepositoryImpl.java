package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.TerminalDevice;
import com.taxi.domain.repository.TerminalDeviceRepository;
import com.taxi.infra.persistence.mapper.TerminalDeviceMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 终端档案仓储实现。
 */
@Repository
public class TerminalDeviceRepositoryImpl implements TerminalDeviceRepository {

    private final TerminalDeviceMapper terminalMapper;

    public TerminalDeviceRepositoryImpl(TerminalDeviceMapper terminalMapper) {
        this.terminalMapper = terminalMapper;
    }

    @Override
    public TerminalDevice findById(Long id) {
        return terminalMapper.selectById(id);
    }

    @Override
    public TerminalDevice findByTerminalId(String terminalId) {
        if (!StringUtils.hasText(terminalId)) {
            return null;
        }
        String value = terminalId.trim();
        TerminalDevice device = terminalMapper.selectOne(Wrappers.<TerminalDevice>lambdaQuery()
                .eq(TerminalDevice::getTerminalId, value));
        if (device == null) {
            String numeric = value.replaceFirst("^0+", "");
            device = terminalMapper.selectByNumericTerminalId(numeric.isEmpty() ? value : numeric);
        }
        return device;
    }

    @Override
    public PageResult<TerminalDevice> search(String keyword, Integer onlineStatus, int page, int size) {
        LambdaQueryWrapper<TerminalDevice> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(TerminalDevice::getTerminalId, trimmed)
                    .or().like(TerminalDevice::getTerminalCode, trimmed)
                    .or().like(TerminalDevice::getTerminalSn, trimmed)
                    .or().like(TerminalDevice::getSimNo, trimmed));
        }
        if (onlineStatus != null) {
            wrapper.eq(TerminalDevice::getOnlineStatus, onlineStatus);
        }
        wrapper.orderByDesc(TerminalDevice::getCreateTime);
        Page<TerminalDevice> result = terminalMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    @Override
    public void save(TerminalDevice device) {
        terminalMapper.insert(device);
    }

    @Override
    public void update(TerminalDevice device) {
        terminalMapper.updateById(device);
    }

    @Override
    public void delete(Long id) {
        terminalMapper.deleteById(id);
    }
}
