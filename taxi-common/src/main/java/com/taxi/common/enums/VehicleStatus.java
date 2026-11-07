package com.taxi.common.enums;

/**
 * 车辆状态：空闲 / 营运中 / 维修 / 停运。
 */
public enum VehicleStatus {

    IDLE(1, "空闲"),
    IN_SERVICE(2, "营运中"),
    MAINTENANCE(3, "维修"),
    SUSPENDED(4, "停运");

    private final int code;
    private final String desc;

    VehicleStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static VehicleStatus of(int code) {
        for (VehicleStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
