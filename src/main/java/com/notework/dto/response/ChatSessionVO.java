package com.notework.dto.response;

import com.notework.entity.ChatSession;

import java.time.LocalDateTime;

/**
 * 会话列表/会话头信息。
 */
public class ChatSessionVO {

    private Long id;

    private String title;

    /** 消息条数（用户消息 + AI 回复） */
    private int messageCount;

    private LocalDateTime lastMessageTime;

    private LocalDateTime createTime;

    public static ChatSessionVO from(ChatSession session) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.id = session.getId();
        vo.title = session.getTitle();
        vo.messageCount = session.getMessageCount() == null ? 0 : session.getMessageCount();
        vo.lastMessageTime = session.getLastMessageTime();
        vo.createTime = session.getCreateTime();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getMessageCount() {
        return messageCount;
    }

    public LocalDateTime getLastMessageTime() {
        return lastMessageTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
