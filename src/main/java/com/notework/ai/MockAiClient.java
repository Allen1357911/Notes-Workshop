package com.notework.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 模拟实现（{@code app.ai.mock=true} 时启用）。
 * <p>
 * 用途有三个：
 * <ol>
 *     <li>单元测试 / 接口测试 / 离线演示时不需要联网、不花 token；</li>
 *     <li>没配 API Key 时也能把"生成 → 确认保存 → 统计"这条链路完整跑通；</li>
 *     <li>多轮客服对话也能离线跑通，并在回复里报出"上下文条数"，方便验证滑动窗口是否生效。</li>
 * </ol>
 * 返回的是固定结构的假数据，但**结构与真实实现完全一致**，所以解析、校验、落库逻辑都是同一条路径。
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "mock", havingValue = "true")
public class MockAiClient implements AiClient {

    private static final String MODEL = "deepseek-flash(mock)";

    @Override
    public AiCompletion complete(AiRequest request) {
        long start = System.nanoTime();
        String content = switch (request.capability()) {
            case SUMMARY -> """
                    {"summary":"（模拟）这篇笔记梳理了书中最重要的观点，并结合自己的阅读体会做了归纳。","keywords":["核心观点","个人体会","阅读方法"]}""";
            case AUDIENCE -> """
                    {"audience":[
                      {"who":"正在读这本书的同学","reason":"可以对照自己的理解，快速抓住重点。"},
                      {"who":"想养成读书习惯的初学者","reason":"结构清晰、篇幅适中，容易坚持读完。"},
                      {"who":"复习备考的学生","reason":"摘要形式便于考前快速回顾。"}
                    ]}""";
            case SCORE -> """
                    {"score":8,"reasons":["有自己的观点，不只是摘抄原文","结构与层次清楚"],"suggestion":"可以补一两个书中的具体例子，让论点更有说服力。"}""";
            case CHAT -> throw new IllegalStateException("单轮接口不支持客服能力，请走 /api/ai/chat");
        };
        long costMs = Math.max(1, (System.nanoTime() - start) / 1_000_000);
        int promptTokens = Math.max(1, request.userPrompt().length() / 2);
        int completionTokens = Math.max(1, content.length() / 2);
        return new AiCompletion(content, MODEL, promptTokens, completionTokens,
                promptTokens + completionTokens, costMs);
    }

    /**
     * 多轮客服的模拟回复。
     * <p>
     * 特意在回复里带上"上下文条数"，这样接口测试可以直接断言滑动窗口是否被限制在
     * {@code 2 × 轮数 + 1} 条以内（历史 20 条 + 本次 1 条 = 21）。
     */
    @Override
    public ChatCompletion chat(String systemPrompt, List<ChatMessage> messages) {
        long start = System.nanoTime();
        String lastUser = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                lastUser = messages.get(i).content();
                break;
            }
        }
        String brief = lastUser.length() <= 30 ? lastUser : lastUser.substring(0, 30) + "…";
        String content = "（模拟客服·上下文 " + messages.size() + " 条）关于「" + brief
                + "」：这是离线模拟回复，用于验证多轮链路与上下文裁剪；真实环境会由 DeepSeek 结合上文作答。";

        long costMs = Math.max(1, (System.nanoTime() - start) / 1_000_000);
        int promptChars = systemPrompt.length();
        for (ChatMessage item : messages) {
            promptChars += item.content().length();
        }
        int promptTokens = Math.max(1, promptChars / 2);
        int completionTokens = Math.max(1, content.length() / 2);
        return new ChatCompletion(content, MODEL, promptTokens, completionTokens,
                promptTokens + completionTokens, costMs);
    }
}
