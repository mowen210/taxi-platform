package com.taxi.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taxi.domain.entity.SysConfig;
import com.taxi.domain.repository.SysConfigRepository;
import com.taxi.infra.persistence.mapper.SysConfigMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统参数仓储实现。解析优先级：车辆(3) &gt; 车队(2) &gt; 全局(1)。
 */
@Repository
public class SysConfigRepositoryImpl implements SysConfigRepository {

    private final SysConfigMapper sysConfigMapper;

    public SysConfigRepositoryImpl(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    @Override
    public Optional<SysConfig> find(String configKey, Integer scopeType, Long scopeId) {
        LambdaQueryWrapper<SysConfig> wrapper = Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, configKey)
                .eq(SysConfig::getScopeType, scopeType)
                .eq(SysConfig::getScopeId, scopeId == null ? 0L : scopeId);
        return Optional.ofNullable(sysConfigMapper.selectOne(wrapper));
    }

    @Override
    public List<SysConfig> findByKey(String configKey) {
        return sysConfigMapper.selectList(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, configKey)
                .orderByAsc(SysConfig::getScopeType));
    }

    @Override
    public void save(SysConfig config) {
        sysConfigMapper.insert(config);
    }

    @Override
    public void update(SysConfig config) {
        sysConfigMapper.updateById(config);
    }

    @Override
    public String resolveValue(String configKey, Long fleetId, Long vehicleId) {
        if (vehicleId != null) {
            Optional<SysConfig> vehicleLevel = find(configKey, 3, vehicleId);
            if (vehicleLevel.isPresent()) {
                return vehicleLevel.get().getConfigValue();
            }
        }
        if (fleetId != null) {
            Optional<SysConfig> fleetLevel = find(configKey, 2, fleetId);
            if (fleetLevel.isPresent()) {
                return fleetLevel.get().getConfigValue();
            }
        }
        return find(configKey, 1, 0L).map(SysConfig::getConfigValue).orElse(null);
    }
}
