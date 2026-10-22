package com.notework.dto.response;

import com.notework.entity.User;

import java.time.LocalDateTime;

/**
 * 用户信息返回对象。
 * <p>
 * 刻意不含 password 字段：任何接口都不会把密码（即使是哈希值）返回给前端。
 */
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    /** 角色名：USER / ADMIN */
    private String role;
    /** 角色的中文说明，方便前端直接展示 */
    private String roleLabel;
    private LocalDateTime createTime;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.id = user.getId();
        vo.username = user.getUsername();
        vo.nickname = user.getNickname();
        vo.avatarUrl = user.getAvatarUrl();
        if (user.getRole() != null) {
            vo.role = user.getRole().name();
            vo.roleLabel = user.getRole().getLabel();
        }
        vo.createTime = user.getCreateTime();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getRole() {
        return role;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
