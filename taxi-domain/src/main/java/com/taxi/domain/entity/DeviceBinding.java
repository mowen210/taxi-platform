package com.taxi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 车辆-终端-计价器绑定（t_device_binding）。
 * <p>表内 `active_key` 为数据库生成列，实体不映射，由唯一索引兜底并发重复绑定。</p>
 */
@TableName("t_device_binding")
public class DeviceBinding extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long vehicleId;

    private String terminalId;

    private String taximeterSn;

    /** 1 绑定 0 解绑 */
    private Integer bindStatus;

    /** 1 注册扩展字段 2 指令查询上报 3 人工绑定 */
    private Integer bindSource;

    private LocalDateTime bindTime;

    private LocalDateTime unbindTime;

    private Long operatorId;

    private String operatorName;

    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(Long vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public String getTaximeterSn() {
        return taximeterSn;
    }

    public void setTaximeterSn(String taximeterSn) {
        this.taximeterSn = taximeterSn;
    }

    public Integer getBindStatus() {
        return bindStatus;
    }

    public void setBindStatus(Integer bindStatus) {
        this.bindStatus = bindStatus;
    }

    public Integer getBindSource() {
        return bindSource;
    }

    public void setBindSource(Integer bindSource) {
        this.bindSource = bindSource;
    }

    public LocalDateTime getBindTime() {
        return bindTime;
    }

    public void setBindTime(LocalDateTime bindTime) {
        this.bindTime = bindTime;
    }

    public LocalDateTime getUnbindTime() {
        return unbindTime;
    }

    public void setUnbindTime(LocalDateTime unbindTime) {
        this.unbindTime = unbindTime;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
