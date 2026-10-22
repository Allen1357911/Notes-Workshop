package com.notework.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 管理员修改用户账号状态请求。
 */
public class UpdateUserStatusRequest {

    /** 1 正常 / 0 禁用 */
    @NotNull(message = "状态不能为空：1 正常 / 0 禁用")
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
