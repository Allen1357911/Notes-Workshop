package com.notework.common.exception;

/**
 * 自定义业务异常。
 * <p>
 * 业务规则不满足时（例如"笔记不存在""无权操作他人的笔记"）抛这个异常，
 * 由 {@link GlobalExceptionHandler} 统一转成响应，Service 层不返回错误码、也不写响应。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    /** 使用错误码自带的提示信息 */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 使用自定义提示信息，错误码仍然保留 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
