package com.notework.dto.response;

import com.notework.entity.Comment;
import com.notework.entity.Note;
import com.notework.entity.User;

import java.time.LocalDateTime;

/**
 * 管理员看到的评论条目：带上所属笔记的书名与评论人信息，便于判定违规。
 */
public class AdminCommentVO {

    private Long id;
    private Long noteId;
    private String noteBookName;
    private Long userId;
    private String userNickname;
    private String userAvatarUrl;
    private Long parentId;
    private Long rootId;
    private String content;
    private LocalDateTime createTime;

    public static AdminCommentVO of(Comment comment, Note note, User user) {
        AdminCommentVO vo = new AdminCommentVO();
        vo.id = comment.getId();
        vo.noteId = comment.getNoteId();
        vo.noteBookName = note == null ? null : note.getBookName();
        vo.userId = comment.getUserId();
        if (user != null) {
            vo.userNickname = user.getNickname();
            vo.userAvatarUrl = user.getAvatarUrl();
        }
        vo.parentId = comment.getParentId();
        vo.rootId = comment.getRootId();
        vo.content = comment.getContent();
        vo.createTime = comment.getCreateTime();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public String getNoteBookName() {
        return noteBookName;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserNickname() {
        return userNickname;
    }

    public String getUserAvatarUrl() {
        return userAvatarUrl;
    }

    public Long getParentId() {
        return parentId;
    }

    public Long getRootId() {
        return rootId;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
