package com.notework.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.notework.ai.AiClient;
import com.notework.ai.AiResultParser;
import com.notework.common.enums.AiCapability;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Role;
import com.notework.common.enums.Visibility;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.config.AiProperties;
import com.notework.dto.request.AiConfirmRequest;
import com.notework.dto.response.AiGenerateVO;
import com.notework.dto.response.AiStatsVO;
import com.notework.entity.AiLog;
import com.notework.entity.Note;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.AiLogMapper;
import com.notework.mapper.NoteMapper;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 服务单元测试。
 * <p>
 * 重点验证：权限、每日配额、失败也留痕、确认保存（含覆盖旧结果）、撤销、公开开关、统计汇总。
 * 全程用 Mock 客户端，不联网、不花 token。
 */
@ExtendWith(MockitoExtension.class)
class AiServiceImplTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long NOTE_ID = 70L;

    @Mock
    private NoteMapper noteMapper;

    @Mock
    private AiLogMapper aiLogMapper;

    @Mock
    private AiClient aiClient;

    private AiProperties properties;
    private AiServiceImpl aiService;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Note.class);
        TableInfoHelper.initTableInfo(assistant, AiLog.class);
    }

    @BeforeEach
    void setUp() {
        properties = new AiProperties();
        properties.setModel("deepseek-flash");
        properties.setDailyQuota(20);
        properties.setMaxInputChars(4000);
        properties.setMaxOutputTokens(800);
        aiService = new AiServiceImpl(noteMapper, aiLogMapper, aiClient, new AiResultParser(new com.fasterxml.jackson.databind.ObjectMapper()), properties);
        UserContext.set(new LoginUser(AUTHOR_ID, "alice", Role.USER));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /* ---------------- 生成 ---------------- */

    @Test
    @DisplayName("生成成功：写一条 success=1 的日志，并返回剩余配额")
    void generateWritesLog() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(AUTHOR_ID));
        when(aiLogMapper.selectCount(any())).thenReturn(3L);
        when(aiClient.complete(any())).thenReturn(new AiClient.AiCompletion(
                "{\"summary\":\"讲的是坚持\",\"keywords\":[\"坚持\"]}", "deepseek-flash", 100, 50, 150, 800L));
        when(aiLogMapper.insert(any(AiLog.class))).thenAnswer(invocation -> {
            AiLog log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        });

        AiGenerateVO vo = aiService.generate(NOTE_ID, AiCapability.SUMMARY);

        assertEquals(900L, vo.getAiLogId());
        assertEquals("SUMMARY", vo.getCapability());
        assertEquals(150, vo.getTotalTokens());
        assertEquals(800L, vo.getCostMs());
        assertEquals(16, vo.getRemainingQuota(), "配额 20，调用前已用 3，本次生成成功后剩 16");
        assertNotNull(vo.getText());
        assertTrue(vo.getText().contains("摘要"), vo.getText());
        assertNotNull(vo.getDisclaimer(), "必须带\"仅供参考\"的提示语");

        ArgumentCaptor<AiLog> captor = ArgumentCaptor.forClass(AiLog.class);
        verify(aiLogMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getSuccess());
        assertEquals(0, captor.getValue().getConfirmed(), "生成不等于确认，confirmed 应为 0");
        assertEquals(AiCapability.SUMMARY, captor.getValue().getCapability());
    }

    @Test
    @DisplayName("生成：他人笔记 → 20002")
    void generateRejectsOthersNote() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(OTHER_ID));

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiService.generate(NOTE_ID, AiCapability.SUMMARY));

        assertEquals(ErrorCode.NOTE_PERMISSION_DENIED, e.getErrorCode());
        verify(aiLogMapper, never()).insert(any(AiLog.class));
    }

    @Test
    @DisplayName("生成：超出每日配额 → 60002，且不调模型")
    void generateRejectsWhenQuotaExceeded() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(AUTHOR_ID));
        when(aiLogMapper.selectCount(any())).thenReturn(20L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiService.generate(NOTE_ID, AiCapability.SUMMARY));

        assertEquals(ErrorCode.AI_QUOTA_EXCEEDED, e.getErrorCode());
        verify(aiClient, never()).complete(any());
        verify(aiLogMapper, never()).insert(any(AiLog.class));
    }

    @Test
    @DisplayName("生成：模型调用失败 → 原样抛出，并且仍然写一条 success=0 的日志")
    void generateRecordsFailure() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(AUTHOR_ID));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);
        when(aiClient.complete(any())).thenThrow(new BusinessException(ErrorCode.AI_CALL_FAILED, "AI 服务暂时不可用"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiService.generate(NOTE_ID, AiCapability.SUMMARY));

        assertEquals(ErrorCode.AI_CALL_FAILED, e.getErrorCode());
        ArgumentCaptor<AiLog> captor = ArgumentCaptor.forClass(AiLog.class);
        verify(aiLogMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getSuccess());
        assertTrue(captor.getValue().getErrorMsg().contains("不可用"), captor.getValue().getErrorMsg());
    }

    @Test
    @DisplayName("生成：模型返回不合规结构 → 60005，日志记为失败，但 token 成本仍要记下来")
    void generateRecordsInvalidResponse() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(AUTHOR_ID));
        when(aiLogMapper.selectCount(any())).thenReturn(0L);
        when(aiClient.complete(any())).thenReturn(new AiClient.AiCompletion(
                "这不是 JSON", "deepseek-flash", 10, 5, 15, 100L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiService.generate(NOTE_ID, AiCapability.SCORE));

        assertEquals(ErrorCode.AI_RESPONSE_INVALID, e.getErrorCode());
        ArgumentCaptor<AiLog> captor = ArgumentCaptor.forClass(AiLog.class);
        verify(aiLogMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getSuccess());
        assertEquals(15, captor.getValue().getTotalTokens(),
                "解析失败时模型其实已经消耗了 token，日志必须记下来，否则成本统计会偏低");
        assertEquals("deepseek-flash", captor.getValue().getModel());
    }

    /* ---------------- 确认保存 ---------------- */

    @Test
    @DisplayName("确认保存：把结果写进笔记，并标记该条日志为已确认")
    void confirmWritesToNote() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(AUTHOR_ID));
        when(aiLogMapper.selectById(900L)).thenReturn(successLog(900L, NOTE_ID));
        when(aiLogMapper.update(any(AiLog.class), any())).thenReturn(1);
        when(aiLogMapper.updateById(any(AiLog.class))).thenReturn(1);

        AiConfirmRequest request = new AiConfirmRequest();
        request.setAiLogId(900L);
        request.setAiPublic(true);
        aiService.confirm(NOTE_ID, request);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).updateById(captor.capture());
        assertEquals(AiCapability.SUMMARY, captor.getValue().getAiType());
        assertEquals("摘要：讲的是坚持", captor.getValue().getAiResult());
        assertEquals(1, captor.getValue().getAiPublic());
        assertNotNull(captor.getValue().getAiConfirmedAt());

        ArgumentCaptor<AiLog> markCaptor = ArgumentCaptor.forClass(AiLog.class);
        verify(aiLogMapper).updateById(markCaptor.capture());
        assertEquals(1, markCaptor.getValue().getConfirmed());
    }

    @Test
    @DisplayName("确认保存：生成记录不存在 → 60003")
    void confirmRejectsMissingLog() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(AUTHOR_ID));
        when(aiLogMapper.selectById(900L)).thenReturn(null);

        AiConfirmRequest request = new AiConfirmRequest();
        request.setAiLogId(900L);

        BusinessException e = assertThrows(BusinessException.class, () -> aiService.confirm(NOTE_ID, request));
        assertEquals(ErrorCode.AI_RESULT_NOT_FOUND, e.getErrorCode());
        verify(noteMapper, never()).updateById(any(Note.class));
    }

    @Test
    @DisplayName("确认保存：记录属于别的笔记 → 60003（不能拿 A 笔记的结果存到 B 笔记）")
    void confirmRejectsLogFromAnotherNote() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(AUTHOR_ID));
        when(aiLogMapper.selectById(900L)).thenReturn(successLog(900L, 999L));

        AiConfirmRequest request = new AiConfirmRequest();
        request.setAiLogId(900L);

        BusinessException e = assertThrows(BusinessException.class, () -> aiService.confirm(NOTE_ID, request));
        assertEquals(ErrorCode.AI_RESULT_NOT_FOUND, e.getErrorCode());
    }

    /* ---------------- 撤销 / 公开开关 ---------------- */

    @Test
    @DisplayName("撤销：把笔记上的 AI 字段置空")
    void cancelClearsFields() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(AUTHOR_ID));
        when(noteMapper.update(isNull(), any())).thenReturn(1);
        when(aiLogMapper.update(any(AiLog.class), any())).thenReturn(1);

        aiService.cancel(NOTE_ID);

        verify(noteMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("公开开关：笔记还没有 AI 结果 → 60006")
    void visibilityRequiresSavedResult() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(AUTHOR_ID));

        BusinessException e = assertThrows(BusinessException.class,
                () -> aiService.updateVisibility(NOTE_ID, true));

        assertEquals(ErrorCode.AI_RESULT_ABSENT, e.getErrorCode());
    }

    @Test
    @DisplayName("公开开关：已有结果时更新 ai_public")
    void visibilityUpdatesFlag() {
        Note note = note(AUTHOR_ID);
        note.setAiType(AiCapability.SCORE);
        note.setAiResult("评分：8/10");
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note);
        when(noteMapper.updateById(any(Note.class))).thenReturn(1);

        aiService.updateVisibility(NOTE_ID, true);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getAiPublic());
    }

    /* ---------------- 统计 ---------------- */

    @Test
    @DisplayName("统计：没有调用记录时返回全 0")
    void statsEmpty() {
        when(aiLogMapper.selectList(any())).thenReturn(List.of());

        AiStatsVO vo = aiService.stats();

        assertEquals(0L, vo.getTotalCalls());
        assertEquals(0L, vo.getSuccessCalls());
        assertEquals(0L, vo.getTotalTokens());
        assertEquals(0L, vo.getConfirmedCalls());
    }

    @Test
    @DisplayName("统计：汇总成功率、token、耗时与按能力分组")
    void statsAggregates() {
        AiLog ok = successLog(1L, NOTE_ID);
        ok.setCapability(AiCapability.SUMMARY);
        ok.setTotalTokens(100);
        ok.setCostMs(500L);
        ok.setConfirmed(1);

        AiLog fail = new AiLog();
        fail.setCapability(AiCapability.SCORE);
        fail.setSuccess(0);
        fail.setTotalTokens(0);
        fail.setCostMs(100L);
        fail.setConfirmed(0);

        when(aiLogMapper.selectList(any())).thenReturn(List.of(ok, fail));

        AiStatsVO vo = aiService.stats();

        assertEquals(2L, vo.getTotalCalls());
        assertEquals(1L, vo.getSuccessCalls());
        assertEquals(1L, vo.getFailedCalls());
        assertEquals(50.0, vo.getSuccessRate());
        assertEquals(100L, vo.getTotalTokens());
        assertEquals(300L, vo.getAvgCostMs());
        assertEquals(500L, vo.getMaxCostMs());
        assertEquals(1L, vo.getConfirmedCalls());
        assertEquals(2, vo.getByCapability().size());
        assertEquals(7, vo.getRecentDays().size(), "最近 7 天趋势固定返回 7 天");
    }

    /* ---------------- 辅助方法 ---------------- */

    private Note note(Long authorId) {
        Note note = new Note();
        note.setId(NOTE_ID);
        note.setUserId(authorId);
        note.setBookName("《活着》");
        note.setContent("这是一段读书笔记正文。");
        note.setVisibility(Visibility.PUBLIC);
        note.setAuditStatus(AuditStatus.APPROVED);
        note.setAiPublic(0);
        return note;
    }

    private AiLog successLog(Long id, Long noteId) {
        AiLog log = new AiLog();
        log.setId(id);
        log.setNoteId(noteId);
        log.setUserId(AUTHOR_ID);
        log.setCapability(AiCapability.SUMMARY);
        log.setSuccess(1);
        log.setConfirmed(0);
        log.setTotalTokens(100);
        log.setCostMs(500L);
        log.setResult("摘要：讲的是坚持");
        log.setCreateTime(LocalDateTime.now());
        return log;
    }
}
