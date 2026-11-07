package com.taxi.common.context;

/**
 * 当前请求登录用户上下文。
 * <p>由安全过滤器写入、请求结束时清理；异步任务或线程池场景必须显式传递，禁止依赖继承线程变量。</p>
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    /** 获取当前用户，未登录抛出 {@link IllegalStateException} */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new IllegalStateException("当前线程不存在登录用户上下文");
        }
        return user;
    }

    public static Long currentUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.userId();
    }

    public static String currentUsername() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.username();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
