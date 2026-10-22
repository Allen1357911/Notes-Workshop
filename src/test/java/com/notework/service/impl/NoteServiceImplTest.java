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
import com.notework.config.UploadProperties;
import com.notework.dto.request.CreateNoteRequest;
import com.notework.dto.request.UpdateNoteRequest;
import com.notework.dto.response.NoteListVO;
import com.notework.dto.response.NoteRankVO;
import com.notework.dto.response.NoteVO;
import com.notework.entity.Note;
import com.notework.entity.NoteImage;
import com.notework.entity.User;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.FollowMapper;
import com.notework.mapper.NoteImageMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
import com.notework.mapper.row.NoteWithLikeRow;
import com.notework.service.CommentService;
import com.notework.service.LikeService;
import com.notework.service.support.NoteViewAssembler;
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
import org.springframework.util.unit.DataSize;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 笔记业务层单元测试。
 * <p>
 * 重点验证三类规则：可见性与读写权限、配图的替换语义、分页组装。
 * Mapper 用 Mockito 替身，不依赖数据库。
 */
@ExtendWith(MockitoExtension.class)
class NoteServiceImplTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_ID = 2L;

    @Mock
    private NoteMapper noteMapper;

    @Mock
    private NoteImageMapper noteImageMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private LikeService likeService;

    @Mock
    private NoteViewAssembler noteViewAssembler;

    @Mock
    private CommentService commentService;

    @Mock
    private FollowMapper followMapper;

    private NoteServiceImpl noteService;

    /**
     * 纯单元测试没有真正启动 MyBatis，而 LambdaQueryWrapper 解析列名需要先注册实体的表信息，
     * 这里手动注册一次，否则用到 .in() 之类的条件时会报 "can not find lambda cache"。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Note.class);
        TableInfoHelper.initTableInfo(assistant, NoteImage.class);
    }

    @BeforeEach
    void setUp() {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setDir("./uploads");
        uploadProperties.setUrlPrefix("/uploads");
        uploadProperties.setMaxImageSize(DataSize.ofMegabytes(5));

        noteService = new NoteServiceImpl(noteMapper, noteImageMapper, userMapper, uploadProperties,
                likeService, noteViewAssembler, commentService, followMapper);
        loginAs(AUTHOR_ID, Role.USER);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /* ---------------- 新建 ---------------- */

    @Test
    @DisplayName("新建笔记：保存正文与配图，返回完整详情")
    void createNoteSavesImages() {
        when(noteMapper.insert(any(Note.class))).thenAnswer(invocation -> {
            Note note = invocation.getArgument(0);
            note.setId(100L);
            return 1;
        });
        when(noteImageMapper.insert(any(NoteImage.class))).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of(
                image(100L, "/uploads/note/a.png", 0),
                image(100L, "/uploads/note/b.png", 1)));
        // 插入后会按主键回查一次（为了拿到数据库生成的时间字段）
        when(noteMapper.selectById(100L)).thenReturn(note(100L, AUTHOR_ID, Visibility.PUBLIC));

        NoteVO vo = noteService.createNote(createRequest(Visibility.PUBLIC,
                List.of("/uploads/note/a.png", "/uploads/note/b.png")));

        assertEquals(100L, vo.getId().longValue());
        assertEquals("《活着》", vo.getBookName());
        assertEquals(2, vo.getImages().size());
        assertEquals("PUBLIC", vo.getVisibility());
        assertTrue(vo.isEditable(), "作者本人应可编辑");
        assertNull(vo.getAiResult(), "AI 结果在基础阶段恒为空");
        assertEquals(0L, vo.getLikeCount());
        assertFalse(vo.isLiked());
        verify(noteImageMapper, times(2)).insert(any(NoteImage.class));
    }

    @Test
    @DisplayName("新建笔记时未登录：抛 401")
    void createNoteRequiresLogin() {
        UserContext.clear();

        BusinessException e = assertThrows(BusinessException.class,
                () -> noteService.createNote(createRequest(Visibility.PRIVATE, null)));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    @Test
    @DisplayName("配图地址不是本系统上传接口返回的：拒绝保存")
    void createNoteRejectsForeignImageUrl() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> noteService.createNote(createRequest(Visibility.PUBLIC,
                        List.of("https://evil.example.com/a.png"))));

        assertEquals(ErrorCode.PARAM_ERROR, e.getErrorCode());
        assertTrue(e.getMessage().contains("配图地址"));
        verify(noteMapper, never()).insert(any(Note.class));
    }

    /* ---------------- 详情与可见性 ---------------- */

    @Test
    @DisplayName("公开笔记：未登录访客也能看，但不能编辑")
    void publicNoteReadableByVisitor() {
        UserContext.clear();
        when(noteMapper.selectById(50L)).thenReturn(note(50L, AUTHOR_ID, Visibility.PUBLIC));
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        NoteVO vo = noteService.getNoteDetail(50L);

        assertEquals(50L, vo.getId().longValue());
        assertFalse(vo.isEditable());
        assertEquals(List.of(), vo.getImages());
        assertNull(vo.getAuditStatus(), "其他人看不到审核状态");
        assertNull(vo.getAuditRemark());
    }

    @Test
    @DisplayName("私有笔记：其他普通用户看不到")
    void privateNoteHiddenFromOthers() {
        loginAs(OTHER_ID, Role.USER);
        when(noteMapper.selectById(51L)).thenReturn(note(51L, AUTHOR_ID, Visibility.PRIVATE));

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.getNoteDetail(51L));

        assertEquals(ErrorCode.NOTE_VISIBILITY_DENIED, e.getErrorCode());
    }

    @Test
    @DisplayName("私有笔记：作者本人能看且可编辑")
    void privateNoteVisibleToAuthor() {
        when(noteMapper.selectById(51L)).thenReturn(note(51L, AUTHOR_ID, Visibility.PRIVATE));
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        NoteVO vo = noteService.getNoteDetail(51L);

        assertTrue(vo.isEditable());
    }

    @Test
    @DisplayName("私有笔记：管理员能看（保证合规）")
    void privateNoteVisibleToAdmin() {
        loginAs(99L, Role.ADMIN);
        when(noteMapper.selectById(51L)).thenReturn(note(51L, AUTHOR_ID, Visibility.PRIVATE));
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        NoteVO vo = noteService.getNoteDetail(51L);

        assertEquals(51L, vo.getId().longValue());
        assertTrue(vo.isEditable(), "管理员可编辑他人笔记");
    }

    @Test
    @DisplayName("笔记不存在：抛 20001")
    void detailOfMissingNote() {
        when(noteMapper.selectById(404L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.getNoteDetail(404L));

        assertEquals(ErrorCode.NOTE_NOT_FOUND, e.getErrorCode());
    }

    /* ---------------- 编辑 ---------------- */

    @Test
    @DisplayName("编辑他人笔记：拒绝且不写库")
    void updateRejectsNonAuthor() {
        loginAs(OTHER_ID, Role.USER);
        when(noteMapper.selectById(60L)).thenReturn(note(60L, AUTHOR_ID, Visibility.PUBLIC));

        BusinessException e = assertThrows(BusinessException.class,
                () -> noteService.updateNote(60L, updateRequest(Visibility.PUBLIC, null)));

        assertEquals(ErrorCode.NOTE_PERMISSION_DENIED, e.getErrorCode());
        verify(noteMapper, never()).updateById(any(Note.class));
    }

    @Test
    @DisplayName("管理员可以编辑他人笔记")
    void updateAllowedForAdmin() {
        loginAs(99L, Role.ADMIN);
        when(noteMapper.selectById(60L)).thenReturn(note(60L, AUTHOR_ID, Visibility.PUBLIC));
        when(noteMapper.updateById(any(Note.class))).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        noteService.updateNote(60L, updateRequest(Visibility.PRIVATE, null));

        ArgumentCaptor<Note> updated = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).updateById(updated.capture());
        assertEquals(60L, updated.getValue().getId().longValue());
        assertEquals(Visibility.PRIVATE, updated.getValue().getVisibility(), "管理员改可见性应写入数据库");
    }

    @Test
    @DisplayName("编辑时 imageUrls 为 null：不动原有配图")
    void updateKeepsImagesWhenNull() {
        when(noteMapper.selectById(60L)).thenReturn(note(60L, AUTHOR_ID, Visibility.PUBLIC));
        when(noteMapper.updateById(any(Note.class))).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of(image(60L, "/uploads/note/old.png", 0)));

        NoteVO vo = noteService.updateNote(60L, updateRequest(Visibility.PUBLIC, null));

        verify(noteImageMapper, never()).delete(any());
        assertEquals(1, vo.getImages().size(), "原配图应保留");
    }

    @Test
    @DisplayName("编辑时 imageUrls 为空列表：清空配图")
    void updateClearsImagesWhenEmptyList() {
        when(noteMapper.selectById(60L)).thenReturn(note(60L, AUTHOR_ID, Visibility.PUBLIC));
        when(noteMapper.updateById(any(Note.class))).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.delete(any())).thenReturn(0);
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        NoteVO vo = noteService.updateNote(60L, updateRequest(Visibility.PUBLIC, List.of()));

        verify(noteImageMapper).delete(any());
        assertTrue(vo.getImages().isEmpty());
    }

    /* ---------------- 删除 ---------------- */

    @Test
    @DisplayName("删除笔记：配图记录与点赞记录一并删除")
    void deleteRemovesImagesToo() {
        when(noteMapper.selectById(70L)).thenReturn(note(70L, AUTHOR_ID, Visibility.PRIVATE));
        when(noteImageMapper.delete(any())).thenReturn(1);
        when(noteMapper.deleteById(70L)).thenReturn(1);

        noteService.deleteNote(70L);

        verify(noteImageMapper).delete(any());
        verify(likeService).deleteLikesByNoteId(70L);
        verify(noteMapper).deleteById(70L);
    }

    @Test
    @DisplayName("删除他人笔记：拒绝且不删任何数据")
    void deleteRejectsNonAuthor() {
        loginAs(OTHER_ID, Role.USER);
        when(noteMapper.selectById(70L)).thenReturn(note(70L, AUTHOR_ID, Visibility.PUBLIC));

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.deleteNote(70L));

        assertEquals(ErrorCode.NOTE_PERMISSION_DENIED, e.getErrorCode());
        verify(noteMapper, never()).deleteById(any(Long.class));
        verify(noteImageMapper, never()).delete(any());
    }

    /* ---------------- 分页 ---------------- */

    @Test
    @DisplayName("我的笔记：分页返回，且一次性批量补齐作者与配图")
    void myNotesPageAssembled() {
        Note note = note(80L, AUTHOR_ID, Visibility.PRIVATE);
        stubPage(List.of(note));
        when(userMapper.selectByIds(any())).thenReturn(List.of(author(AUTHOR_ID)));
        when(noteImageMapper.selectList(any())).thenReturn(List.of(image(80L, "/uploads/note/x.png", 0)));

        PageResult<NoteListVO> result = noteService.pageMyNotes(new PageQuery());

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getList().size());
        NoteListVO vo = result.getList().get(0);
        assertEquals("张三", vo.getAuthorNickname());
        assertEquals(1, vo.getImages().size(), "配图应通过一次批量查询补齐");
        verify(noteImageMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("公开列表：没有数据时返回空分页结构")
    void publicNotesEmptyPage() {
        stubPage(List.of());

        PageResult<NoteListVO> result = noteService.pagePublicNotes(new PageQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
    }

    @Test
    @DisplayName("我的笔记：未登录抛 401")
    void myNotesRequiresLogin() {
        UserContext.clear();

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.pageMyNotes(new PageQuery()));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    /* ---------------- 排行 ---------------- */

    @Test
    @DisplayName("按点赞数排行：走手写 SQL，总数与列表口径一致")
    void rankedNotesPage() {
        when(noteMapper.countRankNotes()).thenReturn(2L);
        when(noteMapper.selectRankList(0, 10))
                .thenReturn(List.of(row(1L, AUTHOR_ID, 5L), row(2L, AUTHOR_ID, 1L)));
        when(noteViewAssembler.assembleRankList(any())).thenReturn(List.of(new NoteRankVO(), new NoteRankVO()));

        PageResult<NoteRankVO> result = noteService.pageRankedNotes(new PageQuery());

        assertEquals(2L, result.getTotal());
        assertEquals(2, result.getList().size());
        verify(noteMapper).selectRankList(0, 10);
    }

    @Test
    @DisplayName("按点赞数排行：没有公开笔记时返回空分页，不再查列表")
    void rankedNotesEmptyPage() {
        when(noteMapper.countRankNotes()).thenReturn(0L);

        PageResult<NoteRankVO> result = noteService.pageRankedNotes(new PageQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(noteMapper, never()).selectRankList(anyInt(), anyInt());
    }

    /* ---------------- 审核（扩展部分） ---------------- */

    @Test
    @DisplayName("新建公开笔记：自动进入待审核状态")
    void createPublicNoteEntersPendingAudit() {
        ArgumentCaptor<Note> inserted = ArgumentCaptor.forClass(Note.class);
        when(noteMapper.insert(inserted.capture())).thenAnswer(invocation -> {
            inserted.getValue().setId(200L);
            return 1;
        });
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        Note saved = note(200L, AUTHOR_ID, Visibility.PUBLIC);
        saved.setAuditStatus(AuditStatus.PENDING);
        saved.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(noteMapper.selectById(200L)).thenReturn(saved);

        NoteVO vo = noteService.createNote(createRequest(Visibility.PUBLIC, null));

        assertEquals(AuditStatus.PENDING, inserted.getValue().getAuditStatus());
        assertEquals("PENDING", vo.getAuditStatus());
        assertEquals("待审核", vo.getAuditStatusLabel());
        assertNotNull(vo.getCreateTime(), "新建后返回的详情必须带创建时间（回查数据库默认值）");
    }

    @Test
    @DisplayName("新建私有笔记：不参与审核，直接已通过")
    void createPrivateNoteSkipsAudit() {
        ArgumentCaptor<Note> inserted = ArgumentCaptor.forClass(Note.class);
        when(noteMapper.insert(inserted.capture())).thenAnswer(invocation -> {
            inserted.getValue().setId(201L);
            return 1;
        });
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of());
        when(noteMapper.selectById(201L)).thenReturn(note(201L, AUTHOR_ID, Visibility.PRIVATE));

        noteService.createNote(createRequest(Visibility.PRIVATE, null));

        assertEquals(AuditStatus.APPROVED, inserted.getValue().getAuditStatus());
    }

    @Test
    @DisplayName("编辑公开笔记：重新进入待审核并清掉上一次的驳回原因")
    void updatePublicNoteReturnsToPending() {
        Note rejected = note(60L, AUTHOR_ID, Visibility.PUBLIC);
        rejected.setAuditStatus(AuditStatus.REJECTED);
        rejected.setAuditRemark("内容不合规");
        when(noteMapper.selectById(60L)).thenReturn(rejected);
        ArgumentCaptor<Note> updated = ArgumentCaptor.forClass(Note.class);
        when(noteMapper.updateById(updated.capture())).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        noteService.updateNote(60L, updateRequest(Visibility.PUBLIC, null));

        assertEquals(AuditStatus.PENDING, updated.getValue().getAuditStatus());
        assertEquals("", updated.getValue().getAuditRemark());
    }

    @Test
    @DisplayName("待审核的公开笔记：其他人按不存在处理，不暴露存在性")
    void pendingNoteHiddenFromOthers() {
        loginAs(OTHER_ID, Role.USER);
        Note pending = note(90L, AUTHOR_ID, Visibility.PUBLIC);
        pending.setAuditStatus(AuditStatus.PENDING);
        when(noteMapper.selectById(90L)).thenReturn(pending);

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.getNoteDetail(90L));

        assertEquals(ErrorCode.NOTE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("已下架的公开笔记：其他人同样看不到")
    void takenDownNoteHiddenFromOthers() {
        UserContext.clear();
        Note takenDown = note(92L, AUTHOR_ID, Visibility.PUBLIC);
        takenDown.setAuditStatus(AuditStatus.TAKEN_DOWN);
        when(noteMapper.selectById(92L)).thenReturn(takenDown);

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.getNoteDetail(92L));

        assertEquals(ErrorCode.NOTE_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("作者能看到自己笔记的审核状态与驳回原因")
    void authorSeesAuditInfo() {
        Note rejected = note(91L, AUTHOR_ID, Visibility.PUBLIC);
        rejected.setAuditStatus(AuditStatus.REJECTED);
        rejected.setAuditRemark("请补充自己的思考");
        when(noteMapper.selectById(91L)).thenReturn(rejected);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        NoteVO vo = noteService.getNoteDetail(91L);

        assertEquals("REJECTED", vo.getAuditStatus());
        assertEquals("已驳回", vo.getAuditStatusLabel());
        assertEquals("请补充自己的思考", vo.getAuditRemark());
    }

    /* ---------------- 重新提交审核（模块二：用户侧重提） ---------------- */

    @Test
    @DisplayName("重新提交审核：已驳回 → 待审核，并清掉驳回原因")
    void resubmitRejectedNote() {
        Note rejected = note(70L, AUTHOR_ID, Visibility.PUBLIC);
        rejected.setAuditStatus(AuditStatus.REJECTED);
        rejected.setAuditRemark("请补充自己的思考");

        Note afterResubmit = note(70L, AUTHOR_ID, Visibility.PUBLIC);
        afterResubmit.setAuditStatus(AuditStatus.PENDING);
        // 第一次查询返回驳回态，更新之后重新查询返回待审核态（模拟数据库里的真实变化）
        when(noteMapper.selectById(70L)).thenReturn(rejected, afterResubmit);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        // 重新提交用的是条件更新（WHERE id=? AND audit_status='REJECTED'），返回受影响行数
        when(noteMapper.update(captor.capture(), any())).thenReturn(1);
        when(userMapper.selectById(AUTHOR_ID)).thenReturn(author(AUTHOR_ID));
        when(noteImageMapper.selectList(any())).thenReturn(List.of());

        NoteVO vo = noteService.resubmitForAudit(70L);

        assertEquals(AuditStatus.PENDING, captor.getValue().getAuditStatus());
        assertEquals("", captor.getValue().getAuditRemark());
        assertEquals("PENDING", vo.getAuditStatus(), "返回的详情应反映最新的审核状态");
    }

    @Test
    @DisplayName("重新提交审核：并发下状态已被别人改掉（影响 0 行）→ 20006")
    void resubmitLosesRaceWhenStateChanged() {
        Note rejected = note(70L, AUTHOR_ID, Visibility.PUBLIC);
        rejected.setAuditStatus(AuditStatus.REJECTED);
        when(noteMapper.selectById(70L)).thenReturn(rejected);
        // 条件更新没改到任何行：说明另一个请求已经把状态从 REJECTED 改走了
        when(noteMapper.update(any(Note.class), any())).thenReturn(0);

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.resubmitForAudit(70L));

        assertEquals(ErrorCode.NOTE_STATE_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("重新提交审核：不是已驳回状态 → 20006")
    void resubmitRejectsOtherStates() {
        when(noteMapper.selectById(70L)).thenReturn(note(70L, AUTHOR_ID, Visibility.PUBLIC));

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.resubmitForAudit(70L));

        assertEquals(ErrorCode.NOTE_STATE_INVALID, e.getErrorCode());
        verify(noteMapper, never()).update(any(Note.class), any());
    }

    @Test
    @DisplayName("重新提交审核：私有笔记 → 20006（私有笔记不参与审核）")
    void resubmitRejectsPrivateNote() {
        Note priv = note(70L, AUTHOR_ID, Visibility.PRIVATE);
        priv.setAuditStatus(AuditStatus.REJECTED);
        when(noteMapper.selectById(70L)).thenReturn(priv);

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.resubmitForAudit(70L));

        assertEquals(ErrorCode.NOTE_STATE_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("重新提交审核：他人操作 → 20002")
    void resubmitRejectsOthers() {
        loginAs(OTHER_ID, Role.USER);
        Note rejected = note(70L, AUTHOR_ID, Visibility.PUBLIC);
        rejected.setAuditStatus(AuditStatus.REJECTED);
        when(noteMapper.selectById(70L)).thenReturn(rejected);

        BusinessException e = assertThrows(BusinessException.class, () -> noteService.resubmitForAudit(70L));

        assertEquals(ErrorCode.NOTE_PERMISSION_DENIED, e.getErrorCode());
    }

    /* ---------------- 关注流与作者主页（模块三） ---------------- */

    @Test
    @DisplayName("关注流：我关注的人为空时直接返回空分页，不查笔记表")
    void followingFeedEmptyWhenNoFollowee() {
        when(followMapper.selectList(any())).thenReturn(List.of());

        PageResult<NoteListVO> result = noteService.pageFollowingFeed(new PageQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
        verify(noteMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("作者主页公开笔记：作者不存在 → 10001")
    void authorNotesRejectsMissingUser() {
        when(userMapper.selectById(404L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> noteService.pageUserPublicNotes(404L, new PageQuery()));

        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
    }

    /* ---------------- 删除笔记的连带清理（模块三：评论一起清理） ---------------- */

    @Test
    @DisplayName("删除笔记：配图、点赞与评论一并清理")
    void deleteNoteAlsoClearsComments() {
        when(noteMapper.selectById(50L)).thenReturn(note(50L, AUTHOR_ID, Visibility.PUBLIC));
        when(noteImageMapper.delete(any())).thenReturn(2);
        when(noteMapper.deleteById(50L)).thenReturn(1);

        noteService.deleteNote(50L);

        verify(noteImageMapper).delete(any());
        verify(likeService).deleteLikesByNoteId(50L);
        verify(commentService).deleteByNoteId(50L);
        verify(noteMapper).deleteById(50L);
    }

    /* ---------------- 辅助方法 ---------------- */

    private void loginAs(Long userId, Role role) {
        UserContext.set(new LoginUser(userId, "user" + userId, role));
    }

    private void stubPage(List<Note> records) {
        when(noteMapper.<Page<Note>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Note> page = invocation.getArgument(0);
            page.setRecords(records);
            page.setTotal(records.size());
            return page;
        });
    }

    private Note note(Long id, Long userId, Visibility visibility) {
        Note note = new Note();
        note.setId(id);
        note.setUserId(userId);
        note.setBookName("《活着》");
        note.setContent("这是一段读书笔记正文。");
        note.setVisibility(visibility);
        // 历史数据默认就是已通过状态
        note.setAuditStatus(AuditStatus.APPROVED);
        return note;
    }

    private NoteImage image(Long noteId, String url, int sortOrder) {
        NoteImage image = new NoteImage();
        image.setNoteId(noteId);
        image.setUrl(url);
        image.setSortOrder(sortOrder);
        return image;
    }

    private User author(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("zhangsan");
        user.setNickname("张三");
        user.setRole(Role.USER);
        return user;
    }

    private NoteWithLikeRow row(Long id, Long userId, Long likeCount) {
        NoteWithLikeRow row = new NoteWithLikeRow();
        row.setId(id);
        row.setUserId(userId);
        row.setBookName("《活着》");
        row.setContent("这是一段读书笔记正文。");
        row.setVisibility("PUBLIC");
        row.setLikeCount(likeCount);
        return row;
    }

    private CreateNoteRequest createRequest(Visibility visibility, List<String> imageUrls) {
        CreateNoteRequest request = new CreateNoteRequest();
        request.setBookName("《活着》");
        request.setContent("这是一段读书笔记正文。");
        request.setVisibility(visibility);
        request.setImageUrls(imageUrls);
        return request;
    }

    private UpdateNoteRequest updateRequest(Visibility visibility, List<String> imageUrls) {
        UpdateNoteRequest request = new UpdateNoteRequest();
        request.setBookName("《活着》");
        request.setContent("改过的笔记正文。");
        request.setVisibility(visibility);
        request.setImageUrls(imageUrls);
        return request;
    }
}
