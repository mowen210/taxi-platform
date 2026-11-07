package com.taxi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 司机证件（t_driver_certificate）。证件到期预警的数据源。
 */
@TableName("t_driver_certificate")
public class DriverCertificate extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long driverId;

    /** 1 驾驶证 2 从业资格证 3 身份证 4 健康证 9 其他 */
    private Integer certType;

    private String certNo;

    private LocalDate issueDate;

    private LocalDate expireDate;

    private String fileUrl;

    /** 0 正常 1 预警 2 过期 */
    private Integer warnStatus;

    private LocalDateTime lastWarnTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public Integer getCertType() {
        return certType;
    }

    public void setCertType(Integer certType) {
        this.certType = certType;
    }

    public String getCertNo() {
        return certNo;
    }

    public void setCertNo(String certNo) {
        this.certNo = certNo;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getExpireDate() {
        return expireDate;
    }

    public void setExpireDate(LocalDate expireDate) {
        this.expireDate = expireDate;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public Integer getWarnStatus() {
        return warnStatus;
    }

    public void setWarnStatus(Integer warnStatus) {
        this.warnStatus = warnStatus;
    }

    public LocalDateTime getLastWarnTime() {
        return lastWarnTime;
    }

    public void setLastWarnTime(LocalDateTime lastWarnTime) {
        this.lastWarnTime = lastWarnTime;
    }
}
