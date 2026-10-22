package com.notework.dto.request;

import jakarta.validation.constraints.Size;

/**
 * 管理员下架违规笔记请求（说明可选）。
 */
public class OfflineNoteRequest {

    @Size(max = 255, message = "下架说明最长 255 个字符")
    private String remark;

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
