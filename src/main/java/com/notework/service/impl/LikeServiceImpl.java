package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Visibility;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.common.util.BizAssert;
import com.notework.dto.response.NoteRankVO;
import com.notework.entity.Note;
import com.notework.entity.NoteLike;
import com.notework.interceptor.UserContext;
import com.notework.mapper.NoteLikeMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.row.NoteWithLikeRow;
import com.notework.service.LikeService;
import com.notework.service.support.NoteViewAssembler;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 点赞业务实现。
 * <p>
 * 规则：
 * <ul>
 *     <li>只有公开笔记能点赞（私有笔记对别人本来就不可见）</li>
 *     <li>点赞与取消都幂等：重复点赞、重复取消都不会报错</li>
 *     <li>点赞数一律实时聚合，不在笔记表里冗余计数字段</li>
 * </ul>
 */
@Service
public class LikeServiceImpl implements LikeService {

    private final NoteLikeMapper noteLikeMapper;
    private final NoteMapper noteMapper;
    private final NoteViewAssembler noteViewAssembler;

    public LikeServiceImpl(NoteLikeMapper noteLikeMapper, NoteMapper noteMapper,
                           NoteViewAssembler noteViewAssembler) {
        this.noteLikeMapper = noteLikeMapper;
        this.noteMapper = noteMapper;
        this.noteViewAssembler = noteViewAssembler;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long like(Long noteId) {
        Long userId = UserContext.requireUserId();

        Note note = noteMapper.selectById(noteId);
        BizAssert.notNull(note, ErrorCode.NOTE_NOT_FOUND);
        if (Visibility.PUBLIC != note.getVisibility()) {
            throw new BusinessException(ErrorCode.NOTE_NOT_PUBLIC);
        }
        // 扩展部分：公开但还没通过审核（待审核/已驳回/已下架）的笔记不能被点赞
        if (AuditStatus.APPROVED != note.getAuditStatus()) {
            throw new BusinessException(ErrorCode.NOTE_STATE_INVALID, "该笔记尚未通过审核，暂时不能点赞");
        }

        if (!hasLiked(noteId, userId)) {
            NoteLike noteLike = new NoteLike();
            noteLike.setNoteId(noteId);
            noteLike.setUserId(userId);
            try {
                noteLikeMapper.insert(noteLike);
            } catch (DuplicateKeyException e) {
                // 并发下同一瞬间点了两次，唯一索引挡住了，按幂等处理即可
            }
        }
        return countByNoteId(noteId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long unlike(Long noteId) {
        Long userId = UserContext.requireUserId();

        // 没点过赞时删除 0 行，同样算成功
        noteLikeMapper.delete(new LambdaQueryWrapper<NoteLike>()
                .eq(NoteLike::getNoteId, noteId)
                .eq(NoteLike::getUserId, userId));

        return countByNoteId(noteId);
    }

    @Override
    public PageResult<NoteRankVO> pageMyLikedNotes(PageQuery pageQuery) {
        Long userId = UserContext.requireUserId();
        int page = pageQuery.getPage();
        int size = pageQuery.getSize();

        long total = noteLikeMapper.countLikedNotes(userId);
        if (total == 0) {
            return PageResult.empty(page, size);
        }

        List<NoteWithLikeRow> rows = noteLikeMapper.selectLikedNoteList(userId, (page - 1) * size, size);
        return PageResult.of(noteViewAssembler.assembleRankList(rows), total, page, size);
    }

    @Override
    public long countByNoteId(Long noteId) {
        Long count = noteLikeMapper.selectCount(
                new LambdaQueryWrapper<NoteLike>().eq(NoteLike::getNoteId, noteId));
        return count == null ? 0L : count;
    }

    @Override
    public boolean hasLiked(Long noteId, Long userId) {
        if (noteId == null || userId == null) {
            return false;
        }
        Long count = noteLikeMapper.selectCount(new LambdaQueryWrapper<NoteLike>()
                .eq(NoteLike::getNoteId, noteId)
                .eq(NoteLike::getUserId, userId));
        return count != null && count > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLikesByNoteId(Long noteId) {
        noteLikeMapper.delete(new LambdaQueryWrapper<NoteLike>().eq(NoteLike::getNoteId, noteId));
    }
}
