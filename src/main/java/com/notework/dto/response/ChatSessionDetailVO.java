package com.notework.dto.response;

import java.util.List;

/**
 * 会话详情：会话头信息 + 最近若干条消息（按时间正序）。
 */
public class ChatSessionDetailVO {

    private ChatSessionVO session;

    /** 最近 N 条消息（N = app.ai.chat-history-limit，按时间正序） */
    private List<ChatMessageVO> messages;

    public ChatSessionDetailVO(ChatSessionVO session, List<ChatMessageVO> messages) {
        this.session = session;
        this.messages = messages;
    }

    public ChatSessionVO getSession() {
        return session;
    }

    public List<ChatMessageVO> getMessages() {
        return messages;
    }
}
