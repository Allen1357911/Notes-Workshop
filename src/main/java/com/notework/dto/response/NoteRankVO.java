package com.notework.dto.response;

import com.notework.common.enums.Visibility;
import com.notework.entity.User;
import com.notework.mapper.row.NoteWithLikeRow;

import java.time.LocalDateTime;

/**
 * 带点赞数的笔记条目：按点赞数排行、我点赞过的笔记这两个列表共用。
 */
public class NoteRankVO {

    private Long id;
    private String bookName;
    private String content;
    private String visibility;
    private String visibilityLabel;
    private Long authorId;
    private String authorNickname;
    private String authorAvatarUrl;
    /** 点赞数（实时从点赞表聚合） */
    private long likeCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static NoteRankVO of(NoteWithLikeRow row, User author) {
        NoteRankVO vo = new NoteRankVO();
        vo.id = row.getId();
        vo.bookName = row.getBookName();
        vo.content = row.getContent();
        vo.visibility = row.getVisibility();
        vo.visibilityLabel = labelOf(row.getVisibility());
        vo.likeCount = row.getLikeCount() == null ? 0L : row.getLikeCount();
        if (author != null) {
            vo.authorId = author.getId();
            vo.authorNickname = author.getNickname();
            vo.authorAvatarUrl = author.getAvatarUrl();
        }
        vo.createTime = row.getCreateTime();
        vo.updateTime = row.getUpdateTime();
        return vo;
    }

    private static String labelOf(String visibility) {
        try {
            return Visibility.valueOf(visibility).getLabel();
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    public Long getId() {
        return id;
    }

    public String getBookName() {
        return bookName;
    }

    public String getContent() {
        return content;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getVisibilityLabel() {
        return visibilityLabel;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getAuthorNickname() {
        return authorNickname;
    }

    public String getAuthorAvatarUrl() {
        return authorAvatarUrl;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
