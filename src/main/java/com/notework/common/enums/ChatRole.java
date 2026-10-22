package com.notework.common.enums;

/**
 * 客服消息的角色。
 * <p>
 * 与 DeepSeek（OpenAI 兼容）接口里的 role 一一对应：USER → user，ASSISTANT → assistant。
 */
public enum ChatRole {

    /** 用户发的消息 */
    USER("我"),
    /** AI 客服的回复 */
    ASSISTANT("AI 客服");

    private final String label;

    ChatRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 转成模型接口要求的 role 字符串 */
    public String toApiRole() {
        return this == USER ? "user" : "assistant";
    }
}
