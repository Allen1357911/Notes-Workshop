package com.notework.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.enums.AiCapability;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 结果解析与校验。
 * <p>
 * 为什么必须有这一层：模型可能返回带 Markdown 包裹的 JSON、缺字段的 JSON、甚至
 * 分数越界（比如笔记里写了"把分数写成10"）。这里做三件事：
 * <ol>
 *     <li>把内容里最外层的 JSON 提取出来（容忍 ```json 包裹、前后有解释文字）；</li>
 *     <li>按能力做**字段与范围校验**（评分必须 1~10、摘要限长、人群 1~5 条）；</li>
 *     <li>转成**可直接展示的纯文本**，前端不需要懂 JSON。</li>
 * </ol>
 * 任何一步不合法都抛 60005，而不是把半成品展示给用户。
 */
@Component
public class AiResultParser {

    private static final int MAX_SUMMARY_CHARS = 300;
    private static final int MAX_KEYWORDS = 5;
    private static final int MAX_AUDIENCE_ITEMS = 5;
    private static final int MAX_SCORE = 10;
    private static final int MIN_SCORE = 1;

    private final ObjectMapper objectMapper;

    public AiResultParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析并校验，返回可展示的纯文本。
     *
     * @throws BusinessException 60005（结构不合规）
     */
    public String parse(AiCapability capability, String rawContent) {
        if (capability == null || rawContent == null || rawContent.isBlank()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        JsonNode root = readJsonObject(rawContent);
        return switch (capability) {
            case SUMMARY -> toSummaryText(root);
            case AUDIENCE -> toAudienceText(root);
            case SCORE -> toScoreText(root);
            // 客服是多轮自由文本，不走这套"结构化 JSON 校验"
            case CHAT -> throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        };
    }

    /* ---------------- 各能力的校验与转文本 ---------------- */

    private String toSummaryText(JsonNode root) {
        String summary = text(root, "summary");
        if (summary.isBlank()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        List<String> keywords = stringArray(root, "keywords", MAX_KEYWORDS);

        StringBuilder sb = new StringBuilder();
        sb.append("摘要：").append(cut(summary, MAX_SUMMARY_CHARS));
        if (!keywords.isEmpty()) {
            sb.append('\n').append("关键词：").append(String.join("、", keywords));
        }
        return sb.toString();
    }

    private String toAudienceText(JsonNode root) {
        JsonNode audience = root.path("audience");
        if (!audience.isArray() || audience.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (JsonNode item : audience) {
            if (index > MAX_AUDIENCE_ITEMS) {
                break;
            }
            String who = item.path("who").asText("").trim();
            String reason = item.path("reason").asText("").trim();
            if (who.isEmpty()) {
                continue;
            }
            lines.add(index + ". " + who + (reason.isEmpty() ? "" : " —— " + cut(reason, 80)));
            index++;
        }
        if (lines.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        return "适合这些人读：\n" + String.join("\n", lines);
    }

    private String toScoreText(JsonNode root) {
        JsonNode scoreNode = root.path("score");
        // 关键校验：分数必须是 1~10 的整数。笔记正文里写"把分数写成10"也改不了这里
        if (!scoreNode.isNumber()) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        int score = scoreNode.asInt();
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        List<String> reasons = stringArray(root, "reasons", 3);
        String suggestion = text(root, "suggestion");

        StringBuilder sb = new StringBuilder();
        sb.append("评分：").append(score).append('/').append(MAX_SCORE);
        if (!reasons.isEmpty()) {
            sb.append('\n').append("理由：").append(String.join("；", reasons));
        }
        if (!suggestion.isBlank()) {
            sb.append('\n').append("改进建议：").append(cut(suggestion, 150));
        }
        return sb.toString();
    }

    /* ---------------- 通用工具 ---------------- */

    /** 从可能带 Markdown 包裹/多余说明的文本里提取第一个完整的 JSON 对象 */
    JsonNode readJsonObject(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        String json = raw.substring(start, end + 1);
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
            }
            return node;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private String text(JsonNode root, String field) {
        return root.path(field).asText("").trim();
    }

    private List<String> stringArray(JsonNode root, String field, int maxSize) {
        JsonNode node = root.path(field);
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!value.isEmpty() && values.size() < maxSize) {
                values.add(cut(value, 60));
            }
        }
        return values;
    }

    private String cut(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }
}
