package com.taxi.api.advice;

import com.taxi.common.context.TraceContext;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.R;
import com.taxi.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常拦截。
 * <p>统一口径：HTTP 状态恒为 200，业务结果由响应体 code 表达（200/400/401/403/404/429/500），
 * 前端只需判断 body.code；traceId 一并返回便于定位。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 可预期业务异常 */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        log.warn("业务异常 code={} msg={} traceId={}", e.getCode(), e.getMessage(), TraceContext.getTraceId());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** @RequestBody 参数校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数校验失败 {}", message);
        return R.fail(ResultCode.BAD_REQUEST, message.isEmpty() ? "参数错误" : message);
    }

    /** 表单/查询参数绑定失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("；"));
        log.warn("参数绑定失败 {}", message);
        return R.fail(ResultCode.BAD_REQUEST, message.isEmpty() ? "参数错误" : message);
    }

    /** 方法参数级约束（@Validated 在类上时触发） */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        log.warn("参数约束失败 {}", message);
        return R.fail(ResultCode.BAD_REQUEST, message.isEmpty() ? "参数错误" : message);
    }

    /** 请求体不是合法 JSON */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败 {}", e.getMessage());
        return R.fail(ResultCode.BAD_REQUEST, "请求体格式错误");
    }

    /** 缺少必填查询参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ResultCode.BAD_REQUEST, "缺少参数：" + e.getParameterName());
    }

    /** 请求方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return R.fail(ResultCode.BAD_REQUEST, "不支持的请求方法：" + e.getMethod());
    }

    /** 静态资源/路由不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResource(NoResourceFoundException e) {
        return R.fail(ResultCode.NOT_FOUND, "接口不存在：" + e.getResourcePath());
    }

    /** 兜底异常：只暴露通用提示，细节进日志 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常 traceId={} uri={}", TraceContext.getTraceId(), e);
        return R.fail(ResultCode.ERROR, "服务异常，请稍后重试（traceId: " + TraceContext.getTraceId() + "）");
    }

    private static String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
