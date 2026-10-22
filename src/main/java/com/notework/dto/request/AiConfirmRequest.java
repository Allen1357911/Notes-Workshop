package com.notework.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 确认保存 AI 结果请求。
 * <p>
 * 注意：这里**只传生成记录的 id**，不传结果内容 —— 结果由服务端按 id 取，
 * 保证"用户不能篡改 AI 的结论"（想改就自己编辑笔记正文）。
 */
public class AiConfirmRequest {

    /** t_ai_log 的主键：即用户刚刚看到的那次生成 */
    @NotNull(message = "生成记录 id 不能为空")
    private Long aiLogId;

    /** 是否对其他人公开：不传默认 false（仅作者与管理员可见） */
    private Boolean aiPublic;

    public Long getAiLogId() {
        return aiLogId;
    }

    public void setAiLogId(Long aiLogId) {
        this.aiLogId = aiLogId;
    }

    public Boolean getAiPublic() {
        return aiPublic;
    }

    public void setAiPublic(Boolean aiPublic) {
        this.aiPublic = aiPublic;
    }
}
