package com.taxi.api.protocol;

import com.taxi.application.dto.ProtocolResult;
import com.taxi.application.service.Protocol808AppService;
import com.taxi.protocol808.Bcd;
import com.taxi.protocol808.Jt808Bodies;
import com.taxi.protocol808.Jt808Const;
import com.taxi.protocol808.Jt808Decoder;
import com.taxi.protocol808.Jt808Encoder;
import com.taxi.protocol808.Jt808Message;
import com.taxi.protocol808.SubPacketAssembler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * 808 上行报文入口：解码 → 分包重组 → 按消息 ID 分派 → 回 0x8001/0x8100。
 * <p>业务异常只回失败结果码并记录日志，不关闭连接，避免终端因单条脏数据反复重连。</p>
 */
public class Jt808ServerHandler extends SimpleChannelInboundHandler<byte[]> {

    private static final Logger log = LoggerFactory.getLogger(Jt808ServerHandler.class);

    private final Protocol808AppService appService;
    private final TerminalSessionRegistry sessionRegistry;
    private final SubPacketAssembler assembler = new SubPacketAssembler(Duration.ofSeconds(30));

    public Jt808ServerHandler(Protocol808AppService appService, TerminalSessionRegistry sessionRegistry) {
        this.appService = appService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("终端连接建立 remote={}", ctx.channel().remoteAddress());
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        sessionRegistry.removeByChannel(ctx.channel());
        log.info("终端连接断开 remote={} 在线={}", ctx.channel().remoteAddress(), sessionRegistry.onlineCount());
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] frame) {
        Jt808Message message;
        try {
            message = Jt808Decoder.decode(frame);
        } catch (RuntimeException e) {
            log.warn("808 帧解析失败 remote={} 原因={} hex={}", ctx.channel().remoteAddress(), e.getMessage(), Bcd.hex(frame));
            return;
        }
        Jt808Message full;
        try {
            full = assembler.accept(message);
        } catch (RuntimeException e) {
            log.warn("808 分包处理失败 terminal={} 原因={}", message.terminalId(), e.getMessage());
            return;
        }
        if (full == null) {
            log.debug("分包未收齐 terminal={} msgId={} seq={}", message.terminalId(),
                    Jt808Const.name(message.msgId()), message.msgSeq());
            return;
        }
        try {
            dispatch(ctx, full);
        } catch (IllegalArgumentException e) {
            log.warn("808 消息体解析失败 terminal={} msg={} 原因={}", full.terminalId(),
                    Jt808Const.name(full.msgId()), e.getMessage());
            writeResponse(ctx, full, ProtocolResult.fail(Jt808Const.RESULT_BAD_MESSAGE, e.getMessage()));
        } catch (RuntimeException e) {
            log.error("808 业务处理异常 terminal={} msg={}", full.terminalId(), Jt808Const.name(full.msgId()), e);
            writeResponse(ctx, full, ProtocolResult.fail("平台处理异常"));
        }
    }

    private void dispatch(ChannelHandlerContext ctx, Jt808Message message) {
        String terminalId = message.terminalId();
        int seq = message.msgSeq();
        String channelKey = ctx.channel().id().asShortText();
        ProtocolResult result;
        switch (message.msgId()) {
            case Jt808Const.MSG_REGISTER -> {
                Jt808Bodies.Register body = Jt808Bodies.Register.parse(message.body());
                // 身份以消息头中的终端手机号为准；消息体里的 7 字节终端 ID 只是设备序列号
                result = appService.register(terminalId,
                        body.manufacturer(), body.model(), body.plateColor(), body.plateNo());
            }
            case Jt808Const.MSG_AUTH -> {
                Jt808Bodies.Auth body = Jt808Bodies.Auth.parse(message.body());
                result = appService.auth(terminalId, body.authCode(), channelKey);
                if (result.success()) {
                    sessionRegistry.bind(terminalId, ctx.channel());
                }
            }
            case Jt808Const.MSG_HEARTBEAT -> result = appService.heartbeat(terminalId, channelKey);
            case Jt808Const.MSG_LOCATION -> {
                Jt808Bodies.Location body = Jt808Bodies.Location.parse(message.body());
                result = appService.location(terminalId, body.lng(), body.lat(), body.speed(),
                        body.direction(), body.altitude(), body.alarm(), body.status(), body.time(),
                        message.rawHex());
            }
            case Jt808Const.MSG_MEDIA_UPLOAD -> {
                Jt808Bodies.MediaUpload body = Jt808Bodies.MediaUpload.parse(message.body());
                result = appService.mediaUpload(terminalId, body.mediaId(), body.mediaType(), body.format(),
                        body.event(), body.channel(), body.data(), body.time());
            }
            case Jt808Const.MSG_ATTACHMENT_INFO -> {
                Jt808Bodies.AttachmentInfo body = Jt808Bodies.AttachmentInfo.parse(message.body());
                result = appService.attachmentInfo(terminalId, body.alarmKey(),
                        body.files().stream().map(Jt808Bodies.FileInfo::name).toList());
            }
            case Jt808Const.MSG_ATTACHMENT_PACKET -> {
                Jt808Bodies.AttachmentPacket body = Jt808Bodies.AttachmentPacket.parse(message.body());
                result = appService.attachmentPacket(terminalId, body.fileName(), body.offset(), body.data());
            }
            case Jt808Const.MSG_ATTACHMENT_COMPLETE -> {
                Jt808Bodies.AttachmentComplete body = Jt808Bodies.AttachmentComplete.parse(message.body());
                result = appService.attachmentComplete(terminalId, body.fileName(), body.size());
            }
            case Jt808Const.MSG_OPERATION_START -> {
                Jt808Bodies.OperationStart body = Jt808Bodies.OperationStart.parse(message.body());
                result = appService.operationStart(terminalId, body.businessId(), body.startTime(),
                        body.lng(), body.lat(), seq, message.rawHex());
            }
            case Jt808Const.MSG_OPERATION_END -> {
                Jt808Bodies.OperationEnd body = Jt808Bodies.OperationEnd.parse(message.body());
                result = appService.operationEnd(terminalId, body.businessId(), body.endTime(),
                        body.lng(), body.lat(), body.mileageKm(), body.waitingSeconds(),
                        body.totalAmount(), body.discountAmount(), seq, message.rawHex());
            }
            default -> {
                log.warn("未支持的 808 消息 terminal={} msg={}", terminalId, Jt808Const.name(message.msgId()));
                result = ProtocolResult.fail(Jt808Const.RESULT_BAD_MESSAGE, "平台不支持该消息");
            }
        }
        writeResponse(ctx, message, result);
    }

    private void writeResponse(ChannelHandlerContext ctx, Jt808Message request, ProtocolResult result) {
        int replyMsgId = request.msgId() == Jt808Const.MSG_REGISTER
                ? Jt808Const.MSG_REGISTER_RESP : Jt808Const.MSG_GENERAL_RESP;
        byte[] frame = request.msgId() == Jt808Const.MSG_REGISTER
                ? Jt808Encoder.registerResponse(request.header(), result.result(), result.authCode())
                : Jt808Encoder.generalResponse(request.header(), request.msgId(), result.result());
        ctx.writeAndFlush(io.netty.buffer.Unpooled.wrappedBuffer(frame));
        log.info("808 应答 terminal={} 请求={} 应答={} result={} 说明={}", request.terminalId(),
                Jt808Const.name(request.msgId()), Jt808Const.name(replyMsgId), result.result(), result.message());
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            log.info("终端心跳超时，关闭连接 remote={}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.warn("808 连接异常 remote={} 原因={}", ctx.channel().remoteAddress(), cause.getMessage());
        ctx.close();
    }
}
