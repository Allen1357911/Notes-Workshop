package com.notework.mapper.row;

/**
 * 数据访问层的行对象：热门作者（按粉丝数排行）。
 */
public class HotAuthorRow {

    private Long userId;
    /** 粉丝数 */
    private Long followerCount;
    /** 公开且已通过的笔记数 */
    private Long noteCount;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(Long followerCount) {
        this.followerCount = followerCount;
    }

    public Long getNoteCount() {
        return noteCount;
    }

    public void setNoteCount(Long noteCount) {
        this.noteCount = noteCount;
    }
}
