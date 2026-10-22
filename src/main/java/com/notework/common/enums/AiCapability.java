package com.notework.common.enums;

/**
 * AI 能力枚举。
 * <p>
 * 前三种是"笔记级"能力，都是"辅助"性质，不生成新的笔记正文：
 * <ul>
 *     <li>{@link #SUMMARY} 总结：把正文压缩成一段摘要 + 关键词，方便复习</li>
 *     <li>{@link #AUDIENCE} 适宜人群：这篇笔记适合谁读</li>
 *     <li>{@link #SCORE} 评分：给笔记本身打个分并给理由与改进建议</li>
 * </ul>
 * {@link #CHAT} 是"会话级"能力（平台答疑客服），与笔记无关：
 * 走 {@code /api/ai/chat/**} 接口，审计日志里 note_id 为空、session_id 有值。
 */
public enum AiCapability {

    SUMMARY("总结"),
    AUDIENCE("适宜人群"),
    SCORE("评分"),
    CHAT("智能客服");

    private final String label;

    AiCapability(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 是否属于"笔记级"能力（可用于 /api/notes/{id}/ai/{capability}） */
    public boolean isNoteCapability() {
        return this != CHAT;
    }

    /** 按枚举名解析；不认识时返回 null，由调用方翻译成 400 参数错误 */
    public static AiCapability of(String name) {
        if (name == null) {
            return null;
        }
        for (AiCapability capability : values()) {
            if (capability.name().equalsIgnoreCase(name.trim())) {
                return capability;
            }
        }
        return null;
    }
}
