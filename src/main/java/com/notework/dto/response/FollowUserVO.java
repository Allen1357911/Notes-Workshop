package com.notework.dto.response;

import com.notework.entity.User;

import java.time.LocalDateTime;

/**
 * 粉丝 / 关注列表里的用户条目。
 */
public class FollowUserVO {

    private Long id;
    private String nickname;
    private String avatarUrl;
    /** 当前登录用户是否已关注这个人（便于在列表里直接显示"关注/已关注"） */
    private boolean followed;
    /** 成为粉丝（或开始关注）的时间 */
    private LocalDateTime createTime;

    public static FollowUserVO of(User user, boolean followed, LocalDateTime createTime) {
        FollowUserVO vo = new FollowUserVO();
        vo.id = user.getId();
        vo.nickname = user.getNickname();
        vo.avatarUrl = user.getAvatarUrl();
        vo.followed = followed;
        vo.createTime = createTime;
        return vo;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isFollowed() {
        return followed;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
