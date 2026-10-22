package com.notework.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 修改 AI 结果公开状态请求（作者决定是否给别人看）。
 */
public class AiVisibilityRequest {

    @NotNull(message = "是否公开不能为空")
    private Boolean aiPublic;

    public Boolean getAiPublic() {
        return aiPublic;
    }

    public void setAiPublic(Boolean aiPublic) {
        this.aiPublic = aiPublic;
    }
}
