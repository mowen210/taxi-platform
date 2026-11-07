package com.taxi.protocol808;

/**
 * 808 解码入口：整帧 → {@link Jt808Message}。
 * 分包由 {@link SubPacketAssembler} 负责重组，本类只做单包解析。
 */
public final class Jt808Decoder {

    private Jt808Decoder() {
    }

    /** 解码完整帧（含首尾 0x7E） */
    public static Jt808Message decode(byte[] frame) {
        byte[] payload = Jt808FrameCodec.decode(frame);
        return decodePayload(payload, Bcd.hex(frame));
    }

    /** 解码已去帧头、去校验的载荷 */
    public static Jt808Message decodePayload(byte[] payload, String rawHex) {
        MessageHeader header = MessageHeader.parse(payload);
        int headerLen = header.length();
        int bodyLen = payload.length - headerLen;
        if (header.encryptionType() != 0) {
            throw new IllegalArgumentException("暂不支持的加密方式：" + header.encryptionType());
        }
        if (!header.subPackage() && header.bodyLength() != bodyLen) {
            throw new IllegalArgumentException("消息体长度不符：声明 " + header.bodyLength() + " 实际 " + bodyLen);
        }
        byte[] body = new byte[bodyLen];
        System.arraycopy(payload, headerLen, body, 0, bodyLen);
        return new Jt808Message(header, body, rawHex);
    }
}
