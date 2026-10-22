package com.notework.common.util;

import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BizAssertTest {

    @Test
    @DisplayName("条件不成立时抛出带错误码的业务异常")
    void throwsWhenExpressionFalse() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> BizAssert.isTrue(false, ErrorCode.NOTE_PERMISSION_DENIED));
        assertEquals(ErrorCode.NOTE_PERMISSION_DENIED, e.getErrorCode());
        assertEquals("无权操作他人的笔记", e.getMessage());
    }

    @Test
    @DisplayName("条件成立时不抛异常")
    void passesWhenExpressionTrue() {
        assertDoesNotThrow(() -> BizAssert.isTrue(true, ErrorCode.NOTE_PERMISSION_DENIED));
        assertDoesNotThrow(() -> BizAssert.notNull("笔记", ErrorCode.NOTE_NOT_FOUND));
    }

    @Test
    @DisplayName("非空校验失败时可使用自定义提示")
    void notNullWithCustomMessage() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> BizAssert.notNull(null, ErrorCode.PARAM_ERROR, "图书名不能为空"));
        assertEquals(ErrorCode.PARAM_ERROR, e.getErrorCode());
        assertEquals("图书名不能为空", e.getMessage());
    }
}
