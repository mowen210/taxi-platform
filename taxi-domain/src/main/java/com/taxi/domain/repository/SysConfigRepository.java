package com.taxi.domain.repository;

import com.taxi.domain.entity.SysConfig;

import java.util.List;
import java.util.Optional;

/**
 * 系统参数仓储。
 * <p>三级配置解析优先级：车辆 &gt; 车队 &gt; 全局。</p>
 */
public interface SysConfigRepository {

    Optional<SysConfig> find(String configKey, Integer scopeType, Long scopeId);

    List<SysConfig> findByKey(String configKey);

    void save(SysConfig config);

    void update(SysConfig config);

    /**
     * 按优先级解析参数值。
     *
     * @param configKey 参数键
     * @param fleetId   车队 ID，可为空
     * @param vehicleId 车辆 ID，可为空
     * @return 解析到的值；全部缺失返回 null，由调用方决定默认值
     */
    String resolveValue(String configKey, Long fleetId, Long vehicleId);
}
