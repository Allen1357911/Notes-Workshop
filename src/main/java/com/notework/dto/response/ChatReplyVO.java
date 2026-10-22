package com.notework.dto.response;

/**
 * 一轮对话的结果：用户消息 + AI 回复 + 会话标题 + 今日剩余次数。
 */
public class ChatReplyVO {

    private ChatMessageVO userMessage;

    private ChatMessageVO reply;

    /** 本轮的会话标题（首条成功消息会用它取名，之后保持不变） */
    private String sessionTitle;

    /** 今日还剩多少条提问配额 */
    private int remainingQuota;

    public ChatReplyVO(ChatMessageVO userMessage, ChatMessageVO reply, String sessionTitle, int remainingQuota) {
        this.userMessage = userMessage;
        this.reply = reply;
        this.sessionTitle = sessionTitle;
        this.remainingQuota = remainingQuota;
    }

    public ChatMessageVO getUserMessage() {
        return userMessage;
    }

    public ChatMessageVO getReply() {
        return reply;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public int getRemainingQuota() {
        return remainingQuota;
    }
}
