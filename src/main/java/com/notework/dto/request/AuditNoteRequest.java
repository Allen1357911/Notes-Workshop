package com.notework.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 管理员审核公开笔记请求。
 */
public class AuditNoteRequest {

    /** true 通过 / false 驳回 */
    @NotNull(message = "审核结论不能为空：true 通过 / false 驳回")
    private Boolean approved;

    /** 驳回原因，驳回时建议填写；不填则使用默认说明 */
    @Size(max = 255, message = "审核意见最长 255 个字符")
    private String remark;

    public Boolean getApproved() {
        return approved;
    }

    public void setApproved(Boolean approved) {
        this.approved = approved;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
