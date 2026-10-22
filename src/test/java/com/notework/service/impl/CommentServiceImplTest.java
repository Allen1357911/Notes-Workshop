package com.notework.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Role;
import com.notework.common.enums.Visibility;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.dto.request.CreateCommentRequest;
import com.notework.dto.response.CommentVO;
import com.notework.entity.Comment;
import com.notework.entity.Note;
import com.notework.entity.User;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.CommentMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 社区互动：评论与多级回复单元测试。
 * <p>
 * 重点验证：root_id 的维护规则、只能评论已通过审核的公开笔记、评论树的组装、级联逻辑删除与删除权限。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    private static final Long ME = 1L;
    private static final Long OTHER = 2L;
    private static final Long ADMIN_ID = 99L;
    private static final Long NOTE_ID = 70L;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private NoteMapper noteMapper;

    @Mock
    private UserMapper userMapper;

    private CommentServiceImpl commentService;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Comment.class);
    }

    @BeforeEach
    void setUp() {
        commentService = new CommentServiceImpl(commentMapper, noteMapper, userMapper);
        UserContext.set(new LoginUser(ME, "me", Role.USER));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /* ---------------- 发表评论与回复 ---------------- */

    @Test
    @DisplayName("顶层评论：root_id 回填成自己的 id")
    void createTopLevelCommentFillsRootId() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(publicNote());
        when(commentMapper.insert(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            comment.setId(500L);
            return 1;
        });
        when(userMapper.selectById(ME)).thenReturn(user(ME));

        Comment saved = comment(500L, null, 500L, "写得很清楚");
        saved.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(commentMapper.selectById(500L)).thenReturn(saved);

        CommentVO vo = commentService.createComment(NOTE_ID, request("写得很清楚", null));

        assertEquals(500L, vo.getId());
        assertEquals("写得很清楚", vo.getContent());
        assertTrue(vo.isDeletable());
        assertNotNull(vo.getCreateTime(), "发表评论的响应必须带创建时间（回查数据库默认值）");
        ArgumentCaptor<Comment> fill = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).updateById(fill.capture());
        assertEquals(500L, fill.getValue().getRootId(), "顶层评论的 root_id 应等于自身 id");
    }

    @Test
    @DisplayName("回复评论：parent_id 指向父评论，root_id 继承父评论所属的顶层评论")
    void createReplyInheritsRootId() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(publicNote());
        when(commentMapper.selectById(11L)).thenReturn(comment(11L, 10L, 10L, "父评论"));
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        when(commentMapper.insert(captor.capture())).thenAnswer(invocation -> {
            captor.getValue().setId(501L);
            return 1;
        });
        when(userMapper.selectById(ME)).thenReturn(user(ME));
        when(commentMapper.selectById(501L)).thenReturn(comment(501L, 11L, 10L, "我也这么觉得"));

        commentService.createComment(NOTE_ID, request("我也这么觉得", 11L));

        assertEquals(11L, captor.getValue().getParentId());
        assertEquals(10L, captor.getValue().getRootId(), "回复的 root_id 应指向顶层评论");
        verify(commentMapper, never()).updateById(any(Comment.class));
    }

    @Test
    @DisplayName("回复不存在的评论：40003")
    void replyMissingParent() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(publicNote());
        when(commentMapper.selectById(11L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> commentService.createComment(NOTE_ID, request("回复", 11L)));

        assertEquals(ErrorCode.COMMENT_PARENT_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("回复别的笔记下的评论：40003，不能跨笔记串评论")
    void replyParentFromAnotherNote() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(publicNote());
        when(commentMapper.selectById(11L)).thenReturn(comment(11L, null, 11L, "别的笔记的评论", 999L));

        BusinessException e = assertThrows(BusinessException.class,
                () -> commentService.createComment(NOTE_ID, request("回复", 11L)));

        assertEquals(ErrorCode.COMMENT_PARENT_INVALID, e.getErrorCode());
    }

    @Test
    @DisplayName("评论待审核的笔记：按不存在处理（20001）")
    void cannotCommentPendingNote() {
        Note pending = publicNote();
        pending.setAuditStatus(AuditStatus.PENDING);
        when(noteMapper.selectById(NOTE_ID)).thenReturn(pending);

        BusinessException e = assertThrows(BusinessException.class,
                () -> commentService.createComment(NOTE_ID, request("抢沙发", null)));

        assertEquals(ErrorCode.NOTE_NOT_FOUND, e.getErrorCode());
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("评论私有笔记：按不存在处理（20001）")
    void cannotCommentPrivateNote() {
        Note priv = publicNote();
        priv.setVisibility(Visibility.PRIVATE);
        when(noteMapper.selectById(NOTE_ID)).thenReturn(priv);

        BusinessException e = assertThrows(BusinessException.class,
                () -> commentService.createComment(NOTE_ID, request("偷偷评论", null)));

        assertEquals(ErrorCode.NOTE_NOT_FOUND, e.getErrorCode());
    }

    /* ---------------- 评论列表与评论树 ---------------- */

    @Test
    @DisplayName("评论列表：顶层评论分页，多级回复挂到各自父评论下")
    void commentTreeAssembled() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(publicNote());
        when(commentMapper.<Page<Comment>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Comment> page = invocation.getArgument(0);
            page.setRecords(List.of(comment(100L, null, 100L, "顶层一"), comment(200L, null, 200L, "顶层二")));
            page.setTotal(2);
            return page;
        });
        when(commentMapper.selectList(any())).thenReturn(List.of(
                comment(101L, 100L, 100L, "回复顶层一"),
                comment(102L, 101L, 100L, "回复回复"),          // 多级：回复的回复
                comment(103L, 999L, 100L, "父评论不在本页"),     // 父不在本页，挂到顶层下
                comment(201L, 200L, 200L, "回复顶层二")));
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(ME), user(OTHER)));

        PageResult<CommentVO> result = commentService.pageComments(NOTE_ID, new PageQuery());

        assertEquals(2L, result.getTotal());
        CommentVO top1 = result.getList().get(0);
        CommentVO top2 = result.getList().get(1);
        assertEquals("顶层一", top1.getContent());
        assertEquals(2, top1.getReplies().size(), "两个直接回复挂到顶层");
        assertEquals(101L, top1.getReplies().get(0).getId());
        assertEquals(103L, top1.getReplies().get(1).getId());
        assertEquals(1, top1.getReplies().get(0).getReplies().size(), "回复的回复挂到父回复下");
        assertEquals(102L, top1.getReplies().get(0).getReplies().get(0).getId());
        assertEquals(1, top2.getReplies().size());
        assertEquals(201L, top2.getReplies().get(0).getId());
    }

    @Test
    @DisplayName("评论列表：没有评论时返回空分页")
    void commentListEmpty() {
        when(noteMapper.selectById(NOTE_ID)).thenReturn(publicNote());
        when(commentMapper.<Page<Comment>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Comment> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        PageResult<CommentVO> result = commentService.pageComments(NOTE_ID, new PageQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    @Test
    @DisplayName("评论列表：笔记被下架后，作者本人仍能看到评论区（与笔记详情规则一致）")
    void authorCanReadCommentsOfTakenDownNote() {
        UserContext.set(new LoginUser(OTHER, "other", Role.USER));   // OTHER 是这篇笔记的作者
        Note takenDown = publicNote();
        takenDown.setAuditStatus(AuditStatus.TAKEN_DOWN);
        when(noteMapper.selectById(NOTE_ID)).thenReturn(takenDown);
        when(commentMapper.<Page<Comment>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Comment> page = invocation.getArgument(0);
            page.setRecords(List.of(comment(100L, null, 100L, "下架前发的评论")));
            page.setTotal(1);
            return page;
        });
        when(userMapper.selectByIds(any())).thenReturn(List.of(user(ME)));

        PageResult<CommentVO> result = commentService.pageComments(NOTE_ID, new PageQuery());

        assertEquals(1L, result.getTotal(), "作者应能看到自己被下架笔记的评论");
    }

    @Test
    @DisplayName("评论列表：陌生人在已下架笔记的评论区按不存在处理（不暴露存在性）")
    void strangerCannotReadCommentsOfTakenDownNote() {
        Note takenDown = publicNote();       // 作者是 OTHER，当前登录的是 ME
        takenDown.setAuditStatus(AuditStatus.TAKEN_DOWN);
        when(noteMapper.selectById(NOTE_ID)).thenReturn(takenDown);

        BusinessException e = assertThrows(BusinessException.class,
                () -> commentService.pageComments(NOTE_ID, new PageQuery()));

        assertEquals(ErrorCode.NOTE_NOT_FOUND, e.getErrorCode());
    }

    /* ---------------- 删除 ---------------- */

    @Test
    @DisplayName("删除自己的顶层评论：按 root_id 一次删掉整棵评论树")
    void deleteOwnCommentCascades() {
        when(commentMapper.selectById(100L)).thenReturn(comment(100L, null, 100L, "我的评论"));
        when(commentMapper.delete(any())).thenReturn(3);

        commentService.deleteComment(100L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Comment>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(commentMapper).delete(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("root_id"), "顶层评论按 root_id 删整棵树：" + sql);
    }

    @Test
    @DisplayName("删除一条回复：只删它自己，绝不能连带删掉别人的顶层评论与兄弟分支")
    void deleteReplyDoesNotWipeWholeTree() {
        when(commentMapper.selectById(101L)).thenReturn(comment(101L, 100L, 100L, "我的回复"));
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(commentMapper.delete(any())).thenReturn(1);

        commentService.deleteComment(101L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Comment>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(commentMapper).delete(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("id"), "按 id 精确删除：" + sql);
        assertFalse(sql.contains("root_id"), "回复不能按 root_id 删，否则会误删整棵树：" + sql);
    }

    @Test
    @DisplayName("删除一条回复：连同它下面的多级回复一起删除")
    void deleteReplyCascadesItsOwnSubtree() {
        when(commentMapper.selectById(101L)).thenReturn(comment(101L, 100L, 100L, "我的回复"));
        // 第一层查出子回复 102，第二层查出 102 的子回复 103，第三层没有更多
        when(commentMapper.selectList(any()))
                .thenReturn(List.of(comment(102L, 101L, 100L, "回复我的回复")))
                .thenReturn(List.of(comment(103L, 102L, 100L, "再深一层")))
                .thenReturn(List.of());
        when(commentMapper.delete(any())).thenReturn(3);

        commentService.deleteComment(101L);

        verify(commentMapper, times(3)).selectList(any());
        verify(commentMapper).delete(any());
    }

    @Test
    @DisplayName("删除评论：并发下已被别人删掉（影响 0 行）→ 40001，与串行行为一致")
    void deleteCommentLosesRaceWhenAlreadyDeleted() {
        when(commentMapper.selectById(100L)).thenReturn(comment(100L, null, 100L, "我的评论"));
        when(commentMapper.delete(any())).thenReturn(0);

        BusinessException e = assertThrows(BusinessException.class, () -> commentService.deleteComment(100L));

        assertEquals(ErrorCode.COMMENT_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("删除他人评论：40002")
    void cannotDeleteOthersComment() {
        when(commentMapper.selectById(100L)).thenReturn(comment(100L, null, 100L, "别人的评论", NOTE_ID, OTHER));

        BusinessException e = assertThrows(BusinessException.class, () -> commentService.deleteComment(100L));

        assertEquals(ErrorCode.COMMENT_PERMISSION_DENIED, e.getErrorCode());
        verify(commentMapper, never()).delete(any());
    }

    @Test
    @DisplayName("管理员删除他人评论：允许")
    void adminCanDeleteOthersComment() {
        UserContext.set(new LoginUser(ADMIN_ID, "admin", Role.ADMIN));
        when(commentMapper.selectById(100L)).thenReturn(comment(100L, null, 100L, "违规评论", NOTE_ID, OTHER));
        when(commentMapper.delete(any())).thenReturn(1);

        commentService.deleteComment(100L);

        verify(commentMapper).delete(any());
    }

    @Test
    @DisplayName("删除不存在的评论：40001")
    void deleteMissingComment() {
        when(commentMapper.selectById(100L)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class, () -> commentService.deleteComment(100L));

        assertEquals(ErrorCode.COMMENT_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("笔记被删除时：按 note_id 清理该笔记下的全部评论")
    void deleteByNoteIdClearsAllComments() {
        when(commentMapper.delete(any())).thenReturn(5);

        int affected = commentService.deleteByNoteId(NOTE_ID);

        assertEquals(5, affected);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Comment>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(commentMapper).delete(captor.capture());
        assertTrue(captor.getValue().getSqlSegment().contains("note_id"),
                "应按 note_id 清理：" + captor.getValue().getSqlSegment());
    }

    @Test
    @DisplayName("评论数统计：只统计未删除的评论")
    void countComments() {
        when(commentMapper.selectCount(any())).thenReturn(4L);

        assertEquals(4L, commentService.countByNoteId(NOTE_ID));
    }

    /* ---------------- 辅助方法 ---------------- */

    private CreateCommentRequest request(String content, Long parentId) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent(content);
        request.setParentId(parentId);
        return request;
    }

    private Note publicNote() {
        Note note = new Note();
        note.setId(NOTE_ID);
        note.setUserId(OTHER);
        note.setBookName("《活着》");
        note.setVisibility(Visibility.PUBLIC);
        note.setAuditStatus(AuditStatus.APPROVED);
        return note;
    }

    private Comment comment(Long id, Long parentId, Long rootId, String content) {
        return comment(id, parentId, rootId, content, NOTE_ID, ME);
    }

    private Comment comment(Long id, Long parentId, Long rootId, String content, Long noteId) {
        return comment(id, parentId, rootId, content, noteId, ME);
    }

    private Comment comment(Long id, Long parentId, Long rootId, String content, Long noteId, Long userId) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setNoteId(noteId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setRootId(rootId);
        comment.setContent(content);
        comment.setDeleted(0);
        return comment;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setNickname("用户" + id);
        return user;
    }
}
