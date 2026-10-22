package com.notework.controller;

import com.notework.common.api.Result;
import com.notework.dto.response.AiStatsVO;
import com.notework.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员后台：AI 调用统计。
 */
@Tag(name = "管理员-AI 统计", description = "AI 调用次数、成功率、token 用量、耗时与按能力/按天分布，需要管理员权限")
@RestController
@RequestMapping("/api/admin/ai-stats")
public class AdminAiController {

    private final AiService aiService;

    public AdminAiController(AiService aiService) {
        this.aiService = aiService;
    }

    @Operation(summary = "AI 调用统计",
            description = "返回总调用次数、成功/失败数、成功率、token 总用量、平均与最大耗时、被确认保存的次数，"
                    + "以及按能力（总结/适宜人群/评分）和最近 7 天的分布。")
    @GetMapping
    public Result<AiStatsVO> stats() {
        return Result.success(aiService.stats());
    }
}
