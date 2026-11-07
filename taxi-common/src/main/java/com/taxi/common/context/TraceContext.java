package com.taxi.common.context;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 全链路追踪上下文。
 * <p>traceId 在接入层（REST 过滤器 / 808 报文入口）生成，串通报文解析、报警、开票、指令下发日志。</p>
 */
public final class TraceContext {

    /** MDC 键名，logback pattern 中使用 %X{traceId} */
    public static final String TRACE_ID_KEY = "traceId";

    private TraceContext() {
    }

    /** 生成 32 位无横线 traceId */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 绑定 traceId 到当前线程，返回实际生效值 */
    public static String bind(String traceId) {
        String value = (traceId == null || traceId.isBlank()) ? generate() : traceId;
        MDC.put(TRACE_ID_KEY, value);
        return value;
    }

    /** 获取当前线程 traceId，未绑定时返回 null */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /** 清理，必须在 finally 中调用，避免线程池复用串号 */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }
}
