package com.taxi.protocol808;

/**
 * JT/T 808 帧转义与校验。
 *
 * <pre>
 * 0x7E | 消息头 + 消息体 + 校验码 | 0x7E
 * 校验码 = 消息头与消息体逐字节异或
 * 转义：0x7E -> 0x7D 0x02；0x7D -> 0x7D 0x01
 * </pre>
 */
public final class Jt808FrameCodec {

    private Jt808FrameCodec() {
    }

    /** 逐字节异或校验 */
    public static byte checksum(byte[] data, int offset, int length) {
        byte sum = 0;
        for (int i = offset; i < offset + length; i++) {
            sum ^= data[i];
        }
        return sum;
    }

    /** 转义：仅对 0x7E / 0x7D 处理 */
    public static byte[] escape(byte[] data) {
        int extra = 0;
        for (byte b : data) {
            if (b == Jt808Const.FLAG || b == Jt808Const.ESCAPE) {
                extra++;
            }
        }
        byte[] out = new byte[data.length + extra];
        int idx = 0;
        for (byte b : data) {
            if (b == Jt808Const.FLAG) {
                out[idx++] = Jt808Const.ESCAPE;
                out[idx++] = Jt808Const.ESCAPE_7E;
            } else if (b == Jt808Const.ESCAPE) {
                out[idx++] = Jt808Const.ESCAPE;
                out[idx++] = Jt808Const.ESCAPE_7D;
            } else {
                out[idx++] = b;
            }
        }
        return out;
    }

    /** 反转义 */
    public static byte[] unescape(byte[] data) {
        byte[] out = new byte[data.length];
        int idx = 0;
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (b == Jt808Const.ESCAPE && i + 1 < data.length) {
                byte next = data[++i];
                if (next == Jt808Const.ESCAPE_7E) {
                    out[idx++] = Jt808Const.FLAG;
                } else if (next == Jt808Const.ESCAPE_7D) {
                    out[idx++] = Jt808Const.ESCAPE;
                } else {
                    out[idx++] = b;
                    out[idx++] = next;
                }
            } else {
                out[idx++] = b;
            }
        }
        byte[] result = new byte[idx];
        System.arraycopy(out, 0, result, 0, idx);
        return result;
    }

    /** 组帧：header+body 追加校验并转义，两端补 0x7E */
    public static byte[] encode(byte[] headerAndBody) {
        byte[] withCheck = new byte[headerAndBody.length + 1];
        System.arraycopy(headerAndBody, 0, withCheck, 0, headerAndBody.length);
        withCheck[headerAndBody.length] = checksum(headerAndBody, 0, headerAndBody.length);
        byte[] escaped = escape(withCheck);
        byte[] frame = new byte[escaped.length + 2];
        frame[0] = Jt808Const.FLAG;
        System.arraycopy(escaped, 0, frame, 1, escaped.length);
        frame[frame.length - 1] = Jt808Const.FLAG;
        return frame;
    }

    /**
     * 去掉首尾 0x7E、反转义并校验，返回「消息头 + 消息体」。
     *
     * @throws IllegalArgumentException 帧不完整或校验失败
     */
    public static byte[] decode(byte[] frame) {
        if (frame == null || frame.length < 4) {
            throw new IllegalArgumentException("帧长度不足");
        }
        int start = frame[0] == Jt808Const.FLAG ? 1 : 0;
        int end = frame[frame.length - 1] == Jt808Const.FLAG ? frame.length - 1 : frame.length;
        byte[] inner = new byte[end - start];
        System.arraycopy(frame, start, inner, 0, inner.length);
        byte[] data = unescape(inner);
        if (data.length < 2) {
            throw new IllegalArgumentException("帧内容不足");
        }
        byte expected = checksum(data, 0, data.length - 1);
        byte actual = data[data.length - 1];
        if (expected != actual) {
            throw new IllegalArgumentException(String.format("校验码错误：期望 %02X 实际 %02X", expected, actual));
        }
        byte[] payload = new byte[data.length - 1];
        System.arraycopy(data, 0, payload, 0, payload.length);
        return payload;
    }
}
