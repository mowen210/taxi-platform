package com.taxi.common.enums;

/**
 * 司机在职状态。
 */
public enum DriverStatus {

    ACTIVE(1, "在职"),
    RESIGNED(2, "离职"),
    SUSPENDED(3, "停运");

    private final int code;
    private final String desc;

    DriverStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static DriverStatus of(int code) {
        for (DriverStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
