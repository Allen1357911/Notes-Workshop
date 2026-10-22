package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.api.Result;
import com.notework.dto.request.SendChatMessageRequest;
import com.notework.dto.response.ChatReplyVO;
import com.notework.dto.response.ChatSessionDetailVO;
import com.notework.dto.response.ChatSessionVO;
import com.notework.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 智能客服接口（平台答疑）。
 * <p>
 * 五个接口全部需要登录；所有会话级操作都在 Service 层做归属校验
 * （不是自己的会话一律返回 60007，且不返回任何内容）。
 */
@Tag(name = "AI 智能客服", description = "平台答疑助手：新建会话 / 会话列表 / 查看历史 / 多轮对话 / 删除会话；会话按用户隔离")
@RestController
@RequestMapping("/api/ai/chat/sessions")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "新建会话", description = "创建一个空会话，标题为「新会话」；之后发消息时由首条成功消息取名。")
    @OperationLog(module = "AI 客服", action = "新建会话", description = "创建一个空的客服会话")
    @PostMapping
    public Result<ChatSessionVO> create() {
        return Result.success(chatService.createSession());
    }

    @Operation(summary = "我的会话列表", description = "只返回当前登录用户的会话，按最后消息时间倒序；空会话按创建时间参与排序。")
    @GetMapping
    public Result<PageResult<ChatSessionVO>> list(PageQuery pageQuery) {
        return Result.success(chatService.pageSessions(pageQuery));
    }

    @Operation(summary = "会话详情（含最近消息）",
            description = "返回会话头信息与最近若干条消息（默认 50 条，按时间正序）。"
                    + "会话不存在或不属于当前用户时返回 60007，且不返回任何内容。")
    @GetMapping("/{id}")
    public Result<ChatSessionDetailVO> detail(@PathVariable("id") Long id) {
        return Result.success(chatService.detail(id));
    }

    @Operation(summary = "发送消息（多轮对话）",
            description = "带最近 10 轮上下文调用模型并返回回复。单条消息上限 1000 字；"
                    + "每人每天 100 条提问配额（独立于笔记 AI）；调用失败时不写对话记录、不扣配额，但会计入 AI 统计的失败次数。")
    @PostMapping("/{id}/messages")
    public Result<ChatReplyVO> send(@PathVariable("id") Long id,
                                    @Valid @RequestBody SendChatMessageRequest request) {
        return Result.success(chatService.send(id, request));
    }

    @Operation(summary = "删除会话", description = "物理删除会话及其全部消息（聊天记录属于用户私有数据，删干净）。")
    @OperationLog(module = "AI 客服", action = "删除会话", description = "删除客服会话及其消息")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        chatService.delete(id);
        return Result.success();
    }
}
