package com.notework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.notework.common.enums.AiCapability;

import java.time.LocalDateTime;

/**
 * AI 调用日志，对应表 t_ai_log。
 * <p>
 * 每调用一次模型就写一条：既是管理员"调用统计"的数据源，
 * 也让"用户确认保存的到底是哪一次生成"可追溯（confirmed 字段）。
 */
@TableName("t_ai_log")
public class AiLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long noteId;

    /** 客服会话 id：只有客服对话有值（笔记 AI 为空） */
    private Long sessionId;

    /** 调用者 id */
    private Long userId;

    private AiCapability capability;

    private String model;

    /** 送入模型的正文长度（截断后） */
    private Integer inputChars;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    /** 调用耗时（毫秒） */
    private Long costMs;

    /** 1 成功 / 0 失败 */
    private Integer success;

    private String errorMsg;

    /** AI 结果（已通过结构校验） */
    private String result;

    /** 是否已被用户确认保存到笔记：1 是 / 0 否 */
    private Integer confirmed;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public void setNoteId(Long noteId) {
        this.noteId = noteId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public AiCapability getCapability() {
        return capability;
    }

    public void setCapability(AiCapability capability) {
        this.capability = capability;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getInputChars() {
        return inputChars;
    }

    public void setInputChars(Integer inputChars) {
        this.inputChars = inputChars;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Long getCostMs() {
        return costMs;
    }

    public void setCostMs(Long costMs) {
        this.costMs = costMs;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Integer getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(Integer confirmed) {
        this.confirmed = confirmed;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
