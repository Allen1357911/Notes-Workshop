package com.notework.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送一条客服消息的请求。
 * <p>
 * 长度上限不写死在这里：它是配置项（默认 1000 字），
 * 由 Service 校验并给出"当前多少字、上限多少字"的明确提示，而不是笼统的 400。
 */
public class SendChatMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    private String content;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
