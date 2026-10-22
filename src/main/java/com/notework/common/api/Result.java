package com.notework.common.api;

import com.notework.common.exception.ErrorCode;

/**
 * 统一响应体。
 * <p>
 * 所有接口的返回值统一包成 {code, message, data}，前端只需要判断 code 是否为 200。
 * 该类不可变，只能通过静态工厂方法创建。
 *
 * @param <T> 业务数据类型
 */
public class Result<T> {

    /** 业务状态码，200 表示成功，其余见 {@link ErrorCode} */
    private final Integer code;
    /** 提示信息，可直接展示给用户 */
    private final String message;
    /** 业务数据，失败时为 null */
    private final T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功，无数据 */
    public static <T> Result<T> success() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /** 成功，带数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /** 失败，使用错误码自带的提示信息 */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 失败，用自定义提示信息覆盖错误码自带的提示 */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    /** 失败，完全自定义（仅在没有对应错误码时使用） */
    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
