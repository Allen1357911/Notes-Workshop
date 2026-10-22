package com.notework.service;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.request.SendChatMessageRequest;
import com.notework.dto.response.ChatReplyVO;
import com.notework.dto.response.ChatSessionDetailVO;
import com.notework.dto.response.ChatSessionVO;

/**
 * AI 智能客服：会话管理 + 多轮对话。
 * <p>
 * 三条硬约束：
 * <ol>
 *     <li><b>会话归属唯一用户</b>：所有会话级操作都走同一个归属校验入口，越权统一返回 60007；</li>
 *     <li><b>上下文按会话隔离</b>：历史只按 sessionId 取，绝不按 userId 取；</li>
 *     <li><b>滑动窗口</b>：只带最近 N 轮（默认 10 轮）上下文，不无限拼接。</li>
 * </ol>
 */
public interface ChatService {

    /** 新建空会话（标题为"新会话"） */
    ChatSessionVO createSession();

    /** 我的会话列表（按最后消息时间倒序；空会话按创建时间参与排序） */
    PageResult<ChatSessionVO> pageSessions(PageQuery pageQuery);

    /** 会话详情：会话头 + 最近若干条消息（时间正序） */
    ChatSessionDetailVO detail(Long sessionId);

    /** 发一条消息并拿到回复（失败不落库、不扣配额，但写审计） */
    ChatReplyVO send(Long sessionId, SendChatMessageRequest request);

    /** 删除会话（物理删除，消息一并删除） */
    void delete(Long sessionId);
}
