package com.notework.dto.response;

import com.notework.entity.ChatMessage;

import java.time.LocalDateTime;

/**
 * 一条客服消息（用户消息或 AI 回复）。
 */
public class ChatMessageVO {

    private Long id;

    /** USER / ASSISTANT */
    private String role;

    /** 我 / AI 客服 */
    private String roleLabel;

    private String content;

    /** 本轮 token 与耗时：只有 AI 回复有值，前端用来显示"本轮成本" */
    private int totalTokens;

    private long costMs;

    private LocalDateTime createTime;

    public static ChatMessageVO from(ChatMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.id = message.getId();
        vo.role = message.getRole() == null ? null : message.getRole().name();
        vo.roleLabel = message.getRole() == null ? null : message.getRole().getLabel();
        vo.content = message.getContent();
        vo.totalTokens = message.getTotalTokens() == null ? 0 : message.getTotalTokens();
        vo.costMs = message.getCostMs() == null ? 0 : message.getCostMs();
        vo.createTime = message.getCreateTime();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public String getRoleLabel() {
        return roleLabel;
    }

    public String getContent() {
        return content;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public long getCostMs() {
        return costMs;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
