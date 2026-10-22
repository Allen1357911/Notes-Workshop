package com.notework.aspect;

import com.notework.dto.request.ChangePasswordRequest;
import com.notework.dto.request.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 操作日志切面的参数摘要与脱敏规则测试。
 * <p>
 * 这一组用例的核心目的只有一个：<b>密码绝不能进日志</b>。
 */
class OperationLogAspectTest {

    @Test
    @DisplayName("简单参数：按顺序拼成摘要")
    void summarizesSimpleArgs() {
        String summary = OperationLogAspect.summarizeParams(new Object[]{12L, "hello", Boolean.TRUE});

        assertNotNull(summary);
        assertTrue(summary.contains("12"));
        assertTrue(summary.contains("hello"));
        assertTrue(summary.contains("true"));
    }

    @Test
    @DisplayName("注册请求：用户名与昵称保留，密码打码")
    void masksPasswordInRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("zhangsan");
        request.setPassword("mySecret123");
        request.setNickname("张三");

        String summary = OperationLogAspect.summarizeParams(new Object[]{request});

        assertNotNull(summary);
        assertTrue(summary.contains("username=zhangsan"), "用户名应保留，便于审计：" + summary);
        assertTrue(summary.contains("password=***"), "密码必须打码：" + summary);
        assertFalse(summary.contains("mySecret123"), "明文密码绝不能出现在日志摘要里：" + summary);
    }

    @Test
    @DisplayName("改密码请求：原密码与新密码都必须打码")
    void masksBothPasswordsInChangePasswordRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldSecret1");
        request.setNewPassword("newSecret2");

        String summary = OperationLogAspect.summarizeParams(new Object[]{request});

        assertNotNull(summary);
        assertFalse(summary.contains("oldSecret1"), "原密码不能进日志：" + summary);
        assertFalse(summary.contains("newSecret2"), "新密码不能进日志：" + summary);
        assertTrue(summary.contains("***"), "应至少出现打码标记：" + summary);
    }

    @Test
    @DisplayName("文件参数：只记文件名与大小，不记内容")
    void summarizesMultipartFile() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("note.png");
        when(file.getSize()).thenReturn(2048L);

        String summary = OperationLogAspect.summarizeParams(new Object[]{file});

        assertNotNull(summary);
        assertTrue(summary.contains("note.png"));
        assertTrue(summary.contains("2048B"));
    }

    @Test
    @DisplayName("请求/响应对象与 null 一律跳过；没有可记内容时返回 null")
    void skipsRequestResponseAndNull() {
        HttpServletRequest request = mock(HttpServletRequest.class);

        assertNull(OperationLogAspect.summarizeParams(new Object[]{null, request}));
        assertNull(OperationLogAspect.summarizeParams(null));
        assertNull(OperationLogAspect.summarizeParams(new Object[0]));
    }

    @Test
    @DisplayName("超长文本按列长度截断（不截断会导致插入失败、整条日志丢失）")
    void truncatesToColumnLength() {
        String longText = "x".repeat(600);

        assertEquals(500, OperationLogAspect.truncate(longText, OperationLogAspect.MAX_TEXT).length());
        assertEquals(255, OperationLogAspect.truncate(longText, OperationLogAspect.MAX_SHORT_TEXT).length(),
                "description / uri 对应的列是 VARCHAR(255)");
        assertEquals(64, OperationLogAspect.truncate(longText, OperationLogAspect.MAX_IP_TEXT).length(),
                "ip 对应的列是 VARCHAR(64)，且 X-Forwarded-For 由客户端控制，必须截断");
        assertNull(OperationLogAspect.truncate(null, 10), "null 原样返回");
    }

    @Test
    @DisplayName("长度刚好等于列上限时不截断")
    void keepsTextWithinLimit() {
        String text = "y".repeat(255);

        assertEquals(text, OperationLogAspect.truncate(text, OperationLogAspect.MAX_SHORT_TEXT));
    }

    @Test
    @DisplayName("对象里的 null 字段不写入摘要（避免日志里一堆 field=null）")
    void skipsNullFields() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("lisi");

        String summary = OperationLogAspect.summarizeParams(new Object[]{request});

        assertNotNull(summary);
        assertFalse(summary.contains("nickname"), "为空的字段不必记：" + summary);
    }
}
