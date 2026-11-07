package com.taxi.common.constant;

/**
 * Redis 键统一管理。禁止在业务代码里拼接字符串字面量。
 * <p>键名规范：{业务域}:{对象}:{标识}，所有键必须设置 TTL（会话类）或明确的清理策略（映射类）。</p>
 */
public final class RedisKeys {

    /** 终端注册鉴权码：terminal:auth:{terminalId} */
    public static final String TERMINAL_AUTH = "terminal:auth:";
    /** 终端会话：terminal:session:{terminalId} -> nodeId|channelId|vehicleId */
    public static final String TERMINAL_SESSION = "terminal:session:";
    /** 终端最新位置：terminal:location:{terminalId} */
    public static final String TERMINAL_LOCATION = "terminal:location:";
    /** 车辆最新位置（监控缓存）：monitor:pos:{vehicleId} */
    public static final String VEHICLE_POSITION = "monitor:pos:";
    /** 用户权限缓存：auth:perm:{userId} */
    public static final String AUTH_PERM = "auth:perm:";
    /** 令牌黑名单：auth:blacklist:{jti} */
    public static final String AUTH_BLACKLIST = "auth:blacklist:";
    /** 登录失败计数：login:fail:{username} */
    public static final String LOGIN_FAIL = "login:fail:";
    /** 接口限流：rate:{name}:{identity} */
    public static final String RATE_LIMIT = "rate:";
    /** 幂等键：idem:{scene}:{key} */
    public static final String IDEMPOTENT = "idem:";
    /** 808 上行流水号去重：808:seq:{terminalId}:{msgId}:{seq} */
    public static final String PROTOCOL_SEQ = "808:seq:";

    private RedisKeys() {
    }

    public static String terminalAuth(String terminalId) {
        return TERMINAL_AUTH + terminalId;
    }

    public static String terminalSession(String terminalId) {
        return TERMINAL_SESSION + terminalId;
    }

    public static String authPerm(Long userId) {
        return AUTH_PERM + userId;
    }

    public static String blacklist(String jti) {
        return AUTH_BLACKLIST + jti;
    }

    public static String loginFail(String username) {
        return LOGIN_FAIL + username;
    }

    public static String rateLimit(String name, String identity) {
        return RATE_LIMIT + name + ":" + identity;
    }

    public static String idempotent(String scene, String key) {
        return IDEMPOTENT + scene + ":" + key;
    }

    public static String terminalLocation(String terminalId) {
        return TERMINAL_LOCATION + terminalId;
    }

    public static String protocolSeq(String terminalId, int msgId, int seq) {
        return PROTOCOL_SEQ + terminalId + ":" + msgId + ":" + seq;
    }

    public static String vehiclePosition(Long vehicleId) {
        return VEHICLE_POSITION + vehicleId;
    }
}
