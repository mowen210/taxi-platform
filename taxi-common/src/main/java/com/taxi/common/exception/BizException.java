package com.taxi.common.exception;

import com.taxi.common.result.ResultCode;

/**
 * 业务异常。由 {@code GlobalExceptionHandler} 统一转换为 {@link com.taxi.common.result.R}。
 * <p>业务代码只允许抛出本异常表达可预期失败，未知异常一律向上抛由框架兜底为 500。</p>
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = ResultCode.BAD_REQUEST.getCode();
    }

    public BizException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /** 参数错误快捷构造 */
    public static BizException badRequest(String message) {
        return new BizException(ResultCode.BAD_REQUEST, message);
    }

    /** 未登录快捷构造 */
    public static BizException unauthorized(String message) {
        return new BizException(ResultCode.UNAUTHORIZED, message);
    }

    /** 无权限快捷构造 */
    public static BizException forbidden(String message) {
        return new BizException(ResultCode.FORBIDDEN, message);
    }

    /** 不存在快捷构造 */
    public static BizException notFound(String message) {
        return new BizException(ResultCode.NOT_FOUND, message);
    }
}
