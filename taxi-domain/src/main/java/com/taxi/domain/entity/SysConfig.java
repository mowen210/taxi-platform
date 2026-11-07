package com.taxi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统参数（t_sys_config）。
 * <p>支持全局/车队/车辆三级配置：报警阈值、录像时长、计价方案均可落此表或业务专表。</p>
 * <p>取值优先级：车辆 &gt; 车队 &gt; 全局。</p>
 */
@TableName("t_sys_config")
public class SysConfig extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String configKey;

    private String configValue;

    /** STRING/INT/DECIMAL/BOOLEAN/JSON */
    private String valueType;

    /** 1 全局 2 车队 3 车辆 */
    private Integer scopeType;

    /** 车队 ID 或车辆 ID，scopeType=1 时为 0 */
    private Long scopeId;

    private String configGroup;

    private String description;

    /** 1 可编辑 0 只读 */
    private Integer editable;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public Integer getScopeType() {
        return scopeType;
    }

    public void setScopeType(Integer scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeId() {
        return scopeId;
    }

    public void setScopeId(Long scopeId) {
        this.scopeId = scopeId;
    }

    public String getConfigGroup() {
        return configGroup;
    }

    public void setConfigGroup(String configGroup) {
        this.configGroup = configGroup;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getEditable() {
        return editable;
    }

    public void setEditable(Integer editable) {
        this.editable = editable;
    }
}
