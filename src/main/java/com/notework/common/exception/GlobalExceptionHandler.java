package com.notework.common.exception;

import com.notework.common.api.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * 全局统一异常处理器。
 * <p>
 * 职责边界：只有它负责把异常转成 HTTP 响应，Controller 与 Service 里不写 try-catch 返回错误。
 * 响应同时带业务 code 和对应的 HTTP 状态码。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：可预期，按错误码返回，记 warn 级别 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("业务异常 -> code={}, message={}", errorCode.getCode(), e.getMessage());
        return build(errorCode, e.getMessage());
    }

    /** @RequestBody 上的 @Valid 校验不通过 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return build(ErrorCode.PARAM_ERROR, firstFieldErrorMessage(e.getBindingResult().getFieldErrors()));
    }

    /** 表单 / 查询参数绑定校验不通过 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        return build(ErrorCode.PARAM_ERROR, firstFieldErrorMessage(e.getBindingResult().getFieldErrors()));
    }

    /** 方法参数上的约束校验不通过（@Validated + @NotNull 之类） */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return build(ErrorCode.PARAM_ERROR, message);
    }

    /** 请求本身有问题：JSON 格式错误、参数类型不匹配、缺少必填参数 */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<Result<Void>> handleBadRequestException(Exception e) {
        log.warn("请求参数异常 -> {}", e.getMessage());
        return build(ErrorCode.PARAM_ERROR, ErrorCode.PARAM_ERROR.getMessage());
    }

    /** 请求方法不支持（例如对只支持 POST 的接口发了 GET） */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持 -> method={}, supported={}", e.getMethod(), e.getSupportedHttpMethods());
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    /** 请求内容类型不支持（例如用 text/plain 提交 JSON） */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException e) {
        log.warn("请求内容类型不支持 -> contentType={}", e.getContentType());
        return build(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage());
    }

    /** 上传文件超过大小限制：文件模块的上传接口依赖它返回 30003 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        log.warn("上传文件超出限制 -> {}", e.getMessage());
        return build(ErrorCode.FILE_TOO_LARGE, ErrorCode.FILE_TOO_LARGE.getMessage());
    }

    /** 访问了不存在的接口或静态资源 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("资源不存在 -> {}", e.getResourcePath());
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    /** 兜底：未预期的异常，记 error 级别并打印堆栈，对外不暴露内部细节 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        log.error("系统异常", e);
        return build(ErrorCode.SYSTEM_ERROR, ErrorCode.SYSTEM_ERROR.getMessage());
    }

    private ResponseEntity<Result<Void>> build(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(Result.fail(errorCode, message));
    }

    private String firstFieldErrorMessage(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
    }
}
