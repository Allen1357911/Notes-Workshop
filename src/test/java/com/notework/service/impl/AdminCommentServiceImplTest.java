package com.notework.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.Role;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.dto.response.AdminCommentVO;
import com.notework.entity.Comment;
import com.notework.entity.Note;
import com.notework.entity.User;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.CommentMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
import com.notework.service.CommentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员后台：违规评论管理单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminCommentServiceImplTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long NOTE_ID = 70L;
    private static final Long COMMENT_ID = 300L;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private NoteMapper noteMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CommentService commentService;

    private AdminCommentServiceImpl adminCommentService;

    /** LambdaQueryWrapper 解析列名需要先注册实体的表信息（纯单元测试没有真正启动 MyBatis） */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Comment.class);
    }

    @BeforeEach
    void setUp() {
        adminCommentService = new AdminCommentServiceImpl(commentMapper, noteMapper, userMapper, commentService);
        UserContext.set(new LoginUser(ADMIN_ID, "admin", Role.ADMIN));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("评论列表：带上书名与评论人昵称")
    void pageCommentsAssembled() {
        when(commentMapper.<Page<Comment>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Comment> page = invocation.getArgument(0);
            page.setRecords(List.of(comment(COMMENT_ID, null, COMMENT_ID)));
            page.setTotal(1);
            return page;
        });
        when(noteMapper.selectByIds(any())).thenReturn(List.of(note()));
        when(userMapper.selectByIds(any())).thenReturn(List.of(user()));

        PageResult<AdminCommentVO> result = adminCommentService.pageComments(NOTE_ID, new PageQuery());

        assertEquals(1L, result.getTotal());
        AdminCommentVO vo = result.getList().get(0);
        assertEquals("《活着》", vo.getNoteBookName());
        assertEquals("zhangsan", vo.getUserNickname());
        assertEquals("这条评论涉嫌违规", vo.getContent());
    }

    @Test
    @DisplayName("删除评论：交给 CommentService 同一套级联逻辑（顶层整棵树、回复只删自己与后代）")
    void deleteCommentDelegatesToCommentService() {
        when(commentMapper.selectById(COMMENT_ID)).thenReturn(comment(COMMENT_ID, null, COMMENT_ID));
        when(commentService.deleteCommentWithReplies(COMMENT_ID)).thenReturn(3);

        adminCommentService.deleteComment(COMMENT_ID);

        verify(commentService).deleteCommentWithReplies(COMMENT_ID);
    }

    @Test
    @DisplayName("删除不存在的评论：抛 40001，不做任何删除")
    void deleteMissingComment() {
        when(commentMapper.selectById(COMMENT_ID)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> adminCommentService.deleteComment(COMMENT_ID));

        assertEquals(ErrorCode.COMMENT_NOT_FOUND, e.getErrorCode());
        verify(commentService, org.mockito.Mockito.never()).deleteCommentWithReplies(any());
    }

    @Test
    @DisplayName("评论列表：没有数据时返回空分页")
    void pageCommentsEmpty() {
        when(commentMapper.<Page<Comment>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Comment> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        PageResult<AdminCommentVO> result = adminCommentService.pageComments(null, new PageQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    private Comment comment(Long id, Long parentId, Long rootId) {
        Comment comment = new Comment();
        comment.setId(id);
        comment.setNoteId(NOTE_ID);
        comment.setUserId(5L);
        comment.setParentId(parentId);
        comment.setRootId(rootId);
        comment.setContent("这条评论涉嫌违规");
        comment.setDeleted(0);
        return comment;
    }

    private Note note() {
        Note note = new Note();
        note.setId(NOTE_ID);
        note.setBookName("《活着》");
        return note;
    }

    private User user() {
        User user = new User();
        user.setId(5L);
        user.setUsername("zhangsan");
        user.setNickname("zhangsan");
        return user;
    }
}
