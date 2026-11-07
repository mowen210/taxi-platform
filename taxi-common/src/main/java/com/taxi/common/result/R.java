package com.taxi.common.result;

import com.taxi.common.context.TraceContext;

import java.io.Serializable;

/**
 * REST 统一返回体。
 * <p>契约固定为 {"code":200,"msg":"success","data":{},"traceId":"xxx"}，禁止在业务侧自行拼装其他结构。</p>
 *
 * @param <T> 业务数据类型
 */
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，取值见 {@link ResultCode} */
    private int code;
    /** 提示信息 */
    private String msg;
    /** 业务数据 */
    private T data;
    /** 全链路追踪号，与日志 MDC 中的 traceId 一致 */
    private String traceId;

    public R() {
    }

    public R(int code, String msg, T data, String traceId) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.traceId = traceId;
    }

    /** 成功，无数据 */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /** 成功，携带数据 */
    public static <T> R<T> ok(T data) {
        return new R<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data, TraceContext.getTraceId());
    }

    /** 失败，使用预定义错误码 */
    public static <T> R<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMsg());
    }

    /** 失败，使用预定义错误码并覆盖提示信息 */
    public static <T> R<T> fail(ResultCode resultCode, String msg) {
        return fail(resultCode.getCode(), msg);
    }

    /** 失败，自定义错误码与提示信息 */
    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null, TraceContext.getTraceId());
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
