package com.notework.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.ai.AiClient;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.ChatRole;
import com.notework.common.enums.Role;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.config.AiProperties;
import com.notework.dto.request.SendChatMessageRequest;
import com.notework.dto.response.ChatReplyVO;
import com.notework.dto.response.ChatSessionDetailVO;
import com.notework.dto.response.ChatSessionVO;
import com.notework.entity.AiLog;
import com.notework.entity.ChatMessage;
import com.notework.entity.ChatSession;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.AiLogMapper;
import com.notework.mapper.ChatMessageMapper;
import com.notework.mapper.ChatSessionMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 智能客服单元测试。
 * <p>
 * 覆盖三类最容易出错的点：
 * <ol>
 *     <li><b>归属隔离</b>：不是自己的会话一律 60007，且不做任何写入；</li>
 *     <li><b>滑动窗口</b>：最多最近 10 轮（20 条历史）+ 本次 1 条；</li>
 *     <li><b>失败语义</b>：不落消息、不扣配额，但审计必须留一条失败。</li>
 * </ol>
 * 模型调用全部用 Mock，不联网、不花 token。
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    private static final Long ME = 1L;
    private static final Long SESSION_ID = 10L;

    @Mock
    private ChatSessionMapper sessionMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private AiLogMapper aiLogMapper;

    @Mock
    private AiClient aiClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    private AiProperties properties;
    private ChatServiceImpl chatService;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ChatSession.class);
        TableInfoHelper.initTableInfo(assistant, ChatMessage.class);
        TableInfoHelper.initTableInfo(assistant, AiLog.class);
    }

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setChatQuota(100);
        properties.setChatWindowTurns(10);
        properties.setChatHistoryLimit(50);
        properties.setChatMaxChars(1000);
        properties.setMaxOutputTokens(800);

        chatService = new ChatServiceImpl(sessionMapper, messageMapper, aiLogMapper,
                aiClient, properties, transactionTemplate);
        UserContext.set(new LoginUser(ME, "alice", Role.USER));

        // 事务模板是 mock：直接把 lambda 跑掉，这样能看到"事务内"的写入
        // 用 lenient：不是每个用例都会走到事务（比如越权、超长、超配额都在事务之前就返回了）
        lenient().doAnswer(invocation -> {
            java.util.function.Consumer<org.springframework.transaction.TransactionStatus> action =
                    invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /* ---------------- 新建 / 列表 ---------------- */

    @Test
    @DisplayName("新建会话：标题为「新会话」、消息数 0，并回查返回")
    void createSession() {
        when(sessionMapper.insert(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(SESSION_ID);
            return 1;
        });
        when(sessionMapper.selectOne(any())).thenReturn(session(ChatSession.DEFAULT_TITLE, 0));

        ChatSessionVO vo = chatService.createSession();

        assertEquals(SESSION_ID, vo.getId());
        assertEquals("新会话", vo.getTitle());
        assertEquals(0, vo.getMessageCount());
        assertNotNull(vo.getCreateTime(), "回查后 createTime 不能是 null");
    }

    @Test
    @DisplayName("会话列表：只查自己的会话，按最后消息时间倒序")
    void pageSessions() {
        Page<ChatSession> page = new Page<>(1, 10);
        page.setRecords(List.of(session("我的会话", 4)));
        page.setTotal(1);
        when(sessionMapper.selectPage(any(), any())).thenReturn(page);

        PageResult<ChatSessionVO> result = chatService.pageSessions(new PageQuery());

        assertEquals(1, result.getTotal());
        assertEquals("我的会话", result.getList().get(0).getTitle());
    }

    /* ---------------- 详情 ---------------- */

    @Test
    @DisplayName("会话详情：消息按时间正序返回（库里是倒序取最近 N 条）")
    void detailReturnsAscendingMessages() {
        when(sessionMapper.selectOne(any())).thenReturn(session("我的会话", 4));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(2L, ChatRole.ASSISTANT, "第二条"),
                message(1L, ChatRole.USER, "第一条")));

        ChatSessionDetailVO detail = chatService.detail(SESSION_ID);

        assertEquals(2, detail.getMessages().size());
        assertEquals("第一条", detail.getMessages().get(0).getContent());
        assertEquals("第二条", detail.getMessages().get(1).getContent());
    }

    @Test
    @DisplayName("会话详情：不是自己的会话 → 60007，且不读消息")
    void detailRejectsOtherUsersSession() {
        when(sessionMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> chatService.detail(SESSION_ID));

        assertEquals(ErrorCode.CHAT_SESSION_NOT_FOUND, e.getErrorCode());
        verify(messageMapper, never()).selectList(any());
    }

    /* ---------------- 发消息 ---------------- */

    @Test
    @DisplayName("发消息成功：写两条消息、更新会话、写成功审计")
    void sendSuccess() {
        when(sessionMapper.selectOne(any())).thenReturn(session(ChatSession.DEFAULT_TITLE, 0));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.chat(any(), any())).thenReturn(completion("你好，我是客服", 300, 50, 350, 900L));

        ChatReplyVO vo = chatService.send(SESSION_ID, request("怎么把笔记设为私有？"));

        assertEquals("你好，我是客服", vo.getReply().getContent());
        assertEquals(350, vo.getReply().getTotalTokens());
        assertEquals("怎么把笔记设为私有？", vo.getUserMessage().getContent());
        assertEquals(100 - 1, vo.getRemainingQuota());
        // 首条成功消息决定标题
        assertEquals("怎么把笔记设为私有？", vo.getSessionTitle());

        verify(messageMapper, times(2)).insert(any(ChatMessage.class));
        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).updateById(sessionCaptor.capture());
        assertEquals(2, sessionCaptor.getValue().getMessageCount());
        assertEquals("怎么把笔记设为私有？", sessionCaptor.getValue().getTitle());

        ArgumentCaptor<AiLog> logCaptor = ArgumentCaptor.forClass(AiLog.class);
        verify(aiLogMapper).insert(logCaptor.capture());
        AiLog log = logCaptor.getValue();
        assertEquals(1, log.getSuccess());
        assertEquals(350, log.getTotalTokens());
        assertEquals(SESSION_ID, log.getSessionId());
        assertNull(log.getNoteId(), "客服审计不该带笔记 id");
        assertNull(log.getResult(), "审计只记元数据，不存对话正文");
    }

    @Test
    @DisplayName("发消息：标题已存在时不再改名")
    void sendKeepsExistingTitle() {
        when(sessionMapper.selectOne(any())).thenReturn(session("旧标题", 6));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.chat(any(), any())).thenReturn(completion("回复", 10, 5, 15, 10L));

        ChatReplyVO vo = chatService.send(SESSION_ID, request("这次问点别的"));

        assertEquals("旧标题", vo.getSessionTitle());
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertNull(captor.getValue().getTitle(), "非首轮不应再次修改标题");
        assertEquals(8, captor.getValue().getMessageCount());
    }

    @Test
    @DisplayName("发消息：标题取前 30 字并加省略号")
    void sendTruncatesLongTitle() {
        when(sessionMapper.selectOne(any())).thenReturn(session(ChatSession.DEFAULT_TITLE, 0));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.chat(any(), any())).thenReturn(completion("回复", 10, 5, 15, 10L));

        String longQuestion = "这是一个很长的问题".repeat(5);
        ChatReplyVO vo = chatService.send(SESSION_ID, request(longQuestion));

        assertEquals(31, vo.getSessionTitle().length(), "30 个字 + 一个省略号");
        assertTrue(vo.getSessionTitle().endsWith("…"));
    }

    @Test
    @DisplayName("发消息失败：不写消息、不改会话，但审计记一条失败")
    void sendFailureWritesAuditOnly() {
        when(sessionMapper.selectOne(any())).thenReturn(session(ChatSession.DEFAULT_TITLE, 0));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.chat(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 服务暂时不可用"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> chatService.send(SESSION_ID, request("问点什么")));

        assertEquals(ErrorCode.CHAT_CALL_FAILED, e.getErrorCode());
        verify(messageMapper, never()).insert(any(ChatMessage.class));
        verify(sessionMapper, never()).updateById(any(ChatSession.class));

        ArgumentCaptor<AiLog> captor = ArgumentCaptor.forClass(AiLog.class);
        verify(aiLogMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getSuccess());
        assertTrue(captor.getValue().getErrorMsg().contains("不可用"));
    }

    @Test
    @DisplayName("发消息：超过 1000 字 → 60008，且不调用模型")
    void sendRejectsTooLongMessage() {
        // 长度校验放在最前面（纯内存判断、不查库）：超长请求连会话都不必查
        BusinessException e = assertThrows(BusinessException.class,
                () -> chatService.send(SESSION_ID, request("字".repeat(1001))));

        assertEquals(ErrorCode.CHAT_MESSAGE_TOO_LONG, e.getErrorCode());
        assertTrue(e.getMessage().contains("1001"), "提示里要给出当前字数：" + e.getMessage());
        verify(aiClient, never()).chat(any(), any());
        verify(sessionMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("发消息：今日配额用尽 → 60009，且不调用模型")
    void sendRejectsWhenQuotaExceeded() {
        when(sessionMapper.selectOne(any())).thenReturn(session("会话", 0));
        when(aiLogMapper.selectCount(any())).thenReturn(100L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> chatService.send(SESSION_ID, request("再问一次")));

        assertEquals(ErrorCode.CHAT_QUOTA_EXCEEDED, e.getErrorCode());
        verify(aiClient, never()).chat(any(), any());
    }

    @Test
    @DisplayName("滑动窗口：最多带 10 轮（20 条历史）+ 本次 1 条")
    void sendTrimsHistoryToWindow() {
        when(sessionMapper.selectOne(any())).thenReturn(session("会话", 20));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);

        // 先按"时间正序"造 20 条（第 0、2、4… 条是用户消息），再反转成数据库 orderByDesc 的返回顺序
        List<ChatMessage> oldestFirst = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            oldestFirst.add(message(100L + i, i % 2 == 0 ? ChatRole.USER : ChatRole.ASSISTANT, "历史" + i));
        }
        List<ChatMessage> descFromDb = new ArrayList<>(oldestFirst);
        java.util.Collections.reverse(descFromDb);
        when(messageMapper.selectList(any())).thenReturn(descFromDb);
        when(aiClient.chat(any(), any())).thenReturn(completion("回复", 10, 5, 15, 10L));

        chatService.send(SESSION_ID, request("新问题"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiClient.ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiClient).chat(any(), captor.capture());
        List<AiClient.ChatMessage> sent = captor.getValue();
        assertEquals(21, sent.size(), "20 条历史 + 1 条新消息");
        assertEquals("user", sent.get(sent.size() - 1).role());
        assertEquals("新问题", sent.get(sent.size() - 1).content());
        // 反转成功后应当恢复成时间正序：最早的一条是用户消息
        assertEquals("user", sent.get(0).role());
        assertEquals("历史0", sent.get(0).content());
    }

    @Test
    @DisplayName("发消息：会话不属于自己 → 60007，且不调用模型")
    void sendRejectsOtherUsersSession() {
        when(sessionMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> chatService.send(SESSION_ID, request("问点什么")));

        assertEquals(ErrorCode.CHAT_SESSION_NOT_FOUND, e.getErrorCode());
        verify(aiClient, never()).chat(any(), any());
    }

    /* ---------------- 删除 ---------------- */

    @Test
    @DisplayName("删除会话：先删消息再删会话")
    void deleteRemovesMessagesThenSession() {
        when(sessionMapper.selectOne(any())).thenReturn(session("会话", 4));

        chatService.delete(SESSION_ID);

        verify(messageMapper).delete(any());
        verify(sessionMapper).deleteById(SESSION_ID);
    }

    @Test
    @DisplayName("删除会话：他人的会话 → 60007，且不删任何数据")
    void deleteRejectsOtherUsersSession() {
        when(sessionMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> chatService.delete(SESSION_ID));

        assertEquals(ErrorCode.CHAT_SESSION_NOT_FOUND, e.getErrorCode());
        verify(messageMapper, never()).delete(any());
        verify(sessionMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("发消息失败：未配置 Key 时保留 60001（不吞成 60010），审计仍记一条")
    void sendKeepsNotConfiguredCode() {
        when(sessionMapper.selectOne(any())).thenReturn(session(ChatSession.DEFAULT_TITLE, 0));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.chat(any(), any())).thenThrow(new BusinessException(ErrorCode.AI_NOT_CONFIGURED));

        BusinessException e = assertThrows(BusinessException.class,
                () -> chatService.send(SESSION_ID, request("问点什么")));

        assertEquals(ErrorCode.AI_NOT_CONFIGURED, e.getErrorCode(),
                "环境没配好应该原样告诉用户是 60001，而不是伪装成 60010 调用失败");
        verify(messageMapper, never()).insert(any(ChatMessage.class));
        verify(aiLogMapper).insert(any(AiLog.class));
    }

    @Test
    @DisplayName("滑动窗口：Mapper 返回不可变列表时也不能抛异常（先复制再反转）")
    void sendWorksWithImmutableHistory() {
        when(sessionMapper.selectOne(any())).thenReturn(session("会话", 2));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);
        // List.of 是不可变列表：如果实现里直接 Collections.reverse(history) 会抛 UnsupportedOperationException
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(2L, ChatRole.ASSISTANT, "上一轮的回复"),
                message(1L, ChatRole.USER, "上一轮的提问")));
        when(aiClient.chat(any(), any())).thenReturn(completion("回复", 10, 5, 15, 10L));

        ChatReplyVO vo = chatService.send(SESSION_ID, request("新问题"));

        assertEquals("回复", vo.getReply().getContent());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AiClient.ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(aiClient).chat(any(), captor.capture());
        assertEquals(3, captor.getValue().size(), "2 条历史 + 1 条新消息");
        assertEquals("上一轮的提问", captor.getValue().get(0).content(), "反转后最早的一条在前");
    }

    @Test
    @DisplayName("发消息失败：审计写入也异常时，仍然返回 60010 而不是 500")
    void sendFailureSurvivesAuditError() {
        when(sessionMapper.selectOne(any())).thenReturn(session(ChatSession.DEFAULT_TITLE, 0));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(aiClient.chat(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 服务暂时不可用"));
        when(aiLogMapper.insert(any(AiLog.class))).thenThrow(new RuntimeException("数据库连接失败"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> chatService.send(SESSION_ID, request("问点什么")));

        assertEquals(ErrorCode.CHAT_CALL_FAILED, e.getErrorCode(),
                "审计写失败不能把业务错误盖成 500");
        verify(messageMapper, never()).insert(any(ChatMessage.class));
        verify(sessionMapper, never()).updateById(any(ChatSession.class));
    }

    @Test
    @DisplayName("新建会话：今日已达上限 → 60011，且不入库")
    void createSessionRejectsWhenDailyLimitReached() {
        properties.setChatDailySessionLimit(3);
        when(sessionMapper.selectCount(any())).thenReturn(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> chatService.createSession());

        assertEquals(ErrorCode.CHAT_SESSION_LIMIT_EXCEEDED, e.getErrorCode());
        assertTrue(e.getMessage().contains("3"), "提示里要给出当前数量与上限：" + e.getMessage());
        verify(sessionMapper, never()).insert(any(ChatSession.class));
    }

    @Test
    @DisplayName("新建会话：未达上限时正常创建")
    void createSessionAllowedUnderLimit() {
        properties.setChatDailySessionLimit(3);
        when(sessionMapper.selectCount(any())).thenReturn(2L);
        when(sessionMapper.insert(any(ChatSession.class))).thenAnswer(invocation -> {
            ChatSession session = invocation.getArgument(0);
            session.setId(SESSION_ID);
            return 1;
        });
        when(sessionMapper.selectOne(any())).thenReturn(session(ChatSession.DEFAULT_TITLE, 0));

        ChatSessionVO vo = chatService.createSession();

        assertEquals(SESSION_ID, vo.getId());
        verify(sessionMapper).insert(any(ChatSession.class));
    }

    /* ---------------- 辅助方法 ---------------- */

    private ChatSession session(String title, int messageCount) {
        ChatSession session = new ChatSession();
        session.setId(SESSION_ID);
        session.setUserId(ME);
        session.setTitle(title);
        session.setMessageCount(messageCount);
        session.setLastMessageTime(LocalDateTime.now());
        session.setCreateTime(LocalDateTime.now());
        return session;
    }

    private ChatMessage message(Long id, ChatRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setSessionId(SESSION_ID);
        message.setUserId(ME);
        message.setRole(role);
        message.setContent(content);
        message.setTotalTokens(0);
        message.setCostMs(0L);
        message.setCreateTime(LocalDateTime.now());
        return message;
    }

    private SendChatMessageRequest request(String content) {
        SendChatMessageRequest request = new SendChatMessageRequest();
        request.setContent(content);
        return request;
    }

    private AiClient.ChatCompletion completion(String content, int prompt, int completionTokens,
                                               int total, long costMs) {
        return new AiClient.ChatCompletion(content, "deepseek-flash", prompt, completionTokens, total, costMs);
    }
}
