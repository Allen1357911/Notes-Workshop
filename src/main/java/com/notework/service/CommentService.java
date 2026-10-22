package com.notework.service;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.request.CreateCommentRequest;
import com.notework.dto.response.CommentVO;

/**
 * 社区互动：评论与多级回复。
 */
public interface CommentService {

    /** 发表评论（parentId 为空）或回复某条评论（parentId 不为空） */
    CommentVO createComment(Long noteId, CreateCommentRequest request);

    /** 评论列表：顶层评论分页，每条评论的所有回复随它一起返回 */
    PageResult<CommentVO> pageComments(Long noteId, PageQuery pageQuery);

    /** 删除自己的评论（逻辑删除，并连同其下的回复一起删除）；管理员也可以删 */
    void deleteComment(Long commentId);

    /**
     * 逻辑删除一条评论及其所有后代回复（不做权限判断，调用方自己校验）。
     * <p>
     * 语义要点：删顶层评论 = 整棵评论树；删一条回复 = 只有它自己与它下面的回复，
     * 不动父评论，也不动同一层的兄弟分支。返回受影响条数。
     */
    int deleteCommentWithReplies(Long commentId);

    /** 某篇笔记的评论总数（只算未被删除的） */
    long countByNoteId(Long noteId);

    /** 笔记被删除时清理它下面的全部评论（逻辑删除），返回受影响条数 */
    int deleteByNoteId(Long noteId);
}
