package com.notework.interceptor;

import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;

/**
 * 当前登录用户上下文。
 * <p>
 * 由 {@link JwtAuthInterceptor} 在请求进入时写入、请求结束时清理。
 * ThreadLocal 用完必须清理，否则线程池复用线程会把上一个请求的用户带给下一个请求。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static boolean isLogin() {
        return HOLDER.get() != null;
    }

    /** 当前登录用户 id，未登录返回 null */
    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static boolean isAdmin() {
        LoginUser user = HOLDER.get();
        return user != null && user.isAdmin();
    }

    /** 当前登录用户 id，未登录直接抛 401，供 Service 层使用 */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
