package com.notework.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.api.Result;
import com.notework.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * 拦截器里直接写响应用的公共方法。
 * <p>
 * 拦截器绕过了 Controller，所以必须自己写 JSON，保证前端拿到的结构与全局异常处理器一致。
 * （原有的 JwtAuthInterceptor 有自己的私有实现，这里抽出来供扩展部分新增的拦截器复用。）
 */
public final class InterceptorResponseWriter {

    private InterceptorResponseWriter() {
    }

    public static void write(HttpServletResponse response, ObjectMapper objectMapper,
                             ErrorCode errorCode, String message) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(errorCode, message)));
    }

    public static void write(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
            throws IOException {
        write(response, objectMapper, errorCode, errorCode.getMessage());
    }
}
