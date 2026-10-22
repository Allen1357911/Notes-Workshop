package com.notework.dto.response;

import com.notework.common.enums.Visibility;
import com.notework.entity.Note;
import com.notework.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记列表项（我的笔记列表、公开笔记列表共用）。
 */
public class NoteListVO {

    private Long id;
    private String bookName;
    private String content;
    private String visibility;
    private String visibilityLabel;
    private Long authorId;
    private String authorNickname;
    private String authorAvatarUrl;
    /** 配图 URL 列表，可能为空 */
    private List<String> images;

    /** 审核状态：作者在自己的列表里能看到"待审核/已驳回/已下架"（扩展部分新增） */
    private String auditStatus;

    private String auditStatusLabel;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * @param withAudit 是否带上审核状态：只有"这些笔记归当前用户管"（我的笔记 / 管理员）时才带，
     *                  广场、排行、作者主页、关注流里对其他人一律不下发审核状态
     */
    public static NoteListVO from(Note note, User author, List<String> images, boolean withAudit) {
        NoteListVO vo = new NoteListVO();
        vo.id = note.getId();
        vo.bookName = note.getBookName();
        vo.content = note.getContent();
        vo.visibility = note.getVisibility() == null ? null : note.getVisibility().name();
        vo.visibilityLabel = note.getVisibility() == null ? null : note.getVisibility().getLabel();
        if (author != null) {
            vo.authorId = author.getId();
            vo.authorNickname = author.getNickname();
            vo.authorAvatarUrl = author.getAvatarUrl();
        }
        vo.images = images == null ? List.of() : images;
        if (withAudit && note.getAuditStatus() != null) {
            vo.auditStatus = note.getAuditStatus().name();
            vo.auditStatusLabel = note.getAuditStatus().getLabel();
        }
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
