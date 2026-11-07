package com.taxi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 司机档案（t_driver）。
 * <p>手机号、身份证入库前加密；读取时解密为明文仅存内存，出参一律脱敏。</p>
 */
@TableName("t_driver")
public class Driver extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String driverCode;

    private String name;

    /** AES-GCM 密文 */
    private String phone;

    /** HMAC-SHA256 摘要，用于等值检索 */
    private String phoneHash;

    /** AES-GCM 密文 */
    private String idCard;

    private String idCardMask;

    /** 1 男 2 女 */
    private Integer gender;

    private String licenseNo;

    private String licenseClass;

    private LocalDate licenseExpireDate;

    private String qualificationNo;

    private LocalDate qualificationExpireDate;

    private Long fleetId;

    /** 1 在线 0 离线 */
    private Integer onlineStatus;

    private BigDecimal safetyScore;

    /** 1 在职 2 离职 3 停运 */
    private Integer status;

    private LocalDate entryDate;

    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDriverCode() {
        return driverCode;
    }

    public void setDriverCode(String driverCode) {
        this.driverCode = driverCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhoneHash() {
        return phoneHash;
    }

    public void setPhoneHash(String phoneHash) {
        this.phoneHash = phoneHash;
    }

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard;
    }

    public String getIdCardMask() {
        return idCardMask;
    }

    public void setIdCardMask(String idCardMask) {
        this.idCardMask = idCardMask;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public String getLicenseNo() {
        return licenseNo;
    }

    public void setLicenseNo(String licenseNo) {
        this.licenseNo = licenseNo;
    }

    public String getLicenseClass() {
        return licenseClass;
    }

    public void setLicenseClass(String licenseClass) {
        this.licenseClass = licenseClass;
    }

    public LocalDate getLicenseExpireDate() {
        return licenseExpireDate;
    }

    public void setLicenseExpireDate(LocalDate licenseExpireDate) {
        this.licenseExpireDate = licenseExpireDate;
    }

    public String getQualificationNo() {
        return qualificationNo;
    }

    public void setQualificationNo(String qualificationNo) {
        this.qualificationNo = qualificationNo;
    }

    public LocalDate getQualificationExpireDate() {
        return qualificationExpireDate;
    }

    public void setQualificationExpireDate(LocalDate qualificationExpireDate) {
        this.qualificationExpireDate = qualificationExpireDate;
    }

    public Long getFleetId() {
        return fleetId;
    }

    public void setFleetId(Long fleetId) {
        this.fleetId = fleetId;
    }

    public Integer getOnlineStatus() {
        return onlineStatus;
    }

    public void setOnlineStatus(Integer onlineStatus) {
        this.onlineStatus = onlineStatus;
    }

    public BigDecimal getSafetyScore() {
        return safetyScore;
    }

    public void setSafetyScore(BigDecimal safetyScore) {
        this.safetyScore = safetyScore;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
