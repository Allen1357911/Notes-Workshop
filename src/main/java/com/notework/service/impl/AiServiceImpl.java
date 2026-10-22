package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.notework.ai.AiClient;
import com.notework.ai.AiResultParser;
import com.notework.ai.PromptTemplates;
import com.notework.common.enums.AiCapability;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.common.util.BizAssert;
import com.notework.config.AiProperties;
import com.notework.dto.request.AiConfirmRequest;
import com.notework.dto.response.AiGenerateVO;
import com.notework.dto.response.AiStatsVO;
import com.notework.entity.AiLog;
import com.notework.entity.Note;
import com.notework.interceptor.UserContext;
import com.notework.mapper.AiLogMapper;
import com.notework.mapper.NoteMapper;
import com.notework.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 辅助服务实现。
 * <p>
 * 几个关键取舍：
 * <ul>
 *     <li><b>生成不写笔记</b>：只写 t_ai_log（confirmed=0），确认时才写 t_note —— 把"必须手动确认"落在接口层；</li>
 *     <li><b>失败也留痕</b>：调用失败/解析失败同样写一条 success=0 的日志，便于排查与统计；</li>
 *     <li><b>配额按人按天</b>：用 t_ai_log 里当天该用户的记录数控制，不需要额外计数表；</li>
 *     <li><b>不参与笔记事务</b>：AI 调用是外部 IO，失败绝不能影响笔记本身的功能。</li>
 * </ul>
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("MM-dd");

    private final NoteMapper noteMapper;
    private final AiLogMapper aiLogMapper;
    private final AiClient aiClient;
    private final AiResultParser resultParser;
    private final AiProperties properties;

    public AiServiceImpl(NoteMapper noteMapper, AiLogMapper aiLogMapper, AiClient aiClient,
                         AiResultParser resultParser, AiProperties properties) {
        this.noteMapper = noteMapper;
        this.aiLogMapper = aiLogMapper;
        this.aiClient = aiClient;
        this.resultParser = resultParser;
        this.properties = properties;
    }

    @Override
    public AiGenerateVO generate(Long noteId, AiCapability capability) {
        Long me = UserContext.requireUserId();
        Note note = loadNote(noteId);
        checkOwnerOrAdmin(note, me);

        long usedToday = countTodayCalls(me);
        if (usedToday >= properties.getDailyQuota()) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED,
                    "今日 AI 调用次数已用完（每天 " + properties.getDailyQuota() + " 次），明天再来吧");
        }

        String content = truncate(note.getContent(), properties.getMaxInputChars());
        AiClient.AiRequest request = new AiClient.AiRequest(capability, PromptTemplates.SYSTEM,
                PromptTemplates.user(capability, note.getBookName(), content),
                properties.getMaxOutputTokens());

        AiLog record = new AiLog();
        record.setNoteId(noteId);
        record.setUserId(me);
        record.setCapability(capability);
        record.setInputChars(content.length());
        record.setConfirmed(0);

        long start = System.nanoTime();
        try {
            AiClient.AiCompletion completion = aiClient.complete(request);
            // 注意：模型调用**已经花了 token**，所以 token 与耗时必须在解析之前就记下来。
            // 否则一旦解析失败（AI 返回结构不合规），这条日志里的 token 全是 0，等于丢掉了这笔成本。
            record.setModel(completion.model());
            record.setPromptTokens(completion.promptTokens());
            record.setCompletionTokens(completion.completionTokens());
            record.setTotalTokens(completion.totalTokens());
            record.setCostMs(completion.costMs());

            String text = resultParser.parse(capability, completion.content());
            record.setSuccess(1);
            record.setResult(text);
            aiLogMapper.insert(record);
            log.info("AI 生成成功：用户[{}] 笔记[{}] 能力[{}] tokens={} 耗时={}ms",
                    me, noteId, capability, completion.totalTokens(), completion.costMs());
            return toVO(record, capability, usedToday + 1);
        } catch (BusinessException e) {
            // 失败也要留痕：记录错误信息，方便管理员在统计页看到失败率
            record.setSuccess(0);
            record.setErrorMsg(truncate(e.getMessage(), 500));
            record.setCostMs((System.nanoTime() - start) / 1_000_000);
            aiLogMapper.insert(record);
            log.warn("AI 生成失败：用户[{}] 笔记[{}] 能力[{}] 原因[{}]", me, noteId, capability, e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long noteId, AiConfirmRequest request) {
        Long me = UserContext.requireUserId();
        Note note = loadNote(noteId);
        checkOwnerOrAdmin(note, me);

        AiLog record = aiLogMapper.selectById(request.getAiLogId());
        if (record == null || !noteId.equals(record.getNoteId())
                || record.getSuccess() == null || record.getSuccess() != 1) {
            throw new BusinessException(ErrorCode.AI_RESULT_NOT_FOUND);
        }

        Note update = new Note();
        update.setId(noteId);
        update.setAiType(record.getCapability());
        update.setAiResult(record.getResult());
        update.setAiConfirmedAt(LocalDateTime.now());
        update.setAiPublic(Boolean.TRUE.equals(request.getAiPublic()) ? 1 : 0);
        noteMapper.updateById(update);

        // 同一篇笔记只保留"当前生效"的那一条为已确认状态
        AiLog reset = new AiLog();
        reset.setConfirmed(0);
        aiLogMapper.update(reset, new LambdaUpdateWrapper<AiLog>()
                .eq(AiLog::getNoteId, noteId)
                .eq(AiLog::getConfirmed, 1));

        AiLog mark = new AiLog();
        mark.setId(record.getId());
        mark.setConfirmed(1);
        aiLogMapper.updateById(mark);

        log.info("用户[{}]确认保存 AI 结果到笔记[{}]：{}，公开={}", me, noteId,
                record.getCapability(), Boolean.TRUE.equals(request.getAiPublic()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long noteId) {
        Long me = UserContext.requireUserId();
        Note note = loadNote(noteId);
        checkOwnerOrAdmin(note, me);

        // 用 UpdateWrapper 才能把字段写成 NULL（updateById 会跳过 null 字段）
        noteMapper.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .set(Note::getAiType, null)
                .set(Note::getAiResult, null)
                .set(Note::getAiConfirmedAt, null)
                .set(Note::getAiPublic, 0));

        AiLog reset = new AiLog();
        reset.setConfirmed(0);
        aiLogMapper.update(reset, new LambdaUpdateWrapper<AiLog>()
                .eq(AiLog::getNoteId, noteId)
                .eq(AiLog::getConfirmed, 1));

        log.info("用户[{}]撤销了笔记[{}]的 AI 结果", me, noteId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateVisibility(Long noteId, boolean aiPublic) {
        Long me = UserContext.requireUserId();
        Note note = loadNote(noteId);
        checkOwnerOrAdmin(note, me);
        if (note.getAiType() == null) {
            throw new BusinessException(ErrorCode.AI_RESULT_ABSENT);
        }

        Note update = new Note();
        update.setId(noteId);
        update.setAiPublic(aiPublic ? 1 : 0);
        noteMapper.updateById(update);
        log.info("用户[{}]把笔记[{}]的 AI 结果设为{}", me, noteId, aiPublic ? "公开" : "仅自己可见");
    }

    @Override
    public AiStatsVO stats() {
        // 只取统计需要的列：一次查询后在内存聚合。
        // 以本项目的数据量（每用户每天 ≤20 次）这样最省事；数据量大时应改成 GROUP BY 的 SQL。
        List<AiLog> logs = aiLogMapper.selectList(new LambdaQueryWrapper<AiLog>()
                .select(AiLog::getCapability, AiLog::getSuccess, AiLog::getConfirmed,
                        AiLog::getTotalTokens, AiLog::getCostMs, AiLog::getCreateTime));

        AiStatsVO vo = new AiStatsVO();
        vo.setModel(properties.getModel());
        vo.setMock(properties.isMock());
        vo.setTotalCalls(logs.size());
        if (logs.isEmpty()) {
            return vo;
        }

        long success = 0;
        long confirmed = 0;
        long tokens = 0;
        long costSum = 0;
        long costMax = 0;

        Map<AiCapability, long[]> byCapability = new LinkedHashMap<>();   // [calls, tokens, costSum]
        Map<String, long[]> byDay = new LinkedHashMap<>();                // [calls, tokens]
        LocalDate today = LocalDate.now();

        for (AiLog item : logs) {
            boolean ok = item.getSuccess() != null && item.getSuccess() == 1;
            if (ok) {
                success++;
            }
            if (item.getConfirmed() != null && item.getConfirmed() == 1) {
                confirmed++;
            }
            long itemTokens = item.getTotalTokens() == null ? 0 : item.getTotalTokens();
            long itemCost = item.getCostMs() == null ? 0 : item.getCostMs();
            tokens += itemTokens;
            costSum += itemCost;
            costMax = Math.max(costMax, itemCost);

            if (item.getCapability() != null) {
                long[] cap = byCapability.computeIfAbsent(item.getCapability(), key -> new long[3]);
                cap[0]++;
                cap[1] += itemTokens;
                cap[2] += itemCost;
            }
            if (item.getCreateTime() != null) {
                LocalDate day = item.getCreateTime().toLocalDate();
                if (!day.isBefore(today.minusDays(6))) {
                    long[] dayStat = byDay.computeIfAbsent(day.format(DAY_FORMAT), key -> new long[2]);
                    dayStat[0]++;
                    dayStat[1] += itemTokens;
                }
            }
        }

        vo.setSuccessCalls(success);
        vo.setFailedCalls(logs.size() - success);
        vo.setSuccessRate(Math.round(success * 1000.0 / logs.size()) / 10.0);
        vo.setTotalTokens(tokens);
        vo.setAvgCostMs(costSum / logs.size());
        vo.setMaxCostMs(costMax);
        vo.setConfirmedCalls(confirmed);

        List<AiStatsVO.CapabilityStat> capabilityStats = new ArrayList<>();
        byCapability.forEach((capability, values) -> capabilityStats.add(new AiStatsVO.CapabilityStat(
                capability.name(), capability.getLabel(), values[0], values[1],
                values[0] == 0 ? 0 : values[2] / values[0])));
        vo.setByCapability(capabilityStats);

        List<AiStatsVO.DayStat> dayStats = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String day = today.minusDays(i).format(DAY_FORMAT);
            long[] values = byDay.getOrDefault(day, new long[2]);
            dayStats.add(new AiStatsVO.DayStat(day, values[0], values[1]));
        }
        vo.setRecentDays(dayStats);
        return vo;
    }

    /* ---------------- 内部方法 ---------------- */

    private Note loadNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        BizAssert.notNull(note, ErrorCode.NOTE_NOT_FOUND);
        return note;
    }

    /** 只有作者本人或管理员能对这篇笔记用 AI（私有笔记也可以用，AI 是学习辅助） */
    private void checkOwnerOrAdmin(Note note, Long userId) {
        if (!userId.equals(note.getUserId()) && !UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.NOTE_PERMISSION_DENIED);
        }
    }

    /** 今日已用掉多少"笔记 AI"配额；客服对话是独立配额，不占用这里的次数 */
    private long countTodayCalls(Long userId) {
        Long count = aiLogMapper.selectCount(new LambdaQueryWrapper<AiLog>()
                .eq(AiLog::getUserId, userId)
                .ne(AiLog::getCapability, AiCapability.CHAT)
                .ge(AiLog::getCreateTime, LocalDate.now().atStartOfDay()));
        return count == null ? 0 : count;
    }

    private AiGenerateVO toVO(AiLog record, AiCapability capability, long usedToday) {
        AiGenerateVO vo = new AiGenerateVO();
        vo.setAiLogId(record.getId());
        vo.setCapability(capability.name());
        vo.setCapabilityLabel(capability.getLabel());
        vo.setText(record.getResult());
        vo.setModel(record.getModel());
        vo.setPromptTokens(record.getPromptTokens() == null ? 0 : record.getPromptTokens());
        vo.setCompletionTokens(record.getCompletionTokens() == null ? 0 : record.getCompletionTokens());
        vo.setTotalTokens(record.getTotalTokens() == null ? 0 : record.getTotalTokens());
        vo.setCostMs(record.getCostMs() == null ? 0 : record.getCostMs());
        vo.setRemainingQuota((int) Math.max(0, properties.getDailyQuota() - usedToday));
        return vo;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
