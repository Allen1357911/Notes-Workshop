package com.notework.service.impl;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Role;
import com.notework.common.enums.Visibility;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.dto.response.NoteRankVO;
import com.notework.entity.Note;
import com.notework.entity.NoteLike;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.NoteLikeMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.row.NoteWithLikeRow;
import com.notework.service.support.NoteViewAssembler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 点赞业务层单元测试。
 * <p>
 * 重点验证：只能赞公开笔记、重复点赞/取消幂等、点赞数实时聚合、我赞过的分页。
 */
@ExtendWith(MockitoExtension.class)
class LikeServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long NOTE_ID = 100L;

    @Mock
    private NoteLikeMapper noteLikeMapper;

    @Mock
    private NoteMapper noteMapper;

    @Mock
    private NoteViewAssembler noteViewAssembler;

    private LikeServiceImpl likeService;

    @BeforeEach
    void setUp() {
        likeService = new LikeServiceImpl(noteLikeMapper, noteMapper, noteViewAssembler);
        UserContext.set(new LoginUser(USER_ID, "zhangsan", Role.USER));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /* ---------------- 点赞 ---------------- */

    @Test
    @DisplayName("点赞公开笔记：写入点赞记录并返回最新点赞数")
    void likePublicNoteInsertsRecord() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(Visibility.PUBLIC));
        when(noteLikeMapper.selectCount(any())).thenReturn(0L).thenReturn(1L);
        when(noteLikeMapper.insert(any(NoteLike.class))).thenReturn(1);

        long count = likeService.like(NOTE_ID);

        ArgumentCaptor<NoteLike> captor = ArgumentCaptor.forClass(NoteLike.class);
        verify(noteLikeMapper).insert(captor.capture());
        assertEquals(NOTE_ID, captor.getValue().getNoteId());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("重复点赞：幂等，不再写第二条记录")
    void likeTwiceIsIdempotent() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(Visibility.PUBLIC));
        when(noteLikeMapper.selectCount(any())).thenReturn(1L);

        long count = likeService.like(NOTE_ID);

        verify(noteLikeMapper, never()).insert(any(NoteLike.class));
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("点赞私有笔记：拒绝")
    void likePrivateNoteRejected() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(Visibility.PRIVATE));

        BusinessException e = assertThrows(BusinessException.class, () -> likeService.like(NOTE_ID));

        assertEquals(ErrorCode.NOTE_NOT_PUBLIC, e.getErrorCode());
        verify(noteLikeMapper, never()).insert(any(NoteLike.class));
    }

    @Test
    @DisplayName("点赞不存在的笔记：抛 20001")
    void likeMissingNoteRejected() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> likeService.like(NOTE_ID));

        assertEquals(ErrorCode.NOTE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("未登录点赞：抛 401")
    void likeRequiresLogin() {
        UserContext.clear();

        BusinessException e = assertThrows(BusinessException.class, () -> likeService.like(NOTE_ID));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    /* ---------------- 取消点赞 ---------------- */

    @Test
    @DisplayName("取消点赞：删除记录并返回最新点赞数")
    void unlikeRemovesRecord() {
        when(noteLikeMapper.delete(any())).thenReturn(1);
        when(noteLikeMapper.selectCount(any())).thenReturn(0L);

        long count = likeService.unlike(NOTE_ID);

        verify(noteLikeMapper).delete(any());
        assertEquals(0L, count);
    }

    @Test
    @DisplayName("取消没点过赞的笔记：幂等，不报错")
    void unlikeWithoutLikeIsIdempotent() {
        when(noteLikeMapper.delete(any())).thenReturn(0);
        when(noteLikeMapper.selectCount(any())).thenReturn(0L);

        assertEquals(0L, likeService.unlike(NOTE_ID));
    }

    /* ---------------- 查询 ---------------- */

    @Test
    @DisplayName("点赞数为空时返回 0 而不是 null")
    void countByNoteIdHandlesNull() {
        when(noteLikeMapper.selectCount(any())).thenReturn(null);

        assertEquals(0L, likeService.countByNoteId(NOTE_ID));
    }

    @Test
    @DisplayName("未登录或参数不全时 hasLiked 直接返回 false，不查库")
    void hasLikedGuardsNullArguments() {
        assertFalse(likeService.hasLiked(NOTE_ID, null));
        assertFalse(likeService.hasLiked(null, USER_ID));
        verify(noteLikeMapper, never()).selectCount(any());
    }

    @Test
    @DisplayName("我赞过的笔记：没有记录时返回空分页，不再查列表")
    void myLikedNotesEmptyPage() {
        when(noteLikeMapper.countLikedNotes(USER_ID)).thenReturn(0L);

        PageResult<NoteRankVO> result = likeService.pageMyLikedNotes(new PageQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(noteLikeMapper, never()).selectLikedNoteList(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("我赞过的笔记：分页查询并组装作者信息")
    void myLikedNotesPageAssembled() {
        when(noteLikeMapper.countLikedNotes(USER_ID)).thenReturn(1L);
        when(noteLikeMapper.selectLikedNoteList(USER_ID, 0, 10)).thenReturn(List.of(row()));
        when(noteViewAssembler.assembleRankList(any())).thenReturn(List.of(new NoteRankVO()));

        PageResult<NoteRankVO> result = likeService.pageMyLikedNotes(new PageQuery());

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        verify(noteLikeMapper).selectLikedNoteList(USER_ID, 0, 10);
    }

    @Test
    @DisplayName("我赞过的笔记：未登录抛 401")
    void myLikedNotesRequiresLogin() {
        UserContext.clear();

        BusinessException e = assertThrows(BusinessException.class,
                () -> likeService.pageMyLikedNotes(new PageQuery()));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    @Test
    @DisplayName("笔记被删除时，为该笔记清理点赞记录")
    void deleteLikesByNoteIdRemovesRecords() {
        when(noteLikeMapper.delete(any())).thenReturn(2);

        likeService.deleteLikesByNoteId(NOTE_ID);

        verify(noteLikeMapper).delete(any());
    }

    @Test
    @DisplayName("点赞待审核的公开笔记：拒绝（还没通过审核不能点赞）")
    void likePendingNoteRejected() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(pendingPublicNote());

        BusinessException e = assertThrows(BusinessException.class, () -> likeService.like(NOTE_ID));

        assertEquals(ErrorCode.NOTE_STATE_INVALID, e.getErrorCode());
        verify(noteLikeMapper, never()).insert(any(NoteLike.class));
    }

    /* ---------------- 辅助方法 ---------------- */

    private Note note(Visibility visibility) {
        Note note = new Note();
        note.setId(NOTE_ID);
        note.setUserId(1L);
        note.setBookName("《活着》");
        note.setContent("正文");
        note.setVisibility(visibility);
        // 历史数据默认已通过审核
        note.setAuditStatus(AuditStatus.APPROVED);
        return note;
    }

    private Note pendingPublicNote() {
        Note note = note(Visibility.PUBLIC);
        note.setAuditStatus(AuditStatus.PENDING);
        return note;
    }

    private NoteWithLikeRow row() {
        NoteWithLikeRow row = new NoteWithLikeRow();
        row.setId(NOTE_ID);
        row.setUserId(1L);
        row.setBookName("《活着》");
        row.setContent("正文");
        row.setVisibility("PUBLIC");
        row.setLikeCount(1L);
        return row;
    }
}
