package com.notework.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.exception.ErrorCode;
import com.notework.entity.User;
import com.notework.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 账号状态校验拦截器（扩展部分新增）。
 * <p>
 * 为什么需要它：JWT 是无状态的，令牌签发后里面的信息就固定了，光靠令牌无法做到
 * "管理员一禁用账号就立刻生效"、也做不到"刚被撤销管理员权限的人立刻失去后台权限"。
 * 所以这里在登录态校验通过后，再按主键读一次用户：
 * <ul>
 *     <li>用户不存在 → 401</li>
 *     <li>status = 0（已禁用）→ 403「账号已被禁用」</li>
 *     <li>否则用数据库里的最新角色覆盖令牌里的角色（授权/撤销立即生效）</li>
 * </ul>
 * 注册顺序在 JwtAuthInterceptor 之后，所以只在"已经登录"的请求上做这次查询；
 * 匿名请求（公开笔记列表、详情、排行）不查库。
 */
@Component
public class AccountStatusInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AccountStatusInterceptor(UserMapper userMapper, ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!UserContext.isLogin()) {
            return true;
        }

        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            UserContext.clear();
            InterceptorResponseWriter.write(response, objectMapper, ErrorCode.UNAUTHORIZED);
            return false;
        }
        if (!User.STATUS_NORMAL.equals(user.getStatus())) {
            UserContext.clear();
            InterceptorResponseWriter.write(response, objectMapper, ErrorCode.ACCOUNT_DISABLED);
            return false;
        }

        // 用数据库里的最新资料覆盖令牌里的信息，保证禁用/授权撤销立即生效
        UserContext.set(new LoginUser(user.getId(), user.getUsername(), user.getRole()));
        return true;
    }
}
