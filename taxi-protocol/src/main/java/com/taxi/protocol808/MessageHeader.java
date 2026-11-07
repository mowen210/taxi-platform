package com.taxi.protocol808;

/**
 * 808 消息头。
 *
 * <pre>
 * 2013：消息ID(2) 消息体属性(2) 终端号(6 BCD) 流水号(2)
 * 2019：消息ID(2) 消息体属性(2) 协议版本(1) 终端号(6 BCD) 流水号(2)
 * </pre>
 * 消息体属性：bit0-9 长度；bit10-12 加密方式（必须为 0）；bit13 分包；bit14 版本标识。
 */
public record MessageHeader(
        int msgId,
        int props,
        int protocolVersion,
        String terminalId,
        byte[] terminalIdRaw,
        int msgSeq,
        int bodyLength,
        boolean subPackage,
        int totalPackets,
        int packetIndex) {

    public static final int HEADER_LENGTH_2013 = 12;
    public static final int HEADER_LENGTH_2019 = 13;

    public boolean hasVersionFlag() {
        return ((props >> 14) & 0x01) == 1;
    }

    public int encryptionType() {
        return (props >> 10) & 0x07;
    }

    /** 解析消息头 */
    public static MessageHeader parse(byte[] payload) {
        if (payload.length < HEADER_LENGTH_2013) {
            throw new IllegalArgumentException("消息头长度不足：" + payload.length);
        }
        int msgId = Bcd.u16(payload, 0);
        int props = Bcd.u16(payload, 2);
        boolean versionFlag = ((props >> 14) & 0x01) == 1;
        int idx = 4;
        int version = 0;
        if (versionFlag) {
            version = Bcd.u8(payload, idx++);
            if (payload.length < HEADER_LENGTH_2019) {
                throw new IllegalArgumentException("2019 消息头长度不足");
            }
        }
        byte[] raw = new byte[6];
        System.arraycopy(payload, idx, raw, 0, 6);
        idx += 6;
        int seq = Bcd.u16(payload, idx);
        idx += 2;

        int bodyLength = props & 0x03FF;
        boolean subPackage = ((props >> 13) & 0x01) == 1;
        int totalPackets = 0;
        int packetIndex = 0;
        if (subPackage) {
            if (payload.length < idx + 4) {
                throw new IllegalArgumentException("分包消息头长度不足");
            }
            totalPackets = Bcd.u16(payload, idx);
            packetIndex = Bcd.u16(payload, idx + 2);
        }
        String digits = Bcd.decode(raw, 0, 6);
        return new MessageHeader(msgId, props, version, Bcd.normalizeTerminalId(digits), raw, seq,
                bodyLength, subPackage, totalPackets, packetIndex);
    }

    /** 消息头字节长度（含分包附加的 4 字节） */
    public int length() {
        int len = hasVersionFlag() ? HEADER_LENGTH_2019 : HEADER_LENGTH_2013;
        return subPackage ? len + 4 : len;
    }
}
