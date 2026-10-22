package com.notework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * AI 客服会话，对应表 t_ai_chat_session。
 * <p>
 * 一条会话归属唯一用户；"先新建空会话再聊天"，
 * 所以空会话也要能独立存在（message_count = 0，last_message_time = 创建时间）。
 */
@TableName("t_ai_chat_session")
public class ChatSession {

    /** 还没有成功消息时的默认标题 */
    public static final String DEFAULT_TITLE = "新会话";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户 id */
    private Long userId;

    /** 会话标题：首条成功消息前 30 字 */
    private String title;

    /** 消息条数（用户消息 + AI 回复） */
    private Integer messageCount;

    /** 最后一条消息时间；空会话等于创建时间，用于列表倒序 */
    private LocalDateTime lastMessageTime;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getMessageCount() {
        return messageCount;
    }

    public void setMessageCount(Integer messageCount) {
        this.messageCount = messageCount;
    }

    public LocalDateTime getLastMessageTime() {
        return lastMessageTime;
    }

    public void setLastMessageTime(LocalDateTime lastMessageTime) {
        this.lastMessageTime = lastMessageTime;
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
