package com.taxi.common.enums;

/**
 * 报警类型字典。
 * <p>两类来源：<br>
 * 1) 808 0x0200 报警标志位（{@code bitIndex} 非空），位定义遵循 JT/T 808，禁止擅自改动；<br>
 * 2) 主动安全扩展（ADAS/DSM）与平台规则（围栏），{@code bitIndex} 为 null，按终端扩展协议码映射，
 * 具体码值固化在 t_alarm_rule_config.ext_alarm_code。</p>
 * <p>未识别的扩展码统一落 {@link #ACTIVE_SAFETY_UNKNOWN}，不猜测映射。</p>
 */
public enum AlarmType {

    // ---------- 808 报警标志位 ----------
    SOS("紧急报警", 0, AlarmLevel.LEVEL_3),
    OVERSPEED("超速报警", 1, AlarmLevel.LEVEL_2),
    FATIGUE("疲劳驾驶报警", 2, AlarmLevel.LEVEL_2),
    WARNING("预警", 3, AlarmLevel.LEVEL_1),
    DEVICE_GNSS_FAULT("GNSS 模块故障", 4, AlarmLevel.LEVEL_1),
    DEVICE_GNSS_ANTENNA_CUT("GNSS 天线未接或被剪断", 5, AlarmLevel.LEVEL_1),
    DEVICE_GNSS_ANTENNA_SHORT("GNSS 天线短路", 6, AlarmLevel.LEVEL_1),
    DEVICE_POWER_LOW("终端主电源欠压", 7, AlarmLevel.LEVEL_1),
    DEVICE_POWER_OFF("终端主电源掉电", 8, AlarmLevel.LEVEL_2),
    DEVICE_LCD_FAULT("终端 LCD 或显示器故障", 9, AlarmLevel.LEVEL_1),
    DEVICE_TTS_FAULT("TTS 模块故障", 10, AlarmLevel.LEVEL_1),
    DEVICE_CAMERA_FAULT("摄像头故障", 11, AlarmLevel.LEVEL_2),
    DEVICE_IC_CARD_FAULT("道路运输证 IC 卡模块故障", 12, AlarmLevel.LEVEL_1),
    OVERSPEED_PREWARN("超速预警", 13, AlarmLevel.LEVEL_1),
    FATIGUE_PREWARN("疲劳驾驶预警", 14, AlarmLevel.LEVEL_1),
    ILLEGAL_DRIVING("违规行驶报警", 15, AlarmLevel.LEVEL_1),
    TIRE_PRESSURE_WARN("轮胎气压预警", 16, AlarmLevel.LEVEL_1),
    BLIND_SPOT_RIGHT("右转盲区异常报警", 17, AlarmLevel.LEVEL_2),
    DOOR_OPEN_ILLEGAL("非法开门报警", 18, AlarmLevel.LEVEL_1),

    // ---------- DSM 驾驶员状态监测 ----------
    DSM_FATIGUE("疲劳驾驶（DSM）", null, AlarmLevel.LEVEL_2),
    DSM_PHONE_CALL("接打电话", null, AlarmLevel.LEVEL_2),
    DSM_SMOKING("抽烟", null, AlarmLevel.LEVEL_2),
    DSM_DISTRACTION("分心驾驶", null, AlarmLevel.LEVEL_2),
    DSM_EYE_OFF_ROAD("不目视前方", null, AlarmLevel.LEVEL_2),
    DSM_HANDS_OFF_WHEEL("双手脱离方向盘", null, AlarmLevel.LEVEL_2),
    DSM_PHONE_HOLD("手持设备", null, AlarmLevel.LEVEL_2),

    // ---------- ADAS 高级驾驶辅助 ----------
    ADAS_FORWARD_COLLISION("前向碰撞", null, AlarmLevel.LEVEL_2),
    ADAS_LANE_DEPARTURE("车道偏离", null, AlarmLevel.LEVEL_2),
    ADAS_DISTANCE_TOO_CLOSE("车距过近", null, AlarmLevel.LEVEL_2),
    ADAS_PEDESTRIAN_COLLISION("行人碰撞", null, AlarmLevel.LEVEL_2),

    // ---------- 兜底与平台规则 ----------
    ACTIVE_SAFETY_UNKNOWN("未识别主动安全报警", null, AlarmLevel.LEVEL_1),
    FENCE_IN("进入电子围栏", null, AlarmLevel.LEVEL_1),
    FENCE_OUT("驶出电子围栏", null, AlarmLevel.LEVEL_1);

    private final String desc;
    private final Integer bitIndex;
    private final AlarmLevel defaultLevel;

    AlarmType(String desc, Integer bitIndex, AlarmLevel defaultLevel) {
        this.desc = desc;
        this.bitIndex = bitIndex;
        this.defaultLevel = defaultLevel;
    }

    public String getDesc() {
        return desc;
    }

    /** 0x0200 报警标志位序号，主动安全/围栏类为 null */
    public Integer getBitIndex() {
        return bitIndex;
    }

    /** 默认等级，可被 t_alarm_rule_config 覆盖 */
    public AlarmLevel getDefaultLevel() {
        return defaultLevel;
    }

    /** 按 808 报警标志位序号反查报警类型 */
    public static AlarmType ofBitIndex(int bitIndex) {
        for (AlarmType type : values()) {
            if (type.bitIndex != null && type.bitIndex == bitIndex) {
                return type;
            }
        }
        return null;
    }

    public static AlarmType of(String name) {
        if (name == null) {
            return null;
        }
        for (AlarmType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
