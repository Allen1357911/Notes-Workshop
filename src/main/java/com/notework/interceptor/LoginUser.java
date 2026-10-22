package com.notework.interceptor;

import com.notework.common.enums.Role;

/**
 * 当前登录用户的轻量信息。
 * <p>
 * 由 JWT 解析得到，放在 {@link UserContext} 里供 Service 层取用；
 * 不是数据库实体，不含密码等敏感字段。
 */
public class LoginUser {

    private final Long userId;
    private final String username;
    private final Role role;

    public LoginUser(Long userId, String username, Role role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Role getRole() {
        return role;
    }

    public boolean isAdmin() {
        return role != null && role.isAdmin();
    }
}
