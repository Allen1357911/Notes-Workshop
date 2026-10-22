package com.notework.common.api;

import com.notework.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResultTest {

    @Test
    @DisplayName("success() 返回 200 且不带数据")
    void successWithoutData() {
        Result<Void> result = Result.success();
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode().intValue());
        assertEquals("成功", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("success(data) 原样返回业务数据")
    void successWithData() {
        Result<String> result = Result.success("《活着》读书笔记");
        assertEquals(200, result.getCode().intValue());
        assertEquals("《活着》读书笔记", result.getData());
    }

    @Test
    @DisplayName("fail(ErrorCode) 使用错误码自带的 code 与提示")
    void failWithErrorCode() {
        Result<Void> result = Result.fail(ErrorCode.NOTE_NOT_FOUND);
        assertEquals(20001, result.getCode().intValue());
        assertEquals("笔记不存在", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    @DisplayName("fail(ErrorCode, message) 保留错误码但替换提示信息")
    void failWithCustomMessage() {
        Result<Void> result = Result.fail(ErrorCode.PARAM_ERROR, "图书名不能为空");
        assertEquals(400, result.getCode().intValue());
        assertEquals("图书名不能为空", result.getMessage());
    }

    @Test
    @DisplayName("错误码与 HTTP 状态码的映射符合预期")
    void errorCodeHttpStatusMapping() {
        assertEquals(200, ErrorCode.SUCCESS.getHttpStatus().value());
        assertEquals(400, ErrorCode.PARAM_ERROR.getHttpStatus().value());
        assertEquals(401, ErrorCode.UNAUTHORIZED.getHttpStatus().value());
        assertEquals(403, ErrorCode.NOTE_PERMISSION_DENIED.getHttpStatus().value());
        assertEquals(404, ErrorCode.NOTE_NOT_FOUND.getHttpStatus().value());
        assertEquals(500, ErrorCode.SYSTEM_ERROR.getHttpStatus().value());
    }
}
