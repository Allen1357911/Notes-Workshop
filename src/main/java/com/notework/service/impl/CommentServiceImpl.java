package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Visibility;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.common.util.BizAssert;
import com.notework.dto.request.CreateCommentRequest;
import com.notework.dto.response.CommentVO;
import com.notework.entity.Comment;
import com.notework.entity.Note;
import com.notework.entity.User;
import com.notework.interceptor.UserContext;
import com.notework.mapper.CommentMapper;
import com.notework.mapper.NoteMapper;
import com.notework.mapper.UserMapper;
import com.notework.service.CommentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 社区互动：评论与多级回复实现。
 * <p>
 * 数据模型：<code>parent_id</code> 指向直接父评论，<code>root_id</code> 指向所在评论树的顶层评论。
 * 顶层评论的 root_id 等于它自己的 id；任何一条回复的 root_id 都等于它所属顶层评论的 id。
 * 这样"取一棵树的所有回复"只需要一个 root_id 条件，删除整棵子树也只需一次 UPDATE。
 * <p>
 * 删除一律是**逻辑删除**（t_comment.deleted 由 @TableLogic 接管），删父评论会级联删掉整棵子树，
 * 避免前台出现"回复还在、被回复的评论没了"的孤儿数据。
 */
@Service
public class CommentServiceImpl implements CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    /** 收集回复子树时的最大层数（防御性上限，避免异常数据把循环拖死） */
    private static final int MAX_REPLY_DEPTH = 20;

    private final CommentMapper commentMapper;
    private final NoteMapper noteMapper;
    private final UserMapper userMapper;

    public CommentServiceImpl(CommentMapper commentMapper, NoteMapper noteMapper, UserMapper userMapper) {
        this.commentMapper = commentMapper;
        this.noteMapper = noteMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO createComment(Long noteId, CreateCommentRequest request) {
        Long me = UserContext.requireUserId();
        Note note = noteMapper.selectById(noteId);
        // 只有"公开且已通过审核"的笔记能评论：待审核、已驳回、已下架的笔记对普通用户相当于不存在
        if (note == null || Visibility.PUBLIC != note.getVisibility()
                || AuditStatus.APPROVED != note.getAuditStatus()) {
            throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
        }

        Comment comment = new Comment();
        comment.setNoteId(noteId);
        comment.setUserId(me);
        comment.setContent(request.getContent().trim());

        if (request.getParentId() != null) {
            Comment parent = commentMapper.selectById(request.getParentId());
            if (parent == null || !noteId.equals(parent.getNoteId())) {
                throw new BusinessException(ErrorCode.COMMENT_PARENT_INVALID);
            }
            comment.setParentId(parent.getId());
            comment.setRootId(parent.getRootId());
            commentMapper.insert(comment);
        } else {
            // 顶层评论：root_id 要等于自己的 id，只能先插入拿到自增主键，再回填
            comment.setRootId(0L);
            commentMapper.insert(comment);
            comment.setRootId(comment.getId());
            Comment fill = new Comment();
            fill.setId(comment.getId());
            fill.setRootId(comment.getId());
            commentMapper.updateById(fill);
        }

        log.info("用户[{}]在笔记[{}]发表评论[{}]{}", me, noteId, comment.getId(),
                comment.getParentId() == null ? "" : "（回复 " + comment.getParentId() + "）");

        // 回查一次：create_time 由数据库默认值生成，插入后的实体上没有它，
        // 直接组装会让响应里的 createTime 是 null（这条评论随后查询却有时间）
        Comment saved = commentMapper.selectById(comment.getId());
        BizAssert.notNull(saved, ErrorCode.COMMENT_NOT_FOUND);

        User user = userMapper.selectById(me);
        return CommentVO.of(saved, user, true);
    }

    @Override
    public PageResult<CommentVO> pageComments(Long noteId, PageQuery pageQuery) {
        Note note = noteMapper.selectById(noteId);
        if (!canReadComments(note)) {
            throw new BusinessException(ErrorCode.NOTE_NOT_FOUND);
        }

        // 第一页数据：只取顶层评论（parent_id 为空），按时间倒序（最新的评论在最上面）
        Page<Comment> page = commentMapper.selectPage(new Page<>(pageQuery.getPage(), pageQuery.getSize()),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getNoteId, noteId)
                        .isNull(Comment::getParentId)
                        .orderByDesc(Comment::getId));

        List<Comment> tops = page.getRecords();
        int current = (int) page.getCurrent();
        int size = (int) page.getSize();
        if (tops.isEmpty()) {
            return PageResult.empty(current, size);
        }

        // 第二页数据：这一页所有顶层评论的后代回复，一次查出来（按 id 升序，回复自然按时间正序）
        List<Long> rootIds = tops.stream().map(Comment::getId).toList();
        List<Comment> replies = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getNoteId, noteId)
                .in(Comment::getRootId, rootIds)
                .isNotNull(Comment::getParentId)
                .orderByAsc(Comment::getId));

        return PageResult.of(assemble(tops, replies), page.getTotal(), current, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long commentId) {
        Long me = UserContext.requireUserId();
        Comment comment = commentMapper.selectById(commentId);
        BizAssert.notNull(comment, ErrorCode.COMMENT_NOT_FOUND);

        if (!me.equals(comment.getUserId()) && !UserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.COMMENT_PERMISSION_DENIED);
        }

        int affected = deleteSubtreeOrFail(comment);
        log.info("用户[{}]删除评论[{}]（含子回复），影响 {} 条", me, commentId, affected);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCommentWithReplies(Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        BizAssert.notNull(comment, ErrorCode.COMMENT_NOT_FOUND);
        return deleteSubtreeOrFail(comment);
    }

    /**
     * 删除并要求"确实删到了行"。
     * <p>
     * 为什么必须检查影响行数：并发重复删除时，多个请求都可能先读到"评论还在"，
     * 但只有一个能真正把 deleted 置 1；不检查的话其余请求也会返回成功，
     * 而串行删除第二次却会返回 40001 —— 同一个操作两种结果。
     */
    private int deleteSubtreeOrFail(Comment comment) {
        int affected = deleteSubtree(comment);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        return affected;
    }

    /**
     * 逻辑删除一条评论及其后代回复（@TableLogic 会把 delete 翻译成 SET deleted = 1）。
     * <p>
     * 顶层评论：整棵树就是 root_id 等于它自己的那些，一条语句搞定。
     * 回复：只删它自己和它下面的回复 —— 这里**不能**按 root_id 删，
     * 否则会把顶层评论和同一棵树里的兄弟分支一起删掉。
     */
    private int deleteSubtree(Comment comment) {
        if (comment.getParentId() == null) {
            return commentMapper.delete(new LambdaQueryWrapper<Comment>()
                    .and(w -> w.eq(Comment::getId, comment.getId())
                            .or().eq(Comment::getRootId, comment.getRootId())));
        }

        List<Long> subtreeIds = collectSubtreeIds(comment.getId());
        return commentMapper.delete(new LambdaQueryWrapper<Comment>().in(Comment::getId, subtreeIds));
    }

    /**
     * 广度优先收集一条评论及其所有后代回复的 id。
     * 层数上限只是防御性的：正常讨论不会超过这个深度，避免异常数据导致无限循环。
     */
    private List<Long> collectSubtreeIds(Long commentId) {
        List<Long> all = new ArrayList<>();
        all.add(commentId);

        List<Long> frontier = List.of(commentId);
        for (int depth = 0; depth < MAX_REPLY_DEPTH && !frontier.isEmpty(); depth++) {
            List<Comment> children = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                    .select(Comment::getId)
                    .in(Comment::getParentId, frontier));
            if (children.isEmpty()) {
                break;
            }
            frontier = children.stream().map(Comment::getId).toList();
            all.addAll(frontier);
        }
        return all;
    }

    @Override
    public long countByNoteId(Long noteId) {
        Long count = commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getNoteId, noteId));
        return count == null ? 0L : count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByNoteId(Long noteId) {
        // 笔记被删除时调用：把这篇笔记下的评论全部逻辑删除，
        // 否则会留下"笔记已不存在但评论还在"的悬空数据（管理员评论列表里会出现书名空白的记录）
        return commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getNoteId, noteId));
    }

    /* ---------------- 内部方法 ---------------- */

    /**
     * 谁可以看评论区：与"笔记详情"的可见性规则保持一致 ——
     * 公开且已通过审核的笔记任何人（含未登录）都能看；
     * 其余情况（待审核 / 已驳回 / 已下架 / 私有）只有作者本人与管理员能看。
     * <p>
     * 这样作者在自己的笔记被下架后，详情页仍然是"正文看得到、评论区也看得到"，不会出现一半报错的矛盾。
     * 注意：发评论的门槛更严（只有公开且已通过的笔记能评论），下架后只是"能看不能再发"。
     */
    private boolean canReadComments(Note note) {
        if (note == null) {
            return false;
        }
        Long me = UserContext.getUserId();
        if ((me != null && me.equals(note.getUserId())) || UserContext.isAdmin()) {
            return true;
        }
        return Visibility.PUBLIC == note.getVisibility() && AuditStatus.APPROVED == note.getAuditStatus();
    }

    /**
     * 组装评论树：顶层评论按分页顺序排列，后代回复挂到各自的父评论下面。
     * 只查两条 SQL（顶层 + 全部回复），不在循环里查库。
     */
    private List<CommentVO> assemble(List<Comment> tops, List<Comment> replies) {
        List<Long> userIds = new ArrayList<>();
        tops.forEach(c -> userIds.add(c.getUserId()));
        replies.forEach(c -> userIds.add(c.getUserId()));
        Map<Long, User> users = loadUsers(userIds);

        Long me = UserContext.getUserId();
        boolean admin = UserContext.isAdmin();

        Map<Long, CommentVO> voById = new LinkedHashMap<>();
        for (Comment top : tops) {
            voById.put(top.getId(), CommentVO.of(top, users.get(top.getUserId()), canDelete(top, me, admin)));
        }

        // 回复按 id 升序进来，因此挂载后每层内部的顺序天然是时间正序
        Map<Long, CommentVO> replyIndex = new HashMap<>();
        for (Comment reply : replies) {
            CommentVO vo = CommentVO.of(reply, users.get(reply.getUserId()), canDelete(reply, me, admin));
            replyIndex.put(reply.getId(), vo);
        }
        for (Comment reply : replies) {
            CommentVO vo = replyIndex.get(reply.getId());
            CommentVO parent = replyIndex.get(reply.getParentId());
            if (parent == null) {
                // 父评论不在这一页时，挂到它所属的顶层评论下，保证回复不丢失
                parent = voById.get(reply.getRootId());
            }
            if (parent != null) {
                parent.getReplies().add(vo);
            }
        }

        return new ArrayList<>(voById.values());
    }

    private boolean canDelete(Comment comment, Long me, boolean admin) {
        return admin || (me != null && me.equals(comment.getUserId()));
    }

    private Map<Long, User> loadUsers(List<Long> userIds) {
        List<Long> distinct = userIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(distinct).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
