package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Visibility;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.common.util.BizAssert;
import com.notework.dto.request.AuditNoteRequest;
import com.notework.dto.response.AdminNoteVO;
import com.notework.entity.Note;
import com.notework.entity.NoteImage;
import com.notework.entity.User;
import com.notework.interceptor.UserContext;
import com.notework.mapper.NoteImageMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
import com.notework.service.AdminNoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员后台：内容管理与审核实现。
 * <p>
 * 状态机（只允许这些流转）：
 * <ul>
 *     <li>PENDING --审核通过--> APPROVED</li>
 *     <li>PENDING --审核驳回--> REJECTED（记录原因，作者可修改后重新提交）</li>
 *     <li>APPROVED --下架--> TAKEN_DOWN</li>
 *     <li>TAKEN_DOWN --恢复--> APPROVED</li>
 * </ul>
 */
@Service
public class AdminNoteServiceImpl implements AdminNoteService {

    private static final Logger log = LoggerFactory.getLogger(AdminNoteServiceImpl.class);

    private static final String DEFAULT_REJECT_REMARK = "内容不符合社区规范，请修改后重新提交";
    private static final String DEFAULT_OFFLINE_REMARK = "经管理员核查，内容不符合社区规范，已下架";

    private final NoteMapper noteMapper;
    private final NoteImageMapper noteImageMapper;
    private final UserMapper userMapper;

    public AdminNoteServiceImpl(NoteMapper noteMapper, NoteImageMapper noteImageMapper, UserMapper userMapper) {
        this.noteMapper = noteMapper;
        this.noteImageMapper = noteImageMapper;
        this.userMapper = userMapper;
    }

    @Override
    public PageResult<AdminNoteVO> pageNotes(Visibility visibility, AuditStatus auditStatus, PageQuery pageQuery) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        if (visibility != null) {
            wrapper.eq(Note::getVisibility, visibility);
        }
        if (auditStatus != null) {
            wrapper.eq(Note::getAuditStatus, auditStatus);
        }
        wrapper.orderByDesc(Note::getCreateTime).orderByDesc(Note::getId);
        return selectAndAssemble(wrapper, pageQuery);
    }

    @Override
    public PageResult<AdminNoteVO> pagePendingNotes(PageQuery pageQuery) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<Note>()
                .eq(Note::getVisibility, Visibility.PUBLIC)
                .eq(Note::getAuditStatus, AuditStatus.PENDING)
                .orderByAsc(Note::getCreateTime)   // 先提交的先审
                .orderByAsc(Note::getId);
        return selectAndAssemble(wrapper, pageQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminNoteVO audit(Long noteId, AuditNoteRequest request) {
        Note note = loadNote(noteId);
        if (Visibility.PUBLIC != note.getVisibility()) {
            throw new BusinessException(ErrorCode.NOTE_STATE_INVALID, "私有笔记不参与审核");
        }
        if (AuditStatus.PENDING != note.getAuditStatus()) {
            throw new BusinessException(ErrorCode.NOTE_NOT_PENDING);
        }

        boolean approved = Boolean.TRUE.equals(request.getApproved());
        String remark = approved ? "" : defaultIfBlank(request.getRemark(), DEFAULT_REJECT_REMARK);

        // 条件更新：只有"数据库里当前确实是待审核"才改得动。
        // 并发下两个管理员同时点通过时只有一个能成功，另一个拿到 20005，
        // 而不是两个都通过前置检查、都写库（check-then-act 竞态）。
        updateAudit(noteId, AuditStatus.PENDING, approved ? AuditStatus.APPROVED : AuditStatus.REJECTED,
                remark, ErrorCode.NOTE_NOT_PENDING, ErrorCode.NOTE_NOT_PENDING.getMessage());
        log.info("管理员[{}]审核笔记[{}]：{}，意见：{}", UserContext.getUserId(), noteId,
                approved ? "通过" : "驳回", StringUtils.hasText(remark) ? remark : "无");

        return toAdminNoteVO(reloadNote(noteId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminNoteVO offline(Long noteId, String remark) {
        Note note = loadNote(noteId);
        if (Visibility.PUBLIC != note.getVisibility() || AuditStatus.APPROVED != note.getAuditStatus()) {
            throw new BusinessException(ErrorCode.NOTE_STATE_INVALID, "只有已通过审核的公开笔记可以下架");
        }

        String finalRemark = defaultIfBlank(remark, DEFAULT_OFFLINE_REMARK);
        // 条件更新里再带上 visibility：并发期间作者若把笔记改成了私有，下架就不该生效
        updateAudit(noteId, Visibility.PUBLIC, AuditStatus.APPROVED, AuditStatus.TAKEN_DOWN, finalRemark,
                ErrorCode.NOTE_STATE_INVALID, "只有已通过审核的公开笔记可以下架");
        log.info("管理员[{}]下架笔记[{}]，说明：{}", UserContext.getUserId(), noteId, finalRemark);

        return toAdminNoteVO(reloadNote(noteId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminNoteVO restore(Long noteId) {
        Note note = loadNote(noteId);
        if (AuditStatus.TAKEN_DOWN != note.getAuditStatus()) {
            throw new BusinessException(ErrorCode.NOTE_STATE_INVALID, "该笔记当前不是已下架状态");
        }

        updateAudit(noteId, AuditStatus.TAKEN_DOWN, AuditStatus.APPROVED, "",
                ErrorCode.NOTE_STATE_INVALID, "该笔记当前不是已下架状态");
        log.info("管理员[{}]恢复笔记[{}]上架", UserContext.getUserId(), noteId);

        return toAdminNoteVO(reloadNote(noteId));
    }

    /* ---------------- 内部方法 ---------------- */

    /**
     * 原子地把审核状态从 expected 改成 target：<code>UPDATE ... WHERE id = ? AND audit_status = ?</code>。
     * <p>
     * 拿到 0 行说明状态已经被别人改掉了（并发点审核/下架/恢复），此时抛冲突错误，
     * 而不是硬写库 —— 这是"检查后动作"改成"条件更新"的关键，保证状态机在并发下也只有一个赢家。
     * <p>
     * 下架还会额外要求 visibility 仍然是 PUBLIC（作者可能在并发期间把它改成了私有）。
     */
    private void updateAudit(Long noteId, AuditStatus expected, AuditStatus target, String remark,
                             ErrorCode conflictError, String conflictMessage) {
        updateAudit(noteId, null, expected, target, remark, conflictError, conflictMessage);
    }

    private void updateAudit(Long noteId, Visibility expectedVisibility, AuditStatus expected, AuditStatus target,
                             String remark, ErrorCode conflictError, String conflictMessage) {
        Note update = new Note();
        update.setAuditStatus(target);
        update.setAuditRemark(remark == null ? "" : remark);
        update.setAuditTime(LocalDateTime.now());

        LambdaUpdateWrapper<Note> wrapper = new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, noteId)
                .eq(Note::getAuditStatus, expected);
        if (expectedVisibility != null) {
            wrapper.eq(Note::getVisibility, expectedVisibility);
        }

        int affected = noteMapper.update(update, wrapper);
        if (affected == 0) {
            throw new BusinessException(conflictError, conflictMessage);
        }
    }

    private Note loadNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        BizAssert.notNull(note, ErrorCode.NOTE_NOT_FOUND);
        return note;
    }

    /**
     * 审核动作完成后回查最新笔记再组装返回。
     * 用断言兜一下：极端情况下（例如状态刚被别的请求改掉、笔记被删）不能返回 NPE 变成 500。
     */
    private Note reloadNote(Long noteId) {
        Note fresh = noteMapper.selectById(noteId);
        BizAssert.notNull(fresh, ErrorCode.NOTE_NOT_FOUND);
        return fresh;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private PageResult<AdminNoteVO> selectAndAssemble(LambdaQueryWrapper<Note> wrapper, PageQuery pageQuery) {
        Page<Note> page = noteMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery.getSize()), wrapper);
        List<Note> records = page.getRecords();
        int current = (int) page.getCurrent();
        int size = (int) page.getSize();
        if (records.isEmpty()) {
            return PageResult.empty(current, size);
        }

        // 作者与配图都一次性批量查，避免 N+1
        List<Long> authorIds = records.stream().map(Note::getUserId).distinct().toList();
        Map<Long, User> authors = userMapper.selectByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, List<String>> images = loadImagesGrouped(records.stream().map(Note::getId).toList());

        List<AdminNoteVO> list = records.stream()
                .map(note -> AdminNoteVO.of(note, authors.get(note.getUserId()),
                        images.getOrDefault(note.getId(), List.of())))
                .toList();

        return PageResult.of(list, page.getTotal(), current, size);
    }

    private AdminNoteVO toAdminNoteVO(Note note) {
        User author = userMapper.selectById(note.getUserId());
        List<String> images = noteImageMapper.selectList(new LambdaQueryWrapper<NoteImage>()
                        .eq(NoteImage::getNoteId, note.getId())
                        .orderByAsc(NoteImage::getSortOrder)
                        .orderByAsc(NoteImage::getId))
                .stream().map(NoteImage::getUrl).toList();
        return AdminNoteVO.of(note, author, images);
    }

    private Map<Long, List<String>> loadImagesGrouped(List<Long> noteIds) {
        if (noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }
        List<NoteImage> images = noteImageMapper.selectList(new LambdaQueryWrapper<NoteImage>()
                .in(NoteImage::getNoteId, noteIds)
                .orderByAsc(NoteImage::getSortOrder)
                .orderByAsc(NoteImage::getId));

        Map<Long, List<String>> grouped = new HashMap<>();
        for (NoteImage image : images) {
            grouped.computeIfAbsent(image.getNoteId(), key -> new ArrayList<>()).add(image.getUrl());
        }
        return grouped;
    }
}
