package com.notework.dto.response;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理员看到的 AI 调用统计。
 */
public class AiStatsVO {

    /** 总调用次数 */
    private long totalCalls;

    private long successCalls;

    private long failedCalls;

    /** 成功率（0~100，保留一位小数） */
    private double successRate;

    /** 总 token 用量（用于估算成本） */
    private long totalTokens;

    /** 平均耗时（毫秒） */
    private long avgCostMs;

    /** 最大耗时（毫秒） */
    private long maxCostMs;

    /** 被用户确认保存的次数 */
    private long confirmedCalls;

    /** 当前使用的模型 */
    private String model;

    /** 是否处于模拟模式（不需要真实 key） */
    private boolean mock;

    /** 按能力分组 */
    private List<CapabilityStat> byCapability = new ArrayList<>();

    /** 最近 7 天趋势 */
    private List<DayStat> recentDays = new ArrayList<>();

    /** 按能力分组的统计项 */
    public static class CapabilityStat {

        private String capability;
        private String label;
        private long calls;
        private long tokens;
        private long avgCostMs;

        public CapabilityStat(String capability, String label, long calls, long tokens, long avgCostMs) {
            this.capability = capability;
            this.label = label;
            this.calls = calls;
            this.tokens = tokens;
            this.avgCostMs = avgCostMs;
        }

        public String getCapability() {
            return capability;
        }

        public String getLabel() {
            return label;
        }

        public long getCalls() {
            return calls;
        }

        public long getTokens() {
            return tokens;
        }

        public long getAvgCostMs() {
            return avgCostMs;
        }
    }

    /** 每日统计项 */
    public static class DayStat {

        private String day;
        private long calls;
        private long tokens;

        public DayStat(String day, long calls, long tokens) {
            this.day = day;
            this.calls = calls;
            this.tokens = tokens;
        }

        public String getDay() {
            return day;
        }

        public long getCalls() {
            return calls;
        }

        public long getTokens() {
            return tokens;
        }
    }

    public long getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(long totalCalls) {
        this.totalCalls = totalCalls;
    }

    public long getSuccessCalls() {
        return successCalls;
    }

    public void setSuccessCalls(long successCalls) {
        this.successCalls = successCalls;
    }

    public long getFailedCalls() {
        return failedCalls;
    }

    public void setFailedCalls(long failedCalls) {
        this.failedCalls = failedCalls;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getAvgCostMs() {
        return avgCostMs;
    }

    public void setAvgCostMs(long avgCostMs) {
        this.avgCostMs = avgCostMs;
    }

    public long getMaxCostMs() {
        return maxCostMs;
    }

    public void setMaxCostMs(long maxCostMs) {
        this.maxCostMs = maxCostMs;
    }

    public long getConfirmedCalls() {
        return confirmedCalls;
    }

    public void setConfirmedCalls(long confirmedCalls) {
        this.confirmedCalls = confirmedCalls;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public List<CapabilityStat> getByCapability() {
        return byCapability;
    }

    public void setByCapability(List<CapabilityStat> byCapability) {
        this.byCapability = byCapability;
    }

    public List<DayStat> getRecentDays() {
        return recentDays;
    }

    public void setRecentDays(List<DayStat> recentDays) {
        this.recentDays = recentDays;
    }
}
