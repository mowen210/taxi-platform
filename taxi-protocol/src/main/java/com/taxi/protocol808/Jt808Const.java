package com.taxi.protocol808;

/**
 * JT/T 808 消息 ID 与常量。
 * <p>只收录本阶段（注册鉴权心跳定位 + 905 营运）用到的消息，其余按需增补。</p>
 */
public final class Jt808Const {

    /** 帧头帧尾 */
    public static final byte FLAG = 0x7E;
    /** 转义字节 */
    public static final byte ESCAPE = 0x7D;
    /** 0x7D02 -> 0x7E */
    public static final byte ESCAPE_7E = 0x02;
    /** 0x7D01 -> 0x7D */
    public static final byte ESCAPE_7D = 0x01;

    /** 终端注册 */
    public static final int MSG_REGISTER = 0x0100;
    /** 终端注册应答 */
    public static final int MSG_REGISTER_RESP = 0x8100;
    /** 终端鉴权 */
    public static final int MSG_AUTH = 0x0102;
    /** 终端心跳 */
    public static final int MSG_HEARTBEAT = 0x0002;
    /** 位置信息汇报 */
    public static final int MSG_LOCATION = 0x0200;
    /** 平台通用应答 */
    public static final int MSG_GENERAL_RESP = 0x8001;
    /** 905 营运开始（接到乘客） */
    public static final int MSG_OPERATION_START = 0x0B02;
    /** 905 营运结束 */
    public static final int MSG_OPERATION_END = 0x0B05;
    /** 808 多媒体数据上传 */
    public static final int MSG_MEDIA_UPLOAD = 0x0801;
    /** 1078 报警附件信息 */
    public static final int MSG_ATTACHMENT_INFO = 0x1210;
    /** 1078 附件分片 */
    public static final int MSG_ATTACHMENT_PACKET = 0x1211;
    /** 1078 附件完成 */
    public static final int MSG_ATTACHMENT_COMPLETE = 0x1212;

    /** 1078 实时音视频传输请求 */
    public static final int MSG_LIVE_REQUEST = 0x9101;
    /** 1078 实时音视频传输控制 */
    public static final int MSG_LIVE_CONTROL = 0x9102;
    /** 808 摄像头立即拍摄 */
    public static final int MSG_SNAPSHOT = 0x8801;
    /** 808 文本信息下发 */
    public static final int MSG_TEXT_DOWN = 0x8300;
    /** 发票信息下发（项目约定） */
    public static final int MSG_INVOICE_DOWN = 0x8F01;

    /** 注册结果：成功 */
    public static final int RESULT_OK = 0;
    /** 注册结果：车辆未备案 */
    public static final int RESULT_VEHICLE_NOT_FOUND = 2;
    /** 通用应答：成功 */
    public static final int RESULT_SUCCESS = 0;
    /** 通用应答：失败 */
    public static final int RESULT_FAILURE = 1;
    /** 通用应答：消息有误 */
    public static final int RESULT_BAD_MESSAGE = 2;

    private Jt808Const() {
    }

    public static String name(int msgId) {
        return switch (msgId) {
            case MSG_REGISTER -> "0x0100 终端注册";
            case MSG_REGISTER_RESP -> "0x8100 注册应答";
            case MSG_AUTH -> "0x0102 终端鉴权";
            case MSG_HEARTBEAT -> "0x0002 心跳";
            case MSG_LOCATION -> "0x0200 位置汇报";
            case MSG_GENERAL_RESP -> "0x8001 通用应答";
            case MSG_OPERATION_START -> "0x0B02 营运开始";
            case MSG_OPERATION_END -> "0x0B05 营运结束";
            case MSG_MEDIA_UPLOAD -> "0x0801 多媒体上传";
            case MSG_ATTACHMENT_INFO -> "0x1210 报警附件信息";
            case MSG_ATTACHMENT_PACKET -> "0x1211 附件分片";
            case MSG_ATTACHMENT_COMPLETE -> "0x1212 附件完成";
            case MSG_LIVE_REQUEST -> "0x9101 实时音视频请求";
            case MSG_LIVE_CONTROL -> "0x9102 实时音视频控制";
            case MSG_SNAPSHOT -> "0x8801 立即拍摄";
            case MSG_TEXT_DOWN -> "0x8300 文本下发";
            case MSG_INVOICE_DOWN -> "0x8F01 发票下发";
            default -> String.format("0x%04X", msgId);
        };
    }
}
