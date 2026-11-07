package com.taxi.application.port;

/**
 * 终端下行能力端口。应用层只表达业务意图，由 API 层用 Netty 会话实现协议编码与发送。
 * <p>终端离线或协议不支持时返回 false，由调用方决定降级或重试。</p>
 */
public interface TerminalCommandPort {

    boolean online(String terminalId);

    boolean requestLive(String terminalId, int channel, int streamType);

    boolean stopLive(String terminalId, int channel);

    boolean requestSnapshot(String terminalId, int channel);

    boolean sendText(String terminalId, String text);

    /** 0x8F01 发票下发；失败时调用方可降级 {@link #sendText(String, String)} */
    boolean sendInvoice(String terminalId, String orderNo, String invoiceNo, String pdfUrl);
}
