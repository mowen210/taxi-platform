package com.taxi.protocol808;

import java.util.Arrays;

/**
 * 解码后的完整 808 消息。rawHex 为原始帧（含帧头帧尾）的十六进制，用于问题回溯。
 */
public record Jt808Message(MessageHeader header, byte[] body, String rawHex) {

    public int msgId() {
        return header.msgId();
    }

    public String terminalId() {
        return header.terminalId();
    }

    public int msgSeq() {
        return header.msgSeq();
    }

    @Override
    public String toString() {
        return "Jt808Message{msgId=" + Jt808Const.name(msgId())
                + ", terminal=" + terminalId()
                + ", seq=" + msgSeq()
                + ", bodyLen=" + body.length
                + ", body=" + Bcd.hex(Arrays.copyOf(body, Math.min(body.length, 24)))
                + (body.length > 24 ? "..." : "") + '}';
    }
}
