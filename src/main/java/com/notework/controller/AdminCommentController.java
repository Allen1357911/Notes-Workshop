package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.api.Result;
import com.notework.dto.response.AdminCommentVO;
import com.notework.service.AdminCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员后台：违规评论管理。
 */
@Tag(name = "管理员-评论管理", description = "查看评论、删除违规评论（逻辑删除并级联子评论），需要管理员权限")
@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

    private final AdminCommentService adminCommentService;

    public AdminCommentController(AdminCommentService adminCommentService) {
        this.adminCommentService = adminCommentService;
    }

    @Operation(summary = "评论列表（分页）",
            description = "可按 noteId 过滤某篇笔记的评论；只列出未被删除的评论，附带书名与评论人昵称，便于判定违规。")
    @GetMapping
    public Result<PageResult<AdminCommentVO>> list(@RequestParam(value = "noteId", required = false) Long noteId,
                                                   PageQuery pageQuery) {
        return Result.success(adminCommentService.pageComments(noteId, pageQuery));
    }

    @Operation(summary = "删除违规评论",
            description = "逻辑删除（数据库里保留记录，只是标记为已删除）；删除父评论时会连同它下面的所有回复一起删除。")
    @OperationLog(module = "管理员-评论管理", action = "删除违规评论", description = "逻辑删除违规评论及其子回复")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        adminCommentService.deleteComment(id);
        return Result.success();
    }
}
