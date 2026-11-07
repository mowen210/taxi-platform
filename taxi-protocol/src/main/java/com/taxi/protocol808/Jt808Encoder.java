package com.taxi.protocol808;

import java.nio.charset.StandardCharsets;

/**
 * 平台下发报文编码（0x8001 通用应答 / 0x8100 注册应答）。
 * 应答沿用请求的协议版本标识，保证 2013 与 2019 终端都能识别。
 */
public final class Jt808Encoder {

    private Jt808Encoder() {
    }

    /** 0x8001 通用应答 */
    public static byte[] generalResponse(MessageHeader request, int replyMsgId, int result) {
        byte[] body = new byte[5];
        Bcd.putU16(body, 0, request.msgSeq());
        Bcd.putU16(body, 2, replyMsgId);
        body[4] = (byte) result;
        return message(Jt808Const.MSG_GENERAL_RESP, request, body);
    }

    /** 0x8100 注册应答：成功时携带鉴权码 */
    public static byte[] registerResponse(MessageHeader request, int result, String authCode) {
        byte[] code = authCode == null ? new byte[0] : authCode.getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[3 + code.length];
        Bcd.putU16(body, 0, request.msgSeq());
        body[2] = (byte) result;
        System.arraycopy(code, 0, body, 3, code.length);
        return message(Jt808Const.MSG_REGISTER_RESP, request, body);
    }

    /**
     * 平台主动下发：按 2013 版消息头组装（12 字节，无版本标识，兼容性最好）。
     * 终端号码统一补足 12 位 BCD。
     */
    public static byte[] terminalMessage(int msgId, String terminalId, int seq, byte[] body) {
        String digits = terminalId == null ? "" : terminalId.replaceAll("\\D", "");
        if (digits.length() > 12) {
            digits = digits.substring(digits.length() - 12);
        }
        digits = String.format("%012d", digits.isEmpty() ? 0L : Long.parseLong(digits));
        byte[] raw = Bcd.encode(digits);
        byte[] payload = new byte[12 + body.length];
        Bcd.putU16(payload, 0, msgId);
        Bcd.putU16(payload, 2, body.length & 0x03FF);
        System.arraycopy(raw, 0, payload, 4, 6);
        Bcd.putU16(payload, 10, seq & 0xFFFF);
        System.arraycopy(body, 0, payload, 12, body.length);
        return Jt808FrameCodec.encode(payload);
    }

    /** 组装完整帧（含帧头帧尾与校验） */
    public static byte[] message(int msgId, MessageHeader request, byte[] body) {
        boolean versionFlag = request.hasVersionFlag();
        int props = body.length & 0x03FF;
        if (versionFlag) {
            props |= 1 << 14;
        }
        int headerLen = versionFlag ? MessageHeader.HEADER_LENGTH_2019 : MessageHeader.HEADER_LENGTH_2013;
        byte[] headerAndBody = new byte[headerLen + body.length];
        Bcd.putU16(headerAndBody, 0, msgId);
        Bcd.putU16(headerAndBody, 2, props);
        int idx = 4;
        if (versionFlag) {
            headerAndBody[idx++] = (byte) request.protocolVersion();
        }
        System.arraycopy(request.terminalIdRaw(), 0, headerAndBody, idx, 6);
        idx += 6;
        Bcd.putU16(headerAndBody, idx, request.msgSeq());
        idx += 2;
        System.arraycopy(body, 0, headerAndBody, idx, body.length);
        return Jt808FrameCodec.encode(headerAndBody);
    }
}
