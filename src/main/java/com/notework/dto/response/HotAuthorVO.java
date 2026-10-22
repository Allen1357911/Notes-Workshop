package com.notework.dto.response;

import com.notework.entity.User;
import com.notework.mapper.row.HotAuthorRow;

/**
 * 热门作者条目（按粉丝数排行）。
 */
public class HotAuthorVO {

    private Long id;
    private String nickname;
    private String avatarUrl;
    private long followerCount;
    /** 公开且已通过的笔记数 */
    private long noteCount;
    /** 当前登录用户是否已关注 */
    private boolean followed;

    public static HotAuthorVO of(HotAuthorRow row, User user, boolean followed) {
        HotAuthorVO vo = new HotAuthorVO();
        vo.id = row.getUserId();
        vo.followerCount = row.getFollowerCount() == null ? 0L : row.getFollowerCount();
        vo.noteCount = row.getNoteCount() == null ? 0L : row.getNoteCount();
        vo.followed = followed;
        if (user != null) {
            vo.nickname = user.getNickname();
            vo.avatarUrl = user.getAvatarUrl();
        }
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

    public long getNoteCount() {
        return noteCount;
    }

    public boolean isFollowed() {
        return followed;
    }
}
