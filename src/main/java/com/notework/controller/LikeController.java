package com.notework.controller;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.api.Result;
import com.notework.dto.response.NoteRankVO;
import com.notework.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞接口。
 * <p>
 * 点赞/取消都返回该笔记最新的点赞数，前端可以直接刷新显示，不用再查一次。
 */
@Tag(name = "点赞接口", description = "公开笔记的点赞与取消，以及我点赞过的笔记")
@RestController
@RequestMapping("/api")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @Operation(summary = "点赞笔记",
            description = "只能点赞公开笔记，私有笔记返回 20003；重复点赞按幂等处理。返回该笔记最新点赞数。")
    @PostMapping("/notes/{id}/like")
    public Result<Long> like(@PathVariable("id") Long id) {
        return Result.success(likeService.like(id));
    }

    @Operation(summary = "取消点赞", description = "没点过赞也按幂等处理。返回该笔记最新点赞数。")
    @DeleteMapping("/notes/{id}/like")
    public Result<Long> unlike(@PathVariable("id") Long id) {
        return Result.success(likeService.unlike(id));
    }

    @Operation(summary = "我点赞过的笔记（分页）", description = "按点赞时间倒序，page 从 1 开始，size 上限 100。")
    @GetMapping("/likes/my")
    public Result<PageResult<NoteRankVO>> myLikes(PageQuery pageQuery) {
        return Result.success(likeService.pageMyLikedNotes(pageQuery));
    }
}
