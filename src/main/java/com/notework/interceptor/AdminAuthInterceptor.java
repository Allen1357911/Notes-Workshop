package com.notework.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 管理员权限拦截器（扩展部分新增）。
 * <p>
 * 挂在 /api/admin/** 上，在 JwtAuthInterceptor（登录态）与 AccountStatusInterceptor（账号状态）之后执行：
 * 未登录返回 401（正常由登录态拦截器先挡掉），已登录但不是管理员返回 403「需要管理员权限」。
 * <p>
 * 职责边界：只判断"是不是管理员"，不管具体业务规则（例如"不能禁用自己"在 Service 层判断）。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!UserContext.isLogin()) {
            InterceptorResponseWriter.write(response, objectMapper, ErrorCode.UNAUTHORIZED);
            return false;
        }
        if (!UserContext.isAdmin()) {
            InterceptorResponseWriter.write(response, objectMapper, ErrorCode.FORBIDDEN, "需要管理员权限");
            return false;
        }
        return true;
    }
}
