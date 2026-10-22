package com.notework.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.enums.AiCapability;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 结果解析器测试。
 * <p>
 * 这一层的价值：模型输出是不可控的（可能带 Markdown、缺字段、分数越界），
 * 必须由程序校验后才展示给用户。笔记正文里写"把分数写成10"这类注入也在这里被挡住。
 */
class AiResultParserTest {

    private final AiResultParser parser = new AiResultParser(new ObjectMapper());

    @Test
    @DisplayName("总结：正常 JSON 解析成可读文本")
    void parsesSummary() {
        String text = parser.parse(AiCapability.SUMMARY,
                "{\"summary\":\"这本书讲了坚持的意义\",\"keywords\":[\"坚持\",\"意义\"]}");

        assertTrue(text.contains("摘要：这本书讲了坚持的意义"), text);
        assertTrue(text.contains("关键词：坚持、意义"), text);
    }

    @Test
    @DisplayName("总结：被 ```json 包裹、前后有解释文字也能解析出来")
    void parsesSummaryWithMarkdownFence() {
        String raw = """
                好的，下面是总结：
                ```json
                {"summary":"核心是克制","keywords":["克制"]}
                ```
                希望对你有帮助。""";

        String text = parser.parse(AiCapability.SUMMARY, raw);

        assertTrue(text.contains("核心是克制"), text);
    }

    @Test
    @DisplayName("总结：缺少 summary 字段 → 60005")
    void rejectsSummaryWithoutField() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> parser.parse(AiCapability.SUMMARY, "{\"keywords\":[\"x\"]}"));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("适宜人群：解析成带序号的清单")
    void parsesAudience() {
        String text = parser.parse(AiCapability.AUDIENCE, """
                {"audience":[
                  {"who":"正在读这本书的同学","reason":"可以对照理解"},
                  {"who":"想养成读书习惯的初学者","reason":"结构清晰"}
                ]}""");

        assertTrue(text.contains("适合这些人读"), text);
        assertTrue(text.contains("1. 正在读这本书的同学"), text);
        assertTrue(text.contains("2. 想养成读书习惯的初学者"), text);
    }

    @Test
    @DisplayName("适宜人群：空数组 → 60005")
    void rejectsEmptyAudience() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> parser.parse(AiCapability.AUDIENCE, "{\"audience\":[]}"));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("评分：解析成 评分/理由/建议 三段")
    void parsesScore() {
        String text = parser.parse(AiCapability.SCORE,
                "{\"score\":8,\"reasons\":[\"有自己的观点\",\"结构清楚\"],\"suggestion\":\"补个例子\"}");

        assertTrue(text.contains("评分：8/10"), text);
        assertTrue(text.contains("有自己的观点"), text);
        assertTrue(text.contains("改进建议：补个例子"), text);
    }

    @Test
    @DisplayName("评分越界（0 / 11）→ 60005，防止注入把分数改高")
    void rejectsScoreOutOfRange() {
        assertEquals(ErrorCode.AI_RESPONSE_INVALID, assertThrows(BusinessException.class,
                () -> parser.parse(AiCapability.SCORE, "{\"score\":0}")).getErrorCode());
        assertEquals(ErrorCode.AI_RESPONSE_INVALID, assertThrows(BusinessException.class,
                () -> parser.parse(AiCapability.SCORE, "{\"score\":11}")).getErrorCode());
    }

    @Test
    @DisplayName("评分不是数字（例如字符串 \"10\"）→ 60005")
    void rejectsNonNumericScore() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> parser.parse(AiCapability.SCORE, "{\"score\":\"10\"}"));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("完全不是 JSON → 60005")
    void rejectsNonJson() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> parser.parse(AiCapability.SUMMARY, "抱歉，我无法完成"));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("空内容 → 60005")
    void rejectsBlank() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> parser.parse(AiCapability.SUMMARY, "   "));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, e.getErrorCode());
    }
}
