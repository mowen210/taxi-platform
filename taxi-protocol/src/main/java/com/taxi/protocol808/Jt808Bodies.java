package com.taxi.protocol808;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 本阶段涉及的消息体编解码。
 *
 * <p>905 营运报文（0x0B02 / 0x0B05）的字段顺序按项目约定实现，与模拟器严格一致；
 * 厂商实际报文若有差异，以 {@code t_taximeter_raw.raw_hex} 留痕后在此处调整偏移，
 * 不改动上层业务。</p>
 */
public final class Jt808Bodies {

    private static final Charset GBK = Charset.forName("GBK");
    /** 905 营运 ID 定长 */
    public static final int BUSINESS_ID_LEN = 32;
    /** 0x0B02 消息体长度：32 + 6 + 4 + 4 + 1 */
    public static final int OPERATION_START_LEN = 47;
    /** 0x0B05 消息体长度：32 + 6 + 4 + 4 + 4 + 2 + 4 + 4 */
    public static final int OPERATION_END_LEN = 60;

    private Jt808Bodies() {
    }

    private static void require(byte[] body, int min, String name) {
        if (body.length < min) {
            throw new IllegalArgumentException(name + " 消息体长度不足：至少 " + min + " 字节，实际 " + body.length);
        }
    }

    private static BigDecimal coord(long raw, boolean negative) {
        BigDecimal value = BigDecimal.valueOf(raw).movePointLeft(6).setScale(6, RoundingMode.HALF_UP);
        return negative ? value.negate() : value;
    }

    private static BigDecimal money(long fen) {
        return BigDecimal.valueOf(fen).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** 0x0100 终端注册 */
    public record Register(int provinceId, int cityId, String manufacturer, String model,
                           String terminalId, int plateColor, String plateNo) {

        public static Register parse(byte[] body) {
            require(body, 37, "0x0100 注册");
            int province = Bcd.u16(body, 0);
            int city = Bcd.u16(body, 2);
            String manufacturer = Bcd.ascii(body, 4, 5);
            String model = Bcd.ascii(body, 9, 20);
            String terminalId = Bcd.ascii(body, 29, 7);
            int plateColor = Bcd.u8(body, 36);
            String plateNo = body.length > 37 ? new String(body, 37, body.length - 37, GBK).trim() : "";
            return new Register(province, city, manufacturer, model,
                    Bcd.normalizeTerminalId(terminalId), plateColor, plateNo);
        }
    }

    /** 0x0102 终端鉴权：兼容「1 字节长度 + 鉴权码」与「纯鉴权码」两种写法 */
    public record Auth(String authCode) {

        public static Auth parse(byte[] body) {
            require(body, 1, "0x0102 鉴权");
            int len = Bcd.u8(body, 0);
            if (len > 0 && body.length == len + 1) {
                return new Auth(new String(body, 1, len, StandardCharsets.US_ASCII).trim());
            }
            return new Auth(new String(body, 0, body.length, StandardCharsets.US_ASCII).trim());
        }
    }

    /** 0x0200 位置汇报（基础 28 字节） */
    public record Location(long alarm, long status, BigDecimal lng, BigDecimal lat,
                           int altitude, int speed, int direction, LocalDateTime time) {

        public static Location parse(byte[] body) {
            require(body, 28, "0x0200 位置");
            long alarm = Bcd.u32(body, 0);
            long status = Bcd.u32(body, 4);
            long latRaw = Bcd.u32(body, 8);
            long lngRaw = Bcd.u32(body, 12);
            boolean south = (status & 0x0004L) == 0;
            boolean west = (status & 0x0008L) == 0;
            return new Location(alarm, status, coord(lngRaw, west), coord(latRaw, south),
                    Bcd.u16(body, 16), Bcd.u16(body, 18), Bcd.u16(body, 20), Bcd.decodeTime(body, 22));
        }
    }

    /** 0x0B02 营运开始 */
    public record OperationStart(String businessId, LocalDateTime startTime,
                                 BigDecimal lng, BigDecimal lat, int taximeterState) {

        public static OperationStart parse(byte[] body) {
            require(body, OPERATION_START_LEN, "0x0B02 营运开始");
            String businessId = Bcd.ascii(body, 0, BUSINESS_ID_LEN);
            LocalDateTime time = Bcd.decodeTime(body, BUSINESS_ID_LEN);
            long latRaw = Bcd.u32(body, BUSINESS_ID_LEN + 6);
            long lngRaw = Bcd.u32(body, BUSINESS_ID_LEN + 10);
            int state = Bcd.u8(body, BUSINESS_ID_LEN + 14);
            return new OperationStart(businessId, time, coord(lngRaw, false), coord(latRaw, false), state);
        }
    }

    /** 0x0B05 营运结束 */
    public record OperationEnd(String businessId, LocalDateTime endTime, BigDecimal lng, BigDecimal lat,
                               BigDecimal mileageKm, int waitingSeconds,
                               BigDecimal totalAmount, BigDecimal discountAmount) {

        public static OperationEnd parse(byte[] body) {
            require(body, OPERATION_END_LEN, "0x0B05 营运结束");
            String businessId = Bcd.ascii(body, 0, BUSINESS_ID_LEN);
            LocalDateTime time = Bcd.decodeTime(body, BUSINESS_ID_LEN);
            long latRaw = Bcd.u32(body, BUSINESS_ID_LEN + 6);
            long lngRaw = Bcd.u32(body, BUSINESS_ID_LEN + 10);
            BigDecimal mileage = BigDecimal.valueOf(Bcd.u32(body, BUSINESS_ID_LEN + 14))
                    .movePointLeft(1).setScale(1, RoundingMode.UNNECESSARY);
            int waiting = Bcd.u16(body, BUSINESS_ID_LEN + 18);
            BigDecimal amount = money(Bcd.u32(body, BUSINESS_ID_LEN + 20));
            BigDecimal discount = money(Bcd.u32(body, BUSINESS_ID_LEN + 24));
            return new OperationEnd(businessId, time, coord(lngRaw, false), coord(latRaw, false),
                    mileage, waiting, amount, discount);
        }
    }

    /** 0x0801 多媒体数据上传：基础字段 + 0x0200 位置块 + 媒体数据 */
    public record MediaUpload(long mediaId, int mediaType, int format, int event, int channel,
                              BigDecimal lng, BigDecimal lat, LocalDateTime time, byte[] data) {

        public static MediaUpload parse(byte[] body) {
            require(body, 36, "0x0801 多媒体上传");
            long id = Bcd.u32(body, 0);
            int type = Bcd.u8(body, 4);
            int format = Bcd.u8(body, 5);
            int event = Bcd.u8(body, 6);
            int channel = Bcd.u8(body, 7);
            long status = Bcd.u32(body, 12);
            long latRaw = Bcd.u32(body, 16);
            long lngRaw = Bcd.u32(body, 20);
            boolean south = (status & 0x0004L) == 0;
            boolean west = (status & 0x0008L) == 0;
            LocalDateTime time = Bcd.decodeTime(body, 30);
            byte[] data = new byte[body.length - 36];
            System.arraycopy(body, 36, data, 0, data.length);
            return new MediaUpload(id, type, format, event, channel,
                    coord(lngRaw, west), coord(latRaw, south), time, data);
        }
    }

    /** 0x1210 报警附件信息：报警编号(32) + 附件数量 + [文件名长度+文件名+文件大小] */
    public record AttachmentInfo(String alarmKey, java.util.List<FileInfo> files) {

        public static AttachmentInfo parse(byte[] body) {
            require(body, 33, "0x1210 报警附件信息");
            String alarmKey = Bcd.ascii(body, 0, 32).trim();
            int count = Bcd.u8(body, 32);
            int idx = 33;
            java.util.List<FileInfo> files = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                if (idx >= body.length) {
                    break;
                }
                int len = Bcd.u8(body, idx++);
                if (idx + len + 4 > body.length) {
                    throw new IllegalArgumentException("0x1210 附件信息长度不足");
                }
                String name = new String(body, idx, len, StandardCharsets.UTF_8);
                idx += len;
                long size = Bcd.u32(body, idx);
                idx += 4;
                files.add(new FileInfo(name, size));
            }
            return new AttachmentInfo(alarmKey, files);
        }
    }

    public record FileInfo(String name, long size) {
    }

    /** 0x1211 附件分片：文件名长度 + 文件名 + 偏移 + 数据长度 + 数据 */
    public record AttachmentPacket(String fileName, long offset, byte[] data) {

        public static AttachmentPacket parse(byte[] body) {
            require(body, 10, "0x1211 附件分片");
            int len = Bcd.u8(body, 0);
            if (1 + len + 8 > body.length) {
                throw new IllegalArgumentException("0x1211 分片长度不足");
            }
            String name = new String(body, 1, len, StandardCharsets.UTF_8);
            int idx = 1 + len;
            long offset = Bcd.u32(body, idx);
            int dataLen = (int) Bcd.u32(body, idx + 4);
            idx += 8;
            if (idx + dataLen > body.length) {
                throw new IllegalArgumentException("0x1211 数据长度超出消息体");
            }
            byte[] data = new byte[dataLen];
            System.arraycopy(body, idx, data, 0, dataLen);
            return new AttachmentPacket(name, offset, data);
        }
    }

    /** 0x1212 附件完成：文件名长度 + 文件名 + 文件大小 */
    public record AttachmentComplete(String fileName, long size) {

        public static AttachmentComplete parse(byte[] body) {
            require(body, 6, "0x1212 附件完成");
            int len = Bcd.u8(body, 0);
            if (1 + len + 4 > body.length) {
                throw new IllegalArgumentException("0x1212 消息长度不足");
            }
            String name = new String(body, 1, len, StandardCharsets.UTF_8);
            long size = Bcd.u32(body, 1 + len);
            return new AttachmentComplete(name, size);
        }
    }
}
