package com.taxi.api.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 808 粘包/半包处理：以 0x7E 为帧界，取出含首尾标识的完整帧。
 * <p>转义后的 0x7D02 不会包含 0x7E，因此直接按 0x7E 扫描是安全的；
 * 反转义与校验在 {@code Jt808Decoder} 中完成。</p>
 */
public class Jt808FrameDecoder extends ByteToMessageDecoder {

    private static final Logger log = LoggerFactory.getLogger(Jt808FrameDecoder.class);
    private static final int FLAG = 0x7E;
    private static final int MAX_FRAME = 8192;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (true) {
            int start = indexOfFlag(in, 0);
            if (start < 0) {
                in.skipBytes(in.readableBytes());
                return;
            }
            if (start > 0) {
                in.skipBytes(start);
            }
            if (in.readableBytes() < 2) {
                return;
            }
            int end = indexOfFlag(in, 1);
            if (end < 0) {
                if (in.readableBytes() > MAX_FRAME) {
                    log.warn("帧超长且未收尾，断开连接 remote={}", ctx.channel().remoteAddress());
                    in.skipBytes(in.readableBytes());
                    ctx.close();
                }
                return;
            }
            if (end == 1) {
                in.skipBytes(2);
                continue;
            }
            byte[] frame = new byte[end + 1];
            in.readBytes(frame);
            out.add(frame);
        }
    }

    private int indexOfFlag(ByteBuf in, int from) {
        for (int i = from; i < in.readableBytes(); i++) {
            if ((in.getByte(in.readerIndex() + i) & 0xFF) == FLAG) {
                return i;
            }
        }
        return -1;
    }
}
