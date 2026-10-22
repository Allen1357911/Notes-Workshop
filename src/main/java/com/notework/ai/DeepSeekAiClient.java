package com.notework.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 客户端（OpenAI 兼容协议）。
 * <p>
 * 直接用 Spring 自带的 {@link RestClient} 发 HTTP，不引第三方 SDK —— 好处是依赖少，
 * 而且每一行请求/解析逻辑都能自己讲清楚。
 * <p>
 * 单轮（{@link #complete}）与多轮（{@link #chat}）共用同一段 HTTP 与解析逻辑：
 * 区别只是 messages 里放一条用户提示，还是放"system + 历史 + 新消息"的完整数组。
 * <p>
 * 只在没开 mock 时注册；没配 key 时也能正常启动，等真正调用时才返回 60001。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "mock", havingValue = "false", matchIfMissing = true)
public class DeepSeekAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAiClient.class);

    private static final String CHAT_PATH = "/chat/completions";

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public DeepSeekAiClient(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        Duration timeout = Duration.ofSeconds(Math.max(1, properties.getTimeoutSeconds()));
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        List<Map<String, String>> messages = List.of(
                message("system", request.systemPrompt()),
                message("user", request.userPrompt()));
        return post(messages, request.maxOutputTokens());
    }

    @Override
    public ChatCompletion chat(String systemPrompt, List<ChatMessage> messages) {
        List<Map<String, String>> payload = new ArrayList<>();
        payload.add(message("system", systemPrompt));
        for (ChatMessage item : messages) {
            payload.add(message(item.role(), item.content()));
        }

        AiCompletion completion = post(payload, properties.getMaxOutputTokens());
        return new ChatCompletion(completion.content(), completion.model(),
                completion.promptTokens(), completion.completionTokens(),
                completion.totalTokens(), completion.costMs());
    }

    /**
     * 统一的 HTTP 调用与解析：单轮与多轮都走这里。
     *
     * @param messages  完整的消息数组（第 0 条通常是 system）
     * @param maxTokens 输出上限
     */
    private AiCompletion post(List<Map<String, String>> messages, int maxTokens) {
        if (!properties.isReady()) {
            throw new BusinessException(ErrorCode.AI_NOT_CONFIGURED);
        }

        long start = System.nanoTime();
        String body;
        try {
            body = restClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .body(buildRequestBody(messages, maxTokens))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // 不把上游返回体原样抛给前端（可能很长），只记日志 + 给用户一条可读的提示
            log.warn("DeepSeek 调用失败：HTTP {}，响应：{}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString()));
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, describeStatus(e.getStatusCode().value()));
        } catch (ResourceAccessException e) {
            log.warn("DeepSeek 调用超时或网络不可达：{}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 服务连接超时，请稍后重试");
        }

        long costMs = (System.nanoTime() - start) / 1_000_000;
        return parseCompletion(body, costMs);
    }

    /** 组装 OpenAI 兼容的请求体：模型、消息数组、非流式、输出上限、低温度（结果稳定） */
    private Map<String, Object> buildRequestBody(List<Map<String, String>> messages, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("max_tokens", maxTokens);
        body.put("temperature", 0.3);
        return body;
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("role", role);
        item.put("content", content);
        return item;
    }

    /** 从上游 JSON 里取 choices[0].message.content 与 usage 里的 token 数 */
    private AiCompletion parseCompletion(String body, long costMs) {
        if (!StringUtils.hasText(body)) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
            }
            JsonNode usage = root.path("usage");
            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);
            int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
            String model = root.path("model").asText(properties.getModel());
            return new AiCompletion(content, model, promptTokens, completionTokens, totalTokens, costMs);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("DeepSeek 返回内容解析失败：{}", e.toString());
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID);
        }
    }

    private String describeStatus(int status) {
        if (status == 401 || status == 403) {
            return "AI Key 无效或没有权限，请检查 DEEPSEEK_API_KEY";
        }
        if (status == 429) {
            return "AI 调用过于频繁，请稍后重试";
        }
        if (status >= 500) {
            return "AI 服务暂时不可用，请稍后重试";
        }
        return "AI 调用失败（HTTP " + status + "），请稍后重试";
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }
}
