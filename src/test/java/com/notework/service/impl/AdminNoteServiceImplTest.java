package com.notework.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Role;
import com.notework.common.enums.Visibility;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.dto.request.AuditNoteRequest;
import com.notework.dto.response.AdminNoteVO;
import com.notework.entity.Note;
import com.notework.entity.NoteImage;
import com.notework.entity.User;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.NoteImageMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员后台：内容管理与审核状态机单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminNoteServiceImplTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long AUTHOR_ID = 5L;
    private static final Long NOTE_ID = 70L;

    @Mock
    private NoteMapper noteMapper;

    @Mock
    private NoteImageMapper noteImageMapper;

    @Mock
    private UserMapper userMapper;

    private AdminNoteServiceImpl adminNoteService;

    /** LambdaQueryWrapper 解析列名需要先注册实体的表信息（纯单元测试没有真正启动 MyBatis） */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Note.class);
        TableInfoHelper.initTableInfo(assistant, NoteImage.class);
    }

    @BeforeEach
    void setUp() {
        adminNoteService = new AdminNoteServiceImpl(noteMapper, noteImageMapper, userMapper);
        UserContext.set(new LoginUser(ADMIN_ID, "admin", Role.ADMIN));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /* ---------------- 待审核队列 ---------------- */

    @Test
    @DisplayName("待审核队列：分页返回，带作者与配图")
    void pendingQueueAssembled() {
        when(noteMapper.<Page<Note>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Note> page = invocation.getArgument(0);
            page.setRecords(List.of(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.PENDING)));
            page.setTotal(1);
            return page;
        });
        when(userMapper.selectByIds(any())).thenReturn(List.of(author()));
        when(noteImageMapper.selectList(any())).thenReturn(List.of(image(NOTE_ID, "/uploads/note/a.png")));

        PageResult<AdminNoteVO> result = adminNoteService.pagePendingNotes(new PageQuery());

        assertEquals(1L, result.getTotal());
        AdminNoteVO vo = result.getList().get(0);
        assertEquals("PENDING", vo.getAuditStatus());
        assertEquals("待审核", vo.getAuditStatusLabel());
        assertEquals("zhangsan", vo.getAuthorNickname());
        assertEquals(1, vo.getImages().size());
    }

    /* ---------------- 审核 ---------------- */

    @Test
    @DisplayName("审核通过：状态变为已通过，并清空审核意见")
    void auditApprove() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.PENDING));
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        // 审核用条件更新（WHERE id=? AND audit_status='PENDING'），返回受影响行数
        when(noteMapper.update(captor.capture(), any())).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author());
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        adminNoteService.audit(NOTE_ID, auditRequest(true, null));

        assertEquals(AuditStatus.APPROVED, captor.getValue().getAuditStatus());
        assertEquals("", captor.getValue().getAuditRemark());
        assertNotNull(captor.getValue().getAuditTime(), "审核时间应写入");
    }

    @Test
    @DisplayName("并发审核：状态已被别人改掉（影响 0 行）→ 20005，不会重复通过")
    void auditLosesRaceWhenStateChanged() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.PENDING));
        when(noteMapper.update(any(Note.class), any())).thenReturn(0);

        BusinessException e = assertThrows(BusinessException.class,
                () -> adminNoteService.audit(NOTE_ID, auditRequest(true, null)));

        assertEquals(ErrorCode.NOTE_NOT_PENDING, e.getErrorCode());
    }

    @Test
    @DisplayName("审核驳回：记录管理员填写的原因")
    void auditRejectWithRemark() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.PENDING));
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        when(noteMapper.update(captor.capture(), any())).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author());
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        adminNoteService.audit(NOTE_ID, auditRequest(false, "请补充自己的思考，不要只摘抄原文"));

        assertEquals(AuditStatus.REJECTED, captor.getValue().getAuditStatus());
        assertEquals("请补充自己的思考，不要只摘抄原文", captor.getValue().getAuditRemark());
    }

    @Test
    @DisplayName("审核驳回：不填原因时使用默认说明")
    void auditRejectUsesDefaultRemark() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.PENDING));
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        when(noteMapper.update(captor.capture(), any())).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author());
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        adminNoteService.audit(NOTE_ID, auditRequest(false, "   "));

        assertTrue(captor.getValue().getAuditRemark().contains("不符合社区规范"));
    }

    @Test
    @DisplayName("审核非待审核状态的笔记：拒绝")
    void auditRejectsNonPending() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.APPROVED));

        BusinessException e = assertThrows(BusinessException.class,
                () -> adminNoteService.audit(NOTE_ID, auditRequest(true, null)));

        assertEquals(ErrorCode.NOTE_NOT_PENDING, e.getErrorCode());
        verify(noteMapper, never()).update(any(Note.class), any());
    }

    @Test
    @DisplayName("审核私有笔记：拒绝（私有笔记不参与审核）")
    void auditRejectsPrivateNote() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PRIVATE, AuditStatus.APPROVED));

        BusinessException e = assertThrows(BusinessException.class,
                () -> adminNoteService.audit(NOTE_ID, auditRequest(true, null)));

        assertEquals(ErrorCode.NOTE_STATE_INVALID, e.getErrorCode());
    }

    /* ---------------- 下架与恢复 ---------------- */

    @Test
    @DisplayName("下架已通过的公开笔记：变为已下架并记录说明")
    void offlineApprovedPublicNote() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.APPROVED));
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        when(noteMapper.update(captor.capture(), any())).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author());
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        adminNoteService.offline(NOTE_ID, "包含广告内容");

        assertEquals(AuditStatus.TAKEN_DOWN, captor.getValue().getAuditStatus());
        assertEquals("包含广告内容", captor.getValue().getAuditRemark());
    }

    @Test
    @DisplayName("并发下架：状态在检查之后被改掉（影响 0 行）→ 20006，不会把不该下架的笔记下架")
    void offlineLosesRaceWhenStateChanged() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.APPROVED));
        when(noteMapper.update(any(Note.class), any())).thenReturn(0);

        BusinessException e = assertThrows(BusinessException.class,
                () -> adminNoteService.offline(NOTE_ID, null));

        assertEquals(ErrorCode.NOTE_STATE_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("下架待审核笔记：拒绝")
    void offlineRejectsPendingNote() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.PENDING));

        BusinessException e = assertThrows(BusinessException.class,
                () -> adminNoteService.offline(NOTE_ID, null));

        assertEquals(ErrorCode.NOTE_STATE_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("恢复已下架笔记：重新变为已通过并清空说明")
    void restoreTakenDownNote() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.TAKEN_DOWN));
        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        when(noteMapper.update(captor.capture(), any())).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author());
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        adminNoteService.restore(NOTE_ID);

        assertEquals(AuditStatus.APPROVED, captor.getValue().getAuditStatus());
        assertEquals("", captor.getValue().getAuditRemark());
    }

    @Test
    @DisplayName("恢复未下架的笔记：拒绝")
    void restoreRejectsNotTakenDown() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(note(NOTE_ID, Visibility.PUBLIC, AuditStatus.APPROVED));

        BusinessException e = assertThrows(BusinessException.class, () -> adminNoteService.restore(NOTE_ID));

        assertEquals(ErrorCode.NOTE_STATE_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("笔记不存在：抛 20001")
    void rejectsMissingNote() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> adminNoteService.restore(NOTE_ID));

        assertEquals(ErrorCode.NOTE_NOT_FOUND, e.getErrorCode());
    }

    /* ---------------- 辅助方法 ---------------- */

    private Note note(Long id, Visibility visibility, AuditStatus auditStatus) {
        Note note = new Note();
        note.setId(id);
        note.setUserId(AUTHOR_ID);
        note.setBookName("《活着》");
        note.setContent("正文");
        note.setVisibility(visibility);
        note.setAuditStatus(auditStatus);
        return note;
    }

    private NoteImage image(Long noteId, String url) {
        NoteImage image = new NoteImage();
        image.setNoteId(noteId);
        image.setUrl(url);
        image.setSortOrder(0);
        return image;
    }

    private User author() {
        User user = new User();
        user.setId(AUTHOR_ID);
        user.setUsername("zhangsan");
        user.setNickname("zhangsan");
        user.setRole(Role.USER);
        return user;
    }

    private AuditNoteRequest auditRequest(boolean approved, String remark) {
        AuditNoteRequest request = new AuditNoteRequest();
        request.setApproved(approved);
        request.setRemark(remark);
        return request;
    }
}
