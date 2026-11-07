package com.taxi.protocol808;

import java.time.LocalDateTime;

/**
 * BCD 与无符号整数工具。
 */
public final class Bcd {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private Bcd() {
    }

    /** BCD 解码为数字串（每字节两位） */
    public static String decode(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            int value = data[offset + i] & 0xFF;
            sb.append((value >> 4) & 0x0F).append(value & 0x0F);
        }
        return sb.toString();
    }

    /** 数字串编码为 BCD，奇数位左补 0 */
    public static byte[] encode(String digits) {
        String normalized = digits.length() % 2 == 0 ? digits : "0" + digits;
        byte[] out = new byte[normalized.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = normalized.charAt(i * 2) - '0';
            int low = normalized.charAt(i * 2 + 1) - '0';
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    /** 6 字节 BCD 时间（YYMMDDHHmmss） */
    public static LocalDateTime decodeTime(byte[] data, int offset) {
        String digits = decode(data, offset, 6);
        int year = 2000 + Integer.parseInt(digits.substring(0, 2));
        int month = Integer.parseInt(digits.substring(2, 4));
        int day = Integer.parseInt(digits.substring(4, 6));
        int hour = Integer.parseInt(digits.substring(6, 8));
        int minute = Integer.parseInt(digits.substring(8, 10));
        int second = Integer.parseInt(digits.substring(10, 12));
        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    public static byte[] encodeTime(LocalDateTime time) {
        String digits = String.format("%02d%02d%02d%02d%02d%02d",
                time.getYear() % 100, time.getMonthValue(), time.getDayOfMonth(),
                time.getHour(), time.getMinute(), time.getSecond());
        return encode(digits);
    }

    public static int u8(byte[] data, int offset) {
        return data[offset] & 0xFF;
    }

    public static int u16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    public static long u32(byte[] data, int offset) {
        return ((long) (data[offset] & 0xFF) << 24)
                | ((long) (data[offset + 1] & 0xFF) << 16)
                | ((long) (data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    public static void putU16(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 8) & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }

    public static void putU32(byte[] data, int offset, long value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    /** 十六进制字符串，供原始报文留痕 */
    public static String hex(byte[] data) {
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int value = data[i] & 0xFF;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }

    /** 从 ASCII 区读取定长字符串，去掉 \\0 与首尾空白 */
    public static String ascii(byte[] data, int offset, int length) {
        int end = offset;
        int limit = Math.min(offset + length, data.length);
        while (end < limit && data[end] != 0) {
            end++;
        }
        return new String(data, offset, end - offset, java.nio.charset.StandardCharsets.US_ASCII).trim();
    }

    /** 终端号去前导 0，与库内 terminal_id 对齐 */
    public static String normalizeTerminalId(String digits) {
        String trimmed = digits.replaceFirst("^0+", "");
        return trimmed.isEmpty() ? digits : trimmed;
    }
}
