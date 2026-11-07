package com.taxi.application.dto;

/**
 * 808 接入处理结果：result 直接映射到 0x8001 / 0x8100 的结果码。
 */
public record ProtocolResult(int result, String authCode, String message) {

    public static final int OK = 0;
    public static final int FAILURE = 1;

    public static ProtocolResult ok(String message) {
        return new ProtocolResult(OK, null, message);
    }

    public static ProtocolResult ok(String authCode, String message) {
        return new ProtocolResult(OK, authCode, message);
    }

    public static ProtocolResult fail(String message) {
        return new ProtocolResult(FAILURE, null, message);
    }

    public static ProtocolResult fail(int result, String message) {
        return new ProtocolResult(result, null, message);
    }

    public boolean success() {
        return result == OK;
    }
}
