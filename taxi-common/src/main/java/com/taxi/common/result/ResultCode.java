package com.taxi.common.result;

/**
 * 全局错误码。契约只允许以下 7 个码值，业务差异通过 msg 表达，禁止新增码值。
 */
public enum ResultCode {

    /** 成功 */
    SUCCESS(200, "success"),
    /** 参数校验失败 */
    BAD_REQUEST(400, "参数错误"),
    /** 未登录或令牌失效 */
    UNAUTHORIZED(401, "未登录或登录已过期"),
    /** 已登录但无权限 */
    FORBIDDEN(403, "无权限访问"),
    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),
    /** 触发限流 */
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),
    /** 服务端异常 */
    ERROR(500, "服务异常，请稍后重试");

    private final int code;
    private final String msg;

    ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
