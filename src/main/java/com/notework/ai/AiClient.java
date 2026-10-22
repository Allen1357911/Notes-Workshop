package com.notework.ai;

import com.notework.common.enums.AiCapability;

import java.util.List;

/**
 * AI 客户端抽象。
 * <p>
 * 抽成接口的目的：真实实现（{@link DeepSeekAiClient}）与模拟实现（{@link MockAiClient}）可互换，
 * 单元测试与离线演示都不需要真的联网、也不花 token。
 * <p>
 * 两种调用形态：
 * <ul>
 *     <li>{@link #complete} —— 单轮：一段系统提示 + 一条用户提示（笔记总结/适宜人群/评分用）</li>
 *     <li>{@link #chat} —— 多轮：一段系统提示 + 完整消息数组（智能客服对话用）</li>
 * </ul>
 */
public interface AiClient {

    /**
     * 单轮调用模型完成一次生成。
     *
     * @throws com.notework.common.exception.BusinessException AI 未配置 / 调用失败 / 返回无法解析
     */
    AiCompletion complete(AiRequest request);

    /**
     * 多轮对话：把历史消息数组原样交给模型，由模型结合上下文回答。
     *
     * @param systemPrompt 客服系统提示（限定回答范围）
     * @param messages     按时间正序的消息数组，最后一条必须是用户的新消息
     * @throws com.notework.common.exception.BusinessException AI 未配置 / 调用失败
     */
    ChatCompletion chat(String systemPrompt, List<ChatMessage> messages);

    /** 一次生成请求 */
    record AiRequest(AiCapability capability, String systemPrompt, String userPrompt, int maxOutputTokens) {
    }

    /** 一次生成结果（含 token 用量，用于落库统计成本） */
    record AiCompletion(String content, String model,
                        int promptTokens, int completionTokens, int totalTokens, long costMs) {
    }

    /**
     * 一条对话消息。
     *
     * @param role    模型接口要求的角色：user / assistant
     * @param content 消息内容
     */
    record ChatMessage(String role, String content) {
    }

    /** 一次多轮对话结果 */
    record ChatCompletion(String content, String model,
                          int promptTokens, int completionTokens, int totalTokens, long costMs) {
    }
}
