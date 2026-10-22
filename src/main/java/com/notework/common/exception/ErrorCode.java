package com.notework.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 全局错误码。
 * <p>
 * code 规则：
 * <ul>
 *     <li>200 成功</li>
 *     <li>400 / 401 / 403 / 404 / 405 / 415 / 500 通用错误，与 HTTP 状态码对齐</li>
 *     <li>1xxxx 按模块划分的业务错误码：1xxxx 用户、2xxxx 笔记、3xxxx 文件</li>
 * </ul>
 * httpStatus 用于让响应同时带上正确的 HTTP 状态码，方便接口测试时一眼看出成功失败。
 */
public enum ErrorCode {

    SUCCESS(200, "成功", HttpStatus.OK),

    PARAM_ERROR(400, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(401, "未登录或登录状态已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(403, "没有操作权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(404, "请求的资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(405, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA_TYPE(415, "请求内容类型不支持", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    SYSTEM_ERROR(500, "系统繁忙，请稍后再试", HttpStatus.INTERNAL_SERVER_ERROR),

    /* ---------- 用户模块 ---------- */
    USER_NOT_FOUND(10001, "用户不存在", HttpStatus.NOT_FOUND),
    USERNAME_ALREADY_EXISTS(10002, "用户名已被注册", HttpStatus.BAD_REQUEST),
    LOGIN_FAILED(10003, "用户名或密码错误", HttpStatus.BAD_REQUEST),
    OLD_PASSWORD_INCORRECT(10004, "原密码不正确", HttpStatus.BAD_REQUEST),
    ACCOUNT_DISABLED(10005, "账号已被禁用，请联系管理员", HttpStatus.FORBIDDEN),
    CANNOT_DISABLE_SELF(10006, "不能禁用自己的账号", HttpStatus.BAD_REQUEST),

    /* ---------- 笔记模块 ---------- */
    NOTE_NOT_FOUND(20001, "笔记不存在", HttpStatus.NOT_FOUND),
    NOTE_PERMISSION_DENIED(20002, "无权操作他人的笔记", HttpStatus.FORBIDDEN),
    NOTE_NOT_PUBLIC(20003, "该笔记未公开，无法点赞", HttpStatus.BAD_REQUEST),
    NOTE_VISIBILITY_DENIED(20004, "无权查看他人的私有笔记", HttpStatus.FORBIDDEN),
    NOTE_NOT_PENDING(20005, "该笔记不在待审核状态", HttpStatus.BAD_REQUEST),
    NOTE_STATE_INVALID(20006, "笔记当前状态不允许该操作", HttpStatus.BAD_REQUEST),

    /* ---------- 文件模块 ---------- */
    FILE_EMPTY(30001, "上传文件为空", HttpStatus.BAD_REQUEST),
    FILE_TYPE_NOT_ALLOWED(30002, "图片格式不支持", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(30003, "图片大小超出限制", HttpStatus.BAD_REQUEST),
    FILE_UPLOAD_FAILED(30004, "图片上传失败", HttpStatus.INTERNAL_SERVER_ERROR),

    /* ---------- 评论模块 ---------- */
    COMMENT_NOT_FOUND(40001, "评论不存在", HttpStatus.NOT_FOUND),
    COMMENT_PERMISSION_DENIED(40002, "只能删除自己的评论", HttpStatus.FORBIDDEN),
    COMMENT_PARENT_INVALID(40003, "回复的评论不存在或不属于这篇笔记", HttpStatus.BAD_REQUEST),

    /* ---------- 关注模块 ---------- */
    CANNOT_FOLLOW_SELF(50001, "不能关注自己", HttpStatus.BAD_REQUEST),

    /* ---------- AI 模块 ---------- */
    AI_NOT_CONFIGURED(60001, "AI 功能未配置：请设置环境变量 DEEPSEEK_API_KEY", HttpStatus.SERVICE_UNAVAILABLE),
    AI_QUOTA_EXCEEDED(60002, "今日 AI 调用次数已用完，明天再来吧", HttpStatus.TOO_MANY_REQUESTS),
    AI_RESULT_NOT_FOUND(60003, "AI 生成记录不存在或不属于这篇笔记", HttpStatus.NOT_FOUND),
    AI_CALL_FAILED(60004, "AI 调用失败，请稍后重试", HttpStatus.BAD_GATEWAY),
    AI_RESPONSE_INVALID(60005, "AI 返回内容无法解析，请重试", HttpStatus.BAD_GATEWAY),
    AI_RESULT_ABSENT(60006, "这篇笔记还没有保存 AI 结果", HttpStatus.BAD_REQUEST),

    /* ---------- AI 智能客服 ---------- */
    CHAT_SESSION_NOT_FOUND(60007, "会话不存在或无权访问", HttpStatus.NOT_FOUND),
    CHAT_MESSAGE_TOO_LONG(60008, "消息太长，请精简后再发", HttpStatus.BAD_REQUEST),
    CHAT_QUOTA_EXCEEDED(60009, "今日 AI 客服提问次数已用完，明天再来吧", HttpStatus.TOO_MANY_REQUESTS),
    CHAT_CALL_FAILED(60010, "AI 客服暂时不可用，请稍后重试", HttpStatus.BAD_GATEWAY),
    CHAT_SESSION_LIMIT_EXCEEDED(60011, "今日新建会话数量已达上限，明天再来吧", HttpStatus.TOO_MANY_REQUESTS);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
