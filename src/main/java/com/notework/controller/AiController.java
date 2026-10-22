package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.Result;
import com.notework.common.enums.AiCapability;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.dto.request.AiConfirmRequest;
import com.notework.dto.request.AiVisibilityRequest;
import com.notework.dto.response.AiGenerateVO;
import com.notework.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 辅助接口：总结 / 适宜人群 / 评分。
 * <p>
 * 关键设计：**生成**与**保存**是两个接口。
 * 生成只写 AI 调用日志、不碰笔记；只有用户点"保存"（确认接口）才会写进笔记。
 * 这样"AI 结果只作参考、必须手动确认"是接口层面的硬约束。
 */
@Tag(name = "AI 辅助", description = "笔记总结 / 适宜人群推荐 / 笔记评分；生成后需手动确认才保存，一篇笔记最多保存一种")
@RestController
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @Operation(summary = "生成 AI 结果（预览，不保存）",
            description = "capability 传 SUMMARY / AUDIENCE / SCORE。返回结果与本次生成记录 id（aiLogId），"
                    + "确认保存时要把 aiLogId 传回来。只有作者本人或管理员能调用；"
                    + "每人每天有配额（默认 20 次），未配置 API Key 返回 60001。")
    @OperationLog(module = "AI 辅助", action = "生成 AI 结果", description = "调用模型生成总结/适宜人群/评分")
    @PostMapping("/api/notes/{noteId}/ai/{capability}")
    public Result<AiGenerateVO> generate(@PathVariable("noteId") Long noteId,
                                         @PathVariable("capability") String capability) {
        AiCapability parsed = AiCapability.of(capability);
        // CHAT 是会话级能力，只能走 /api/ai/chat/sessions/{id}/messages
        if (parsed == null || !parsed.isNoteCapability()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "capability 只能是 SUMMARY / AUDIENCE / SCORE");
        }
        return Result.success(aiService.generate(noteId, parsed));
    }

    @Operation(summary = "确认保存 AI 结果",
            description = "只传 aiLogId（不传结果内容，保证用户改不了 AI 的结论）。"
                    + "保存后覆盖这篇笔记之前的 AI 结果 —— 一篇笔记最多保存一种。aiPublic 传 true 表示对其他人公开。")
    @OperationLog(module = "AI 辅助", action = "确认保存 AI 结果", description = "用户手动确认后才写入笔记")
    @PutMapping("/api/notes/{noteId}/ai-result")
    public Result<Void> confirm(@PathVariable("noteId") Long noteId,
                                @Valid @RequestBody AiConfirmRequest request) {
        aiService.confirm(noteId, request);
        return Result.success();
    }

    @Operation(summary = "撤销已保存的 AI 结果", description = "清空笔记上的 AI 结果，笔记正文不受影响。")
    @OperationLog(module = "AI 辅助", action = "撤销 AI 结果", description = "清空笔记上已保存的 AI 结果")
    @DeleteMapping("/api/notes/{noteId}/ai-result")
    public Result<Void> cancel(@PathVariable("noteId") Long noteId) {
        aiService.cancel(noteId);
        return Result.success();
    }

    @Operation(summary = "设置 AI 结果是否公开",
            description = "作者决定是否让别人看到这篇笔记的 AI 结果。公开的前提是笔记本身公开且已通过审核；"
                    + "关闭后只有作者本人与管理员可见。")
    @OperationLog(module = "AI 辅助", action = "设置 AI 结果公开状态", description = "作者选择是否公开 AI 结果")
    @PutMapping("/api/notes/{noteId}/ai-result/visibility")
    public Result<Void> updateVisibility(@PathVariable("noteId") Long noteId,
                                         @Valid @RequestBody AiVisibilityRequest request) {
        aiService.updateVisibility(noteId, Boolean.TRUE.equals(request.getAiPublic()));
        return Result.success();
    }
}
