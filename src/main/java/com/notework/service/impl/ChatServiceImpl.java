package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.ai.AiClient;
import com.notework.ai.ChatPromptTemplates;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AiCapability;
import com.notework.common.enums.ChatRole;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.common.util.BizAssert;
import com.notework.config.AiProperties;
import com.notework.dto.request.SendChatMessageRequest;
import com.notework.dto.response.ChatMessageVO;
import com.notework.dto.response.ChatReplyVO;
import com.notework.dto.response.ChatSessionDetailVO;
import com.notework.dto.response.ChatSessionVO;
import com.notework.entity.AiLog;
import com.notework.entity.ChatMessage;
import com.notework.entity.ChatSession;
import com.notework.interceptor.UserContext;
import com.notework.mapper.AiLogMapper;
import com.notework.mapper.ChatMessageMapper;
import com.notework.mapper.ChatSessionMapper;
import com.notework.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI 智能客服实现。
 * <p>
 * 关键取舍：
 * <ul>
 *     <li><b>无状态</b>：每次请求都从数据库重建上下文，重启/多端/多标签页行为一致；</li>
 *     <li><b>归属校验只有一个入口</b> {@link #loadOwnedSession}：SQL 同时带 id 与 user_id，
 *         查不到就抛 60007（不区分"不存在"与"不是你的"，避免探测）；</li>
 *     <li><b>上下文只按 sessionId 取</b>，绝不按 userId 取 —— 这是"同一用户不同会话互不污染"的保证；</li>
 *     <li><b>模型调用在事务外</b>：先把 AI 调完，再用 {@link TransactionTemplate} 把"写两条消息 + 更新会话 + 写审计"
 *         包成一个小事务，避免持着数据库连接等外部 HTTP；</li>
 *     <li><b>失败不入库、不扣配额，但审计必记</b>。</li>
 * </ul>
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /** 会话标题最多取首条成功消息的前 30 个字 */
    private static final int TITLE_MAX_CHARS = 30;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final AiLogMapper aiLogMapper;
    private final AiClient aiClient;
    private final AiProperties properties;
    private final TransactionTemplate transactionTemplate;

    public ChatServiceImpl(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper,
                           AiLogMapper aiLogMapper, AiClient aiClient,
                           AiProperties properties, TransactionTemplate transactionTemplate) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.aiLogMapper = aiLogMapper;
        this.aiClient = aiClient;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ChatSessionVO createSession() {
        Long me = UserContext.requireUserId();

        // 空会话创建得极便宜，但没有上限就会被无限刷；按"今天建了多少个"限制（删掉的也算，防刷）
        int limit = Math.max(1, properties.getChatDailySessionLimit());
        long createdToday = countTodaySessions(me);
        if (createdToday >= limit) {
            throw new BusinessException(ErrorCode.CHAT_SESSION_LIMIT_EXCEEDED,
                    "今天已经新建了 " + createdToday + " 个会话（上限 " + limit + " 个），明天再来吧");
        }

        ChatSession session = new ChatSession();
        session.setUserId(me);
        session.setTitle(ChatSession.DEFAULT_TITLE);
        session.setMessageCount(0);
        // 空会话也要能参与列表排序：last_message_time 用创建时间
        session.setLastMessageTime(LocalDateTime.now());
        sessionMapper.insert(session);

        // 回查一次：create_time 由数据库默认值生成，插入后的实体上没有它
        return ChatSessionVO.from(loadOwnedSession(session.getId()));
    }

    @Override
    public PageResult<ChatSessionVO> pageSessions(PageQuery pageQuery) {
        Long me = UserContext.requireUserId();
        Page<ChatSession> page = sessionMapper.selectPage(
                new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, me)
                        .orderByDesc(ChatSession::getLastMessageTime)
                        .orderByDesc(ChatSession::getId));

        List<ChatSessionVO> list = new ArrayList<>();
        for (ChatSession session : page.getRecords()) {
            list.add(ChatSessionVO.from(session));
        }
        return PageResult.of(list, page.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public ChatSessionDetailVO detail(Long sessionId) {
        ChatSession session = loadOwnedSession(sessionId);

        int limit = Math.max(1, properties.getChatHistoryLimit());
        List<ChatMessage> recent = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + limit));

        // 倒序取最近 N 条，再反转成时间正序
        List<ChatMessageVO> messages = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {
            messages.add(ChatMessageVO.from(recent.get(i)));
        }
        return new ChatSessionDetailVO(ChatSessionVO.from(session), messages);
    }

    @Override
    public ChatReplyVO send(Long sessionId, SendChatMessageRequest request) {
        Long me = UserContext.requireUserId();
        String content = request == null || request.getContent() == null ? "" : request.getContent().trim();

        int maxChars = Math.max(1, properties.getChatMaxChars());
        if (content.length() > maxChars) {
            throw new BusinessException(ErrorCode.CHAT_MESSAGE_TOO_LONG,
                    "消息最多 " + maxChars + " 字，当前 " + content.length() + " 字，请精简后再发");
        }

        // 归属校验的唯一入口
        ChatSession session = loadOwnedSession(sessionId);

        long usedToday = countTodayUserMessages(me);
        int quota = Math.max(1, properties.getChatQuota());
        if (usedToday >= quota) {
            throw new BusinessException(ErrorCode.CHAT_QUOTA_EXCEEDED,
                    "今日 AI 客服提问次数已用完（每天 " + quota + " 条），明天再来吧");
        }

        List<AiClient.ChatMessage> messages = buildWindow(sessionId, content);
        // 用 stream 一次性算出来，保证它是 effectively final（后面要在事务 lambda 里用）
        final int inputChars = messages.stream().mapToInt(item -> item.content().length()).sum();

        AiClient.ChatCompletion completion;
        try {
            completion = aiClient.chat(ChatPromptTemplates.SYSTEM, messages);
        } catch (BusinessException e) {
            // 失败：不落消息、不扣配额；但审计必须留一条（规格要求计入成功/失败次数）。
            // 审计自己也失败时只 warn：不能让它把真正的业务错误（60010/60001）盖成 500。
            try {
                writeAudit(me, sessionId, inputChars, null, e.getMessage());
            } catch (RuntimeException auditError) {
                log.warn("客服失败审计写入异常（不影响给用户的错误提示）：{}", auditError.toString());
            }
            log.warn("AI 客服调用失败：用户[{}] 会话[{}] 原因[{}]", me, sessionId, e.getMessage());
            // 未配置 Key 是"环境问题"，保留 60001 原样返回（与笔记 AI 的行为一致），
            // 其余失败统一成 60010，避免把上游细节透给前端
            if (e.getErrorCode() == ErrorCode.AI_NOT_CONFIGURED) {
                throw e;
            }
            throw new BusinessException(ErrorCode.CHAT_CALL_FAILED, e.getMessage());
        }

        // 截到秒：数据库 DATETIME 只存到秒，如果内存里带纳秒，
        // 刚发完的消息会显示 15:19:45.419，重新加载后又是 15:19:45 —— 同一个字段两种样子
        LocalDateTime now = LocalDateTime.now().withNano(0);
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setUserId(me);
        userMessage.setRole(ChatRole.USER);
        userMessage.setContent(content);
        userMessage.setPromptTokens(0);
        userMessage.setCompletionTokens(0);
        userMessage.setTotalTokens(0);
        userMessage.setCostMs(0L);
        userMessage.setCreateTime(now);

        ChatMessage replyMessage = new ChatMessage();
        replyMessage.setSessionId(sessionId);
        replyMessage.setUserId(me);
        replyMessage.setRole(ChatRole.ASSISTANT);
        replyMessage.setContent(completion.content());
        replyMessage.setPromptTokens(completion.promptTokens());
        replyMessage.setCompletionTokens(completion.completionTokens());
        replyMessage.setTotalTokens(completion.totalTokens());
        replyMessage.setCostMs(completion.costMs());
        replyMessage.setCreateTime(now);

        // 标题只在"当前仍是新会话"时取本条内容，之后不再变化
        String newTitle = ChatSession.DEFAULT_TITLE.equals(session.getTitle()) ? titleOf(content) : null;

        // lambda 里只能引用 effectively final 的变量，这里把 try/catch 里赋值的 completion 固定下来
        final AiClient.ChatCompletion auditCompletion = completion;
        final String titleToSet = newTitle;

        transactionTemplate.executeWithoutResult(status -> {
            messageMapper.insert(userMessage);
            messageMapper.insert(replyMessage);

            ChatSession update = new ChatSession();
            update.setId(sessionId);
            update.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
            update.setLastMessageTime(now);
            if (titleToSet != null) {
                update.setTitle(titleToSet);
            }
            sessionMapper.updateById(update);

            writeAudit(me, sessionId, inputChars, auditCompletion, null);
        });

        String sessionTitle = titleToSet != null ? titleToSet : session.getTitle();
        int remaining = (int) Math.max(0, quota - (usedToday + 1));
        return new ChatReplyVO(ChatMessageVO.from(userMessage), ChatMessageVO.from(replyMessage),
                sessionTitle, remaining);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long sessionId) {
        // 先校验归属（不是自己的会话删不了），再物理删除消息与会话
        loadOwnedSession(sessionId);
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
        log.info("删除客服会话[{}]（消息一并物理删除）", sessionId);
    }

    /* ---------------- 内部方法 ---------------- */

    /**
     * 归属校验的唯一入口：一条 SQL 同时带 id 与 user_id。
     * 查不到统一抛 60007 —— 不区分"不存在"和"不是你的"，避免被用来探测会话是否存在。
     */
    private ChatSession loadOwnedSession(Long sessionId) {
        Long me = UserContext.requireUserId();
        ChatSession session = sessionId == null ? null : sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getId, sessionId)
                        .eq(ChatSession::getUserId, me));
        BizAssert.notNull(session, ErrorCode.CHAT_SESSION_NOT_FOUND);
        return session;
    }

    /**
     * 组装滑动窗口：最近 N 轮（N×2 条历史）+ 本次新消息。
     * 只按 sessionId 取，保证同一用户的不同会话互不污染。
     */
    private List<AiClient.ChatMessage> buildWindow(Long sessionId, String newContent) {
        int windowMessages = Math.max(1, properties.getChatWindowTurns()) * 2;
        List<ChatMessage> history = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + windowMessages));

        // 倒序取最近 N 条，反转成时间正序，模型才能正确理解上下文顺序。
        // 注意：先复制一份再反转 —— 不要依赖 Mapper 返回的集合是否可变（万一将来换成不可变列表会直接抛异常）
        List<ChatMessage> ordered = new ArrayList<>(history);
        Collections.reverse(ordered);

        List<AiClient.ChatMessage> messages = new ArrayList<>();
        for (ChatMessage item : ordered) {
            messages.add(new AiClient.ChatMessage(item.getRole().toApiRole(), item.getContent()));
        }
        messages.add(new AiClient.ChatMessage("user", newContent));
        return messages;
    }

    /** 今天已经新建了多少个会话（按创建时间算：删掉的也计入，防止"建了删、删了建"刷数量） */
    private long countTodaySessions(Long userId) {
        Long count = sessionMapper.selectCount(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .ge(ChatSession::getCreateTime, LocalDate.now().atStartOfDay()));
        return count == null ? 0 : count;
    }

    /** 今日已用掉多少条提问配额：只统计成功的客服调用（失败不占配额） */
    private long countTodayUserMessages(Long userId) {
        Long count = aiLogMapper.selectCount(new LambdaQueryWrapper<AiLog>()
                .eq(AiLog::getUserId, userId)
                .eq(AiLog::getCapability, AiCapability.CHAT)
                .eq(AiLog::getSuccess, 1)
                .ge(AiLog::getCreateTime, LocalDate.now().atStartOfDay()));
        return count == null ? 0 : count;
    }

    /**
     * 写审计（只记元数据，不存对话正文）。
     *
     * @param completion 成功时的模型返回；失败传 null
     */
    private void writeAudit(Long userId, Long sessionId, int inputChars,
                            AiClient.ChatCompletion completion, String errorMsg) {
        AiLog record = new AiLog();
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setCapability(AiCapability.CHAT);
        record.setInputChars(inputChars);
        record.setConfirmed(0);
        if (completion == null) {
            record.setSuccess(0);
            record.setErrorMsg(truncate(errorMsg, 500));
            record.setCostMs(0L);
        } else {
            record.setSuccess(1);
            record.setModel(completion.model());
            record.setPromptTokens(completion.promptTokens());
            record.setCompletionTokens(completion.completionTokens());
            record.setTotalTokens(completion.totalTokens());
            record.setCostMs(completion.costMs());
        }
        aiLogMapper.insert(record);
    }

    /** 标题取首条成功消息前 30 字，超出加省略号 */
    private String titleOf(String content) {
        String text = content == null ? "" : content.trim();
        if (text.length() <= TITLE_MAX_CHARS) {
            return text;
        }
        return text.substring(0, TITLE_MAX_CHARS) + "…";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
