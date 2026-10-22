package com.notework.dto.response;

import com.notework.entity.Note;
import com.notework.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员看到的内容条目：比普通列表多出审核状态、审核意见与配图，便于审核与判定违规。
 */
public class AdminNoteVO {

    private Long id;
    private String bookName;
    private String content;
    private String visibility;
    private String visibilityLabel;
    private String auditStatus;
    private String auditStatusLabel;
    private String auditRemark;
    private LocalDateTime auditTime;
    private Long authorId;
    private String authorNickname;
    private String authorAvatarUrl;
    /** 配图 URL 列表，审核时可以看到书页截图或手写笔记照片 */
    private List<String> images;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static AdminNoteVO of(Note note, User author, List<String> images) {
        AdminNoteVO vo = new AdminNoteVO();
        vo.id = note.getId();
        vo.bookName = note.getBookName();
        vo.content = note.getContent();
        vo.visibility = note.getVisibility() == null ? null : note.getVisibility().name();
        vo.visibilityLabel = note.getVisibility() == null ? null : note.getVisibility().getLabel();
        if (note.getAuditStatus() != null) {
            vo.auditStatus = note.getAuditStatus().name();
            vo.auditStatusLabel = note.getAuditStatus().getLabel();
        }
        vo.auditRemark = note.getAuditRemark();
        vo.auditTime = note.getAuditTime();
        if (author != null) {
            vo.authorId = author.getId();
            vo.authorNickname = author.getNickname();
            vo.authorAvatarUrl = author.getAvatarUrl();
        }
        vo.images = images == null ? List.of() : images;
        vo.createTime = note.getCreateTime();
        vo.updateTime = note.getUpdateTime();
        return vo;
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

    public String getAuditStatus() {
        return auditStatus;
    }

    public String getAuditStatusLabel() {
        return auditStatusLabel;
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
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

    public List<String> getImages() {
        return images;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
