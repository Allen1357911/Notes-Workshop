package com.notework.dto.response;

import com.notework.entity.Comment;
import com.notework.entity.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论（含多级回复）。
 * <p>
 * 顶层评论分页返回，它的所有后代回复一次性带在 replies 里（前端按层级缩进展示）。
 */
public class CommentVO {

    private Long id;
    private Long noteId;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private Long parentId;
    private String content;
    /** 当前登录用户是否可以删除这条评论（本人或管理员） */
    private boolean deletable;
    private LocalDateTime createTime;
    /** 该评论下的所有回复（按时间正序） */
    private List<CommentVO> replies = new ArrayList<>();

    public static CommentVO of(Comment comment, User user, boolean deletable) {
        CommentVO vo = new CommentVO();
        vo.id = comment.getId();
        vo.noteId = comment.getNoteId();
        vo.userId = comment.getUserId();
        if (user != null) {
            vo.nickname = user.getNickname();
            vo.avatarUrl = user.getAvatarUrl();
        }
        vo.parentId = comment.getParentId();
        vo.content = comment.getContent();
        vo.deletable = deletable;
        vo.createTime = comment.getCreateTime();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getContent() {
        return content;
    }

    public boolean isDeletable() {
        return deletable;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public List<CommentVO> getReplies() {
        return replies;
    }

    public void setReplies(List<CommentVO> replies) {
        this.replies = replies;
    }
}
