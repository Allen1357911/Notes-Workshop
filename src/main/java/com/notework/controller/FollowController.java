package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.api.Result;
import com.notework.dto.response.AuthorVO;
import com.notework.dto.response.FollowUserVO;
import com.notework.dto.response.HotAuthorVO;
import com.notework.dto.response.NoteListVO;
import com.notework.service.FollowService;
import com.notework.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区互动：关注、粉丝、作者主页、关注流与热门作者。
 * <p>
 * 路径没有统一的类级前缀（既有 /api/users/** 也有 /api/authors/** 与 /api/feed/**），
 * 因此直接写在方法上。
 */
@Tag(name = "社区互动-关注与作者", description = "关注/取关、粉丝与关注列表、作者主页、关注流、热门作者排行")
@RestController
public class FollowController {

    private final FollowService followService;
    private final NoteService noteService;

    public FollowController(FollowService followService, NoteService noteService) {
        this.followService = followService;
        this.noteService = noteService;
    }

    @Operation(summary = "关注用户", description = "重复关注按幂等处理；不能关注自己（返回 50001）；未登录返回 401。")
    @OperationLog(module = "社区互动", action = "关注用户", description = "关注某个用户（重复关注也留痕）")
    @PostMapping("/api/users/{id}/follow")
    public Result<Void> follow(@PathVariable("id") Long id) {
        followService.follow(id);
        return Result.success();
    }

    @Operation(summary = "取消关注", description = "没关注过时同样返回成功，保证幂等。")
    @OperationLog(module = "社区互动", action = "取消关注", description = "取消关注某个用户")
    @DeleteMapping("/api/users/{id}/follow")
    public Result<Void> unfollow(@PathVariable("id") Long id) {
        followService.unfollow(id);
        return Result.success();
    }

    @Operation(summary = "作者主页信息",
            description = "返回昵称、头像、粉丝数、关注数、公开笔记数，以及当前用户是否已关注（可匿名访问）。")
    @GetMapping("/api/users/{id}")
    public Result<AuthorVO> author(@PathVariable("id") Long id) {
        return Result.success(followService.getAuthor(id));
    }

    @Operation(summary = "作者的公开笔记", description = "只返回该作者「公开且已通过审核」的笔记，可匿名访问。")
    @GetMapping("/api/users/{id}/notes")
    public Result<PageResult<NoteListVO>> authorNotes(@PathVariable("id") Long id, PageQuery pageQuery) {
        return Result.success(noteService.pageUserPublicNotes(id, pageQuery));
    }

    @Operation(summary = "粉丝列表", description = "谁关注了他，按关注时间倒序分页，可匿名访问。")
    @GetMapping("/api/users/{id}/followers")
    public Result<PageResult<FollowUserVO>> followers(@PathVariable("id") Long id, PageQuery pageQuery) {
        return Result.success(followService.pageFollowers(id, pageQuery));
    }

    @Operation(summary = "关注列表", description = "他关注了谁，按关注时间倒序分页，可匿名访问。")
    @GetMapping("/api/users/{id}/following")
    public Result<PageResult<FollowUserVO>> following(@PathVariable("id") Long id, PageQuery pageQuery) {
        return Result.success(followService.pageFollowing(id, pageQuery));
    }

    @Operation(summary = "热门作者排行",
            description = "按粉丝数从多到少，粉丝数相同时按用户 id 升序（保证分页稳定），可匿名访问。")
    @GetMapping("/api/authors/hot")
    public Result<PageResult<HotAuthorVO>> hotAuthors(PageQuery pageQuery) {
        return Result.success(followService.pageHotAuthors(pageQuery));
    }

    @Operation(summary = "关注流",
            description = "我关注的人发布的「公开且已通过审核」的笔记，按发布时间倒序分页；未登录返回 401。")
    @GetMapping("/api/feed/following")
    public Result<PageResult<NoteListVO>> followingFeed(PageQuery pageQuery) {
        return Result.success(noteService.pageFollowingFeed(pageQuery));
    }
}
