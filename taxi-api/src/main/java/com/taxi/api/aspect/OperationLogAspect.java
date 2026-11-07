package com.taxi.api.aspect;

import com.taxi.common.context.LoginUser;
import com.taxi.common.context.TraceContext;
import com.taxi.common.context.UserContext;
import com.taxi.common.util.JsonUtil;
import com.taxi.domain.entity.OperationLog;
import com.taxi.domain.repository.OperationLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 操作日志切面。
 * <p>记录请求参数、响应快照、耗时、traceId、操作人与来源 IP；快照按 4000 字符截断。
 * 日志写入失败只打 WARN，绝不影响业务结果。</p>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);
    private static final int SNAPSHOT_LIMIT = 4000;

    private final OperationLogRepository operationLogRepository;

    public OperationLogAspect(OperationLogRepository operationLogRepository) {
        this.operationLogRepository = operationLogRepository;
    }

    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint joinPoint, OpLog opLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            saveLog(joinPoint, opLog, result, error, System.currentTimeMillis() - start);
        }
    }

    private void saveLog(ProceedingJoinPoint joinPoint, OpLog opLog, Object result, Throwable error, long costMs) {
        try {
            OperationLog entity = new OperationLog();
            entity.setTraceId(TraceContext.getTraceId());
            LoginUser user = UserContext.get();
            if (user != null) {
                entity.setUserId(user.userId());
                entity.setUserName(user.username());
            }
            entity.setModule(opLog.module());
            entity.setAction(opLog.action());
            entity.setBizType(opLog.bizType().isBlank() ? null : opLog.bizType());
            entity.setMethod(joinPoint.getSignature().getDeclaringType().getSimpleName() + "." + joinPoint.getSignature().getName());
            entity.setResult(error == null ? 1 : 0);
            entity.setCostMs(costMs);
            entity.setOpTime(LocalDateTime.now());

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                entity.setRequestUri(request.getRequestURI());
                entity.setIp(clientIp(request));
                entity.setUserAgent(JsonUtil.truncate(request.getHeader("User-Agent"), 500));
            }
            entity.setRequestParam(JsonUtil.truncate(safeArgs(joinPoint.getArgs()), SNAPSHOT_LIMIT));
            if (error != null) {
                entity.setErrorMsg(JsonUtil.truncate(error.getMessage(), 1000));
            } else {
                entity.setResponseSnapshot(JsonUtil.truncate(JsonUtil.toJson(result), SNAPSHOT_LIMIT));
            }
            operationLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("操作日志写入失败 module={} action={} reason={}", opLog.module(), opLog.action(), e.getMessage());
        }
    }

    /** 过滤不可序列化参数，避免日志切面把请求流打爆 */
    private String safeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        Object[] filtered = Arrays.stream(args)
                .map(arg -> {
                    if (arg instanceof HttpServletRequest || arg instanceof MultipartFile) {
                        return "<ignored>";
                    }
                    return arg;
                })
                .toArray();
        return JsonUtil.toJson(filtered);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
