package com.notework.dto.response;

/**
 * AI 生成结果（预览）。
 * <p>
 * 这个接口返回的内容**还没有保存到笔记上**：用户看过之后点"保存"才会写进笔记（见确认接口）。
 * 这样"AI 结果只作参考、必须手动确认"就是接口层面的强制约束，而不是靠前端自觉。
 */
public class AiGenerateVO {

    /** 本次生成在 t_ai_log 里的记录 id，确认保存时要带回来 */
    private Long aiLogId;

    /** SUMMARY / AUDIENCE / SCORE */
    private String capability;

    private String capabilityLabel;

    /** 校验并排版后的可展示文本 */
    private String text;

    private String model;

    private int promptTokens;

    private int completionTokens;

    private int totalTokens;

    private long costMs;

    /** 今天还剩多少次配额 */
    private int remainingQuota;

    /** 固定提示语：AI 生成，仅供参考 */
    private String disclaimer = "AI 生成内容，仅供参考；保存前请自行确认是否符合你的真实想法。";

    public Long getAiLogId() {
        return aiLogId;
    }

    public void setAiLogId(Long aiLogId) {
        this.aiLogId = aiLogId;
    }

    public String getCapability() {
        return capability;
    }

    public void setCapability(String capability) {
        this.capability = capability;
    }

    public String getCapabilityLabel() {
        return capabilityLabel;
    }

    public void setCapabilityLabel(String capabilityLabel) {
        this.capabilityLabel = capabilityLabel;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getCostMs() {
        return costMs;
    }

    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }

    public int getRemainingQuota() {
        return remainingQuota;
    }

    public void setRemainingQuota(int remainingQuota) {
        this.remainingQuota = remainingQuota;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
