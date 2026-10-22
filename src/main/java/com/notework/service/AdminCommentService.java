package com.notework.service;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.response.AdminCommentVO;

/**
 * 管理员后台：违规评论管理。
 */
public interface AdminCommentService {

    /** 评论列表（分页），可按笔记过滤；只列出未被删除的评论 */
    PageResult<AdminCommentVO> pageComments(Long noteId, PageQuery pageQuery);

    /** 删除违规评论：逻辑删除，并连同其下的所有子评论一起删除 */
    void deleteComment(Long commentId);
}
