package com.notework.dto.response;

import com.notework.entity.User;

import java.time.LocalDateTime;

/**
 * 管理员看到的用户条目。
 * <p>
 * 同样不包含 password 字段，任何接口都不会把密码（即使是哈希值）返回出去。
 */
public class AdminUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatarUrl;
    private String role;
    private String roleLabel;
    /** 1 正常 / 0 禁用 */
    private Integer status;
    private String statusLabel;
    private LocalDateTime createTime;

    public static AdminUserVO from(User user) {
        AdminUserVO vo = new AdminUserVO();
        vo.id = user.getId();
        vo.username = user.getUsername();
        vo.nickname = user.getNickname();
        vo.avatarUrl = user.getAvatarUrl();
        if (user.getRole() != null) {
            vo.role = user.getRole().name();
            vo.roleLabel = user.getRole().getLabel();
        }
        vo.status = user.getStatus();
        vo.statusLabel = User.STATUS_NORMAL.equals(user.getStatus()) ? "正常" : "已禁用";
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

    public Integer getStatus() {
        return status;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
