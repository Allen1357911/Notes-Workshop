package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.exception.ErrorCode;
import com.notework.common.util.BizAssert;
import com.notework.dto.response.AdminCommentVO;
import com.notework.entity.Comment;
import com.notework.entity.Note;
import com.notework.entity.User;
import com.notework.interceptor.UserContext;
import com.notework.mapper.CommentMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
import com.notework.service.AdminCommentService;
import com.notework.service.CommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员后台：违规评论管理实现。
 * <p>
 * 删除是**逻辑删除**（作业明确要求）：实体的 deleted 字段由 MyBatis-Plus 的 @TableLogic 接管，
 * mapper.delete(...) 会自动变成 UPDATE ... SET deleted = 1，并且查询会自动过滤掉已删除的评论。
 * 删除父评论时，按 root_id 把它整棵子树一起逻辑删除，避免留下"孤儿回复"。
 */
@Service
public class AdminCommentServiceImpl implements AdminCommentService {

    private static final Logger log = LoggerFactory.getLogger(AdminCommentServiceImpl.class);

    private final CommentMapper commentMapper;
    private final NoteMapper noteMapper;
    private final UserMapper userMapper;
    private final CommentService commentService;

    public AdminCommentServiceImpl(CommentMapper commentMapper, NoteMapper noteMapper, UserMapper userMapper,
                                   CommentService commentService) {
        this.commentMapper = commentMapper;
        this.noteMapper = noteMapper;
        this.userMapper = userMapper;
        this.commentService = commentService;
    }

    @Override
    public PageResult<AdminCommentVO> pageComments(Long noteId, PageQuery pageQuery) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        if (noteId != null) {
            wrapper.eq(Comment::getNoteId, noteId);
        }
        wrapper.orderByDesc(Comment::getId);

        Page<Comment> page = commentMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery.getSize()), wrapper);
        List<Comment> records = page.getRecords();
        int current = (int) page.getCurrent();
        int size = (int) page.getSize();
        if (records.isEmpty()) {
            return PageResult.empty(current, size);
        }

        List<Long> noteIds = records.stream().map(Comment::getNoteId).distinct().toList();
        Map<Long, Note> notes = noteMapper.selectByIds(noteIds).stream()
                .collect(Collectors.toMap(Note::getId, Function.identity()));
        List<Long> userIds = records.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, User> users = userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminCommentVO> list = records.stream()
                .map(comment -> AdminCommentVO.of(comment,
                        notes.get(comment.getNoteId()),
                        users.get(comment.getUserId())))
                .toList();

        return PageResult.of(list, page.getTotal(), current, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        BizAssert.notNull(comment, ErrorCode.COMMENT_NOT_FOUND);

        // 删除范围与用户自己删评论完全一致（顶层评论=整棵树；回复=它自己和它的后代），
        // 复用 CommentService 的实现，避免两处规则不一致
        int affected = commentService.deleteCommentWithReplies(commentId);

        log.info("管理员[{}]删除违规评论[{}]（含子回复），影响 {} 条", UserContext.getUserId(), commentId, affected);
    }
}
