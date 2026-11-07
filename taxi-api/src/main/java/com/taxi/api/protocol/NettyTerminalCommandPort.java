package com.taxi.api.protocol;

import com.taxi.application.port.TerminalCommandPort;
import com.taxi.protocol808.Bcd;
import com.taxi.protocol808.Jt808Const;
import com.taxi.protocol808.Jt808Encoder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 Netty 会话的终端下行实现：0x9101/0x9102/0x8801/0x8300/0x8F01。
 * <p>会话单节点内存，集群化需按终端路由到持有连接的节点。</p>
 */
@Component
public class NettyTerminalCommandPort implements TerminalCommandPort {

    private static final Logger log = LoggerFactory.getLogger(NettyTerminalCommandPort.class);
    private static final Charset GBK = Charset.forName("GBK");

    private final TerminalSessionRegistry registry;
    private final AtomicInteger seq = new AtomicInteger(100);

    @Value("${media.push-ip:127.0.0.1}")
    private String pushIp = "127.0.0.1";

    @Value("${media.push-port:8554}")
    private int pushPort = 8554;

    public NettyTerminalCommandPort(TerminalSessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean online(String terminalId) {
        Channel channel = registry.get(terminalId);
        return channel != null && channel.isActive();
    }

    @Override
    public boolean requestLive(String terminalId, int channel, int streamType) {
        byte[] ip = pushIp.getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[1 + 1 + 1 + 1 + ip.length + 4];
        int idx = 0;
        body[idx++] = (byte) channel;
        body[idx++] = 1;                 // 音视频类型：视频
        body[idx++] = (byte) streamType;  // 0 主码流 1 子码流
        body[idx++] = (byte) ip.length;
        System.arraycopy(ip, 0, body, idx, ip.length);
        idx += ip.length;
        Bcd.putU16(body, idx, pushPort);
        idx += 2;
        Bcd.putU16(body, idx, pushPort);
        return send(terminalId, Jt808Const.MSG_LIVE_REQUEST, body);
    }

    @Override
    public boolean stopLive(String terminalId, int channel) {
        return send(terminalId, Jt808Const.MSG_LIVE_CONTROL, new byte[]{(byte) channel, 0, 0, 0});
    }

    @Override
    public boolean requestSnapshot(String terminalId, int channel) {
        return send(terminalId, Jt808Const.MSG_SNAPSHOT,
                new byte[]{(byte) channel, 1, 0, 0, 0, 1, 1, 1, 1});
    }

    @Override
    public boolean sendText(String terminalId, String text) {
        byte[] content = (text == null ? "" : text).getBytes(GBK);
        byte[] body = new byte[1 + content.length];
        body[0] = 0x01;
        System.arraycopy(content, 0, body, 1, content.length);
        return send(terminalId, Jt808Const.MSG_TEXT_DOWN, body);
    }

    @Override
    public boolean sendInvoice(String terminalId, String orderNo, String invoiceNo, String pdfUrl) {
        byte[] order = (orderNo == null ? "" : orderNo).getBytes(StandardCharsets.UTF_8);
        byte[] invoice = (invoiceNo == null ? "" : invoiceNo).getBytes(StandardCharsets.UTF_8);
        byte[] url = (pdfUrl == null ? "" : pdfUrl).getBytes(StandardCharsets.UTF_8);
        int len = 1 + order.length + 1 + invoice.length + 2 + url.length;
        if (order.length > 255 || invoice.length > 255 || url.length > 65535) {
            log.warn("发票下发内容超长 terminal={} orderNo={}", terminalId, orderNo);
            return false;
        }
        byte[] body = new byte[len];
        int idx = 0;
        body[idx++] = (byte) order.length;
        System.arraycopy(order, 0, body, idx, order.length);
        idx += order.length;
        body[idx++] = (byte) invoice.length;
        System.arraycopy(invoice, 0, body, idx, invoice.length);
        idx += invoice.length;
        Bcd.putU16(body, idx, url.length);
        idx += 2;
        System.arraycopy(url, 0, body, idx, url.length);
        return send(terminalId, Jt808Const.MSG_INVOICE_DOWN, body);
    }

    private boolean send(String terminalId, int msgId, byte[] body) {
        Channel channel = registry.get(terminalId);
        if (channel == null || !channel.isActive()) {
            log.info("终端不在线，下行未发送 terminal={} msg=0x{}", terminalId, Integer.toHexString(msgId));
            return false;
        }
        try {
            byte[] frame = Jt808Encoder.terminalMessage(msgId, terminalId, seq.incrementAndGet(), body);
            channel.writeAndFlush(Unpooled.wrappedBuffer(frame));
            log.info("下行指令 terminal={} msg=0x{} bodyLen={}", terminalId, Integer.toHexString(msgId), body.length);
            return true;
        } catch (RuntimeException e) {
            log.warn("下行指令失败 terminal={} msg=0x{} 原因={}", terminalId, Integer.toHexString(msgId), e.getMessage());
            return false;
        }
    }
}
