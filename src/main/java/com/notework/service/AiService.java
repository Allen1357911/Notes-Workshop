package com.notework.service;

import com.notework.common.enums.AiCapability;
import com.notework.dto.request.AiConfirmRequest;
import com.notework.dto.response.AiGenerateVO;
import com.notework.dto.response.AiStatsVO;

/**
 * AI 辅助服务：总结 / 适宜人群 / 评分 + 确认保存 + 调用统计。
 * <p>
 * 核心约束（作业要求）：
 * <ol>
 *     <li>AI 结果**只作参考**，生成后**不会**自动写进笔记；</li>
 *     <li>必须由用户**手动确认**才保存；</li>
 *     <li>一篇笔记**最多保存一种** AI 结果（再保存会覆盖旧的）。</li>
 * </ol>
 */
public interface AiService {

    /**
     * 生成一次 AI 结果（预览，不写笔记）。
     *
     * @param noteId     笔记 id
     * @param capability 能力类型
     * @throws com.notework.common.exception.BusinessException 60001 未配置 / 60002 超配额 / 60004 调用失败 / 60005 返回不合规
     */
    AiGenerateVO generate(Long noteId, AiCapability capability);

    /** 确认保存：把某次生成结果写进笔记（覆盖旧结果） */
    void confirm(Long noteId, AiConfirmRequest request);

    /** 撤销：清空笔记上已保存的 AI 结果 */
    void cancel(Long noteId);

    /** 作者决定 AI 结果是否对其他人公开 */
    void updateVisibility(Long noteId, boolean aiPublic);

    /** 管理员统计 */
    AiStatsVO stats();
}
