package com.notework.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发表评论 / 回复请求。
 * <p>
 * parentId 为空表示对笔记发表顶层评论；不为空表示回复某条评论（支持多级回复）。
 */
public class CreateCommentRequest {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 1000, message = "评论最长 1000 个字符")
    private String content;

    /** 回复的父评论 id，顶层评论不传 */
    private Long parentId;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }
}
