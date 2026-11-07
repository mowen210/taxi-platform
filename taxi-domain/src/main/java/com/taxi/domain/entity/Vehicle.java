package com.taxi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

/**
 * 车辆档案（t_vehicle）。
 * <p>状态取值固定 1 空闲 / 2 营运中 / 3 维修 / 4 停运，见 {@link com.taxi.common.enums.VehicleStatus}。</p>
 */
@TableName("t_vehicle")
public class Vehicle extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String plateNo;

    private String plateColor;

    private String vehicleType;

    private String vin;

    private String engineNo;

    private String ownerName;

    private Long fleetId;

    /** 默认司机 */
    private Long driverId;

    /** 1 空闲 2 营运中 3 维修 4 停运 */
    private Integer status;

    private LocalDate insuranceExpireDate;

    private LocalDate annualInspectExpireDate;

    private LocalDate purchaseDate;

    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlateNo() {
        return plateNo;
    }

    public void setPlateNo(String plateNo) {
        this.plateNo = plateNo;
    }

    public String getPlateColor() {
        return plateColor;
    }

    public void setPlateColor(String plateColor) {
        this.plateColor = plateColor;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVin() {
        return vin;
    }

    public void setVin(String vin) {
        this.vin = vin;
    }

    public String getEngineNo() {
        return engineNo;
    }

    public void setEngineNo(String engineNo) {
        this.engineNo = engineNo;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Long getFleetId() {
        return fleetId;
    }

    public void setFleetId(Long fleetId) {
        this.fleetId = fleetId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDate getInsuranceExpireDate() {
        return insuranceExpireDate;
    }

    public void setInsuranceExpireDate(LocalDate insuranceExpireDate) {
        this.insuranceExpireDate = insuranceExpireDate;
    }

    public LocalDate getAnnualInspectExpireDate() {
        return annualInspectExpireDate;
    }

    public void setAnnualInspectExpireDate(LocalDate annualInspectExpireDate) {
        this.annualInspectExpireDate = annualInspectExpireDate;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
