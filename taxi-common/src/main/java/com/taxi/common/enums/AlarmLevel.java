package com.taxi.common.enums;

/**
 * 三级报警等级。
 * <p>一级：仅记录；二级：弹窗+提示音+短信通知+人工处置；三级：超时自动升级、强制置顶督办。</p>
 * <p>具体报警类型的默认等级见 {@link AlarmType#getDefaultLevel()}，可在 t_alarm_rule_config 按车队/车辆覆盖。</p>
 */
public enum AlarmLevel {

    LEVEL_1(1, "一级报警", "仅记录，不打扰"),
    LEVEL_2(2, "二级报警", "弹窗+提示音+短信通知+人工处置"),
    LEVEL_3(3, "三级报警", "超时自动升级，强制置顶督办");

    private final int code;
    private final String desc;
    private final String action;

    AlarmLevel(int code, String desc, String action) {
        this.code = code;
        this.desc = desc;
        this.action = action;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public String getAction() {
        return action;
    }

    public static AlarmLevel of(int code) {
        for (AlarmLevel level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        return LEVEL_1;
    }
}
