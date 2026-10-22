package com.notework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AI 相关配置，对应 application.yml 里的 app.ai.*
 * <p>
 * 安全要点：{@code apiKey} 只从环境变量 {@code DEEPSEEK_API_KEY} 读取，
 * 代码、配置文件、仓库里都不出现真实密钥。
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** 是否使用模拟实现（离线测试 / 无 key 演示） */
    private boolean mock = false;

    /** DeepSeek API Key，来自环境变量 */
    private String apiKey = "";

    /** 接口地址（OpenAI 兼容） */
    private String baseUrl = "https://api.deepseek.com";

    /** 模型名 */
    private String model = "deepseek-flash";

    /** 单次调用超时（秒） */
    private int timeoutSeconds = 30;

    /** 送入模型的正文最大字数 */
    private int maxInputChars = 4000;

    /** 每人每天调用配额 */
    private int dailyQuota = 20;

    /** 单次生成的最大输出 token */
    private int maxOutputTokens = 800;

    /** AI 客服：每人每天可发多少条用户消息（独立配额，不与笔记 AI 共享） */
    private int chatQuota = 100;

    /** AI 客服：滑动窗口轮数（1 轮 = 用户消息 + AI 回复） */
    private int chatWindowTurns = 10;

    /** AI 客服：会话详情一次返回最近多少条消息 */
    private int chatHistoryLimit = 50;

    /** AI 客服：单条用户消息最大字符数 */
    private int chatMaxChars = 1000;

    /** AI 客服：每人每天最多新建多少个会话（防止空会话被无限刷） */
    private int chatDailySessionLimit = 100;

    /**
     * 是否已具备真实调用条件：配了 key 且没开 mock。
     * 未配置时接口返回 60001，而不是启动失败 —— AI 是增强功能，不能拖垮主流程。
     */
    public boolean isReady() {
        return !mock && StringUtils.hasText(apiKey);
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxInputChars() {
        return maxInputChars;
    }

    public void setMaxInputChars(int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    public int getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(int dailyQuota) {
        this.dailyQuota = dailyQuota;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public int getChatQuota() {
        return chatQuota;
    }

    public void setChatQuota(int chatQuota) {
        this.chatQuota = chatQuota;
    }

    public int getChatWindowTurns() {
        return chatWindowTurns;
    }

    public void setChatWindowTurns(int chatWindowTurns) {
        this.chatWindowTurns = chatWindowTurns;
    }

    public int getChatHistoryLimit() {
        return chatHistoryLimit;
    }

    public void setChatHistoryLimit(int chatHistoryLimit) {
        this.chatHistoryLimit = chatHistoryLimit;
    }

    public int getChatMaxChars() {
        return chatMaxChars;
    }

    public void setChatMaxChars(int chatMaxChars) {
        this.chatMaxChars = chatMaxChars;
    }

    public int getChatDailySessionLimit() {
        return chatDailySessionLimit;
    }

    public void setChatDailySessionLimit(int chatDailySessionLimit) {
        this.chatDailySessionLimit = chatDailySessionLimit;
    }
}
