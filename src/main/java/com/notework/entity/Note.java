package com.notework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.notework.common.enums.AiCapability;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Visibility;

import java.time.LocalDateTime;

/**
 * 读书笔记实体，对应表 t_note。
 * <p>
 * 基础阶段只有图书名（字符串）与正文，不建图书表、不设标题。
 */
@TableName("t_note")
public class Note {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者用户 id */
    private Long userId;

    /** 图书名 */
    private String bookName;

    /** 笔记正文 */
    private String content;

    /** 可见性：PRIVATE 私有 / PUBLIC 公开 */
    private Visibility visibility;

    /** 审核状态：公开笔记新建或编辑后进入 PENDING，管理员审核后变为 APPROVED / REJECTED / TAKEN_DOWN */
    private AuditStatus auditStatus;

    /** 驳回或下架的原因，仅作者与管理员可见 */
    private String auditRemark;

    /** 最近一次审核时间 */
    private LocalDateTime auditTime;

    /** 已保存的 AI 结果类型（一篇笔记最多一种），未保存时为 null */
    private AiCapability aiType;

    /** 已确认保存的 AI 结果（只作参考），未保存时为 null */
    private String aiResult;

    /** 用户确认保存 AI 结果的时间 */
    private LocalDateTime aiConfirmedAt;

    /** AI 结果是否对其他人公开：0 仅作者与管理员可见，1 随笔记公开 */
    private Integer aiPublic;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public AuditStatus getAuditStatus() {
        return auditStatus;
    }

    public void setAuditStatus(AuditStatus auditStatus) {
        this.auditStatus = auditStatus;
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public AiCapability getAiType() {
        return aiType;
    }

    public void setAiType(AiCapability aiType) {
        this.aiType = aiType;
    }

    public String getAiResult() {
        return aiResult;
    }

    public void setAiResult(String aiResult) {
        this.aiResult = aiResult;
    }

    public LocalDateTime getAiConfirmedAt() {
        return aiConfirmedAt;
    }

    public void setAiConfirmedAt(LocalDateTime aiConfirmedAt) {
        this.aiConfirmedAt = aiConfirmedAt;
    }

    public Integer getAiPublic() {
        return aiPublic;
    }

    public void setAiPublic(Integer aiPublic) {
        this.aiPublic = aiPublic;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
