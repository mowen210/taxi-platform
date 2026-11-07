package com.taxi.common.crypto;

/**
 * 页面脱敏工具。数据库存密文、接口返回脱敏值，任何接口不得回传完整手机号与身份证号。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 手机号脱敏：13800001111 -> 138****1111 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone == null ? null : "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /** 身份证脱敏：110101199001011234 -> 110101********1234 */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard == null ? null : "****";
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /** 姓名脱敏：张三 -> 张*，欧阳修 -> 欧** */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /** 车牌脱敏（保留前 2 位与末 2 位） */
    public static String maskPlate(String plate) {
        if (plate == null || plate.length() < 5) {
            return plate;
        }
        return plate.substring(0, 2) + "***" + plate.substring(plate.length() - 2);
    }
}
