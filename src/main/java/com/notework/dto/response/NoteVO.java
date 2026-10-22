package com.notework.dto.response;

import com.notework.common.enums.AiCapability;
import com.notework.common.enums.AuditStatus;
import com.notework.entity.Note;
import com.notework.entity.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记详情（含正文、图书名、全部配图与可能存在的 AI 辅助结果）。
 */
public class NoteVO {

    private Long id;
    private String bookName;
    private String content;
    private String visibility;
    private String visibilityLabel;
    private Long authorId;
    private String authorNickname;
    private String authorAvatarUrl;
    /** 全部配图 URL，按排序号排列，可能为空 */
    private List<String> images;

    /**
     * AI 辅助结果：未保存、或当前查看者无权查看时为 null。
     * 只有用户手动确认保存过的结果才会出现在这里（一篇笔记最多一种）。
     */
    private String aiResult;

    /** 已保存 AI 结果的类型：SUMMARY / AUDIENCE / SCORE */
    private String aiType;

    private String aiTypeLabel;

    /** AI 结果是否对其他人公开（只有作者与管理员能看到这个开关状态） */
    private boolean aiPublic;

    /** 用户确认保存 AI 结果的时间 */
    private LocalDateTime aiConfirmedAt;

    /** 点赞数（实时从点赞表聚合） */
    private long likeCount;

    /** 当前用户是否已点赞 */
    private boolean liked;

    /** 评论数（实时从评论表聚合；扩展部分新增） */
    private long commentCount;

    /** 当前用户是否有权编辑/删除这篇笔记（作者本人或管理员） */
    private boolean editable;

    /** 审核状态：仅作者本人与管理员可见，其他人拿到 null（扩展部分新增） */
    private String auditStatus;

    /** 审核状态中文说明 */
    private String auditStatusLabel;

    /** 驳回或下架原因：仅作者本人与管理员可见 */
    private String auditRemark;

    /** 最近一次审核时间：仅作者本人与管理员可见 */
    private LocalDateTime auditTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static NoteVO of(Note note, User author, List<String> images, boolean editable,
                            long likeCount, boolean liked, long commentCount,
                            AuditStatus auditStatus, String auditRemark, LocalDateTime auditTime) {
        NoteVO vo = new NoteVO();
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
        vo.aiResult = null;
        vo.likeCount = likeCount;
        vo.liked = liked;
        vo.commentCount = commentCount;
        vo.editable = editable;
        if (auditStatus != null) {
            vo.auditStatus = auditStatus.name();
            vo.auditStatusLabel = auditStatus.getLabel();
        }
        vo.auditRemark = auditRemark;
        vo.auditTime = auditTime;
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

    public String getAiResult() {
        return aiResult;
    }

    /**
     * 填充 AI 结果字段。
     * <p>
     * 单独一个方法而不是塞进 {@code of(...)}：那个方法的参数已经很多了，
     * 而且 AI 字段要不要填取决于"谁在看"，由 Service 层判断后再调用。
     */
    public void applyAi(AiCapability type, String result, Integer aiPublicFlag, LocalDateTime confirmedAt) {
        this.aiType = type == null ? null : type.name();
        this.aiTypeLabel = type == null ? null : type.getLabel();
        this.aiResult = result;
        this.aiPublic = aiPublicFlag != null && aiPublicFlag == 1;
        this.aiConfirmedAt = confirmedAt;
    }

    public String getAiType() {
        return aiType;
    }

    public String getAiTypeLabel() {
        return aiTypeLabel;
    }

    public boolean isAiPublic() {
        return aiPublic;
    }

    public LocalDateTime getAiConfirmedAt() {
        return aiConfirmedAt;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public boolean isLiked() {
        return liked;
    }

    public boolean isEditable() {
        return editable;
    }

    public long getCommentCount() {
        return commentCount;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
