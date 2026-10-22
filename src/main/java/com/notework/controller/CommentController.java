package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.api.Result;
import com.notework.dto.request.CreateCommentRequest;
import com.notework.dto.response.CommentVO;
import com.notework.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区互动：评论与多级回复。
 */
@Tag(name = "社区互动-评论", description = "发表评论与多级回复、查看评论树、删除自己的评论")
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(summary = "发表评论 / 回复",
            description = "parentId 不传表示对笔记发表顶层评论，传了表示回复某条评论（支持多级回复）。"
                    + "只能评论「公开且已通过审核」的笔记，其他情况返回 20001。")
    @OperationLog(module = "社区互动", action = "发表评论", description = "发表评论或回复")
    @PostMapping("/api/notes/{noteId}/comments")
    public Result<CommentVO> create(@PathVariable("noteId") Long noteId,
                                    @Valid @RequestBody CreateCommentRequest request) {
        return Result.success(commentService.createComment(noteId, request));
    }

    @Operation(summary = "评论列表（含多级回复）",
            description = "顶层评论倒序分页，每条评论的所有回复随它一起返回（前端按层级缩进展示），可匿名访问。")
    @GetMapping("/api/notes/{noteId}/comments")
    public Result<PageResult<CommentVO>> list(@PathVariable("noteId") Long noteId, PageQuery pageQuery) {
        return Result.success(commentService.pageComments(noteId, pageQuery));
    }

    @Operation(summary = "删除评论",
            description = "只能删自己的评论（管理员可删任意评论，否则返回 40002）；逻辑删除，"
                    + "删父评论时连同它下面的所有回复一起删除。")
    @OperationLog(module = "社区互动", action = "删除评论", description = "删除自己的评论（连同子回复）")
    @DeleteMapping("/api/comments/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        commentService.deleteComment(id);
        return Result.success();
    }
}
