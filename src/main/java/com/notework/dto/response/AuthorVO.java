package com.notework.dto.response;

import com.notework.entity.User;

/**
 * 作者主页信息：昵称、头像、粉丝数、关注数、笔记数与"我是否已关注"。
 */
public class AuthorVO {

    private Long id;
    private String nickname;
    private String avatarUrl;
    /** 粉丝数（多少人关注了他） */
    private long followerCount;
    /** 关注数（他关注了多少人） */
    private long followingCount;
    /** 公开且已通过的笔记数 */
    private long noteCount;
    /** 当前登录用户是否已关注他；未登录恒为 false */
    private boolean followed;
    /** 是否是当前登录用户自己 */
    private boolean self;

    public static AuthorVO of(User user, long followerCount, long followingCount, long noteCount,
                              boolean followed, boolean self) {
        AuthorVO vo = new AuthorVO();
        vo.id = user.getId();
        vo.nickname = user.getNickname();
        vo.avatarUrl = user.getAvatarUrl();
        vo.followerCount = followerCount;
        vo.followingCount = followingCount;
        vo.noteCount = noteCount;
        vo.followed = followed;
        vo.self = self;
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

    public long getFollowerCount() {
        return followerCount;
    }

    public long getFollowingCount() {
        return followingCount;
    }

    public long getNoteCount() {
        return noteCount;
    }

    public boolean isFollowed() {
        return followed;
    }

    public boolean isSelf() {
        return self;
    }
}
