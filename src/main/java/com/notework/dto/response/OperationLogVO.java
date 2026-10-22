package com.notework.dto.response;

import com.notework.entity.OperationLogRecord;

import java.time.LocalDateTime;

/**
 * 管理员看到的操作日志条目。
 */
public class OperationLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String description;
    private Long targetId;
    private String method;
    private String uri;
    private String ip;
    private String params;
    /** true 成功 / false 失败 */
    private boolean success;
    private String errorMsg;
    private Long costMs;
    private LocalDateTime createTime;

    public static OperationLogVO from(OperationLogRecord record) {
        OperationLogVO vo = new OperationLogVO();
        vo.id = record.getId();
        vo.userId = record.getUserId();
        vo.username = record.getUsername();
        vo.module = record.getModule();
        vo.action = record.getAction();
        vo.description = record.getDescription();
        vo.targetId = record.getTargetId();
        vo.method = record.getMethod();
        vo.uri = record.getUri();
        vo.ip = record.getIp();
        vo.params = record.getParams();
        vo.success = record.getSuccess() != null && record.getSuccess() == 1;
        vo.errorMsg = record.getErrorMsg();
        vo.costMs = record.getCostMs();
        vo.createTime = record.getCreateTime();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getModule() {
        return module;
    }

    public String getAction() {
        return action;
    }

    public String getDescription() {
        return description;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getMethod() {
        return method;
    }

    public String getUri() {
        return uri;
    }

    public String getIp() {
        return ip;
    }

    public String getParams() {
        return params;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public Long getCostMs() {
        return costMs;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
