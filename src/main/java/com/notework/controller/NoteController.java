package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.api.Result;
import com.notework.dto.request.CreateNoteRequest;
import com.notework.dto.request.UpdateNoteRequest;
import com.notework.dto.response.NoteListVO;
import com.notework.dto.response.NoteRankVO;
import com.notework.dto.response.NoteVO;
import com.notework.service.NoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 读书笔记接口。
 * <p>
 * 其中公开列表、排行与详情允许匿名访问（详情对私有笔记会在 Service 层拒绝），
 * 具体放行规则见 JwtAuthInterceptor。
 */
@Tag(name = "读书笔记接口", description = "笔记的增删改查、公开/私有、配图与分页；公开内容允许匿名浏览")
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @Operation(summary = "新建笔记",
            description = "必须指定图书名与可见性（PRIVATE 私有 / PUBLIC 公开）；配图先调上传接口拿 URL 再放进 imageUrls，最多 9 张。")
    @OperationLog(module = "读书笔记", action = "新建笔记", description = "新建笔记（公开的会进入待审核）")
    @PostMapping
    public Result<NoteVO> create(@Valid @RequestBody CreateNoteRequest request) {
        return Result.success(noteService.createNote(request));
    }

    @Operation(summary = "我的笔记列表（分页）",
            description = "返回当前登录用户的全部笔记，包含私有与公开；page 从 1 开始，size 上限 100。")
    @GetMapping("/my")
    public Result<PageResult<NoteListVO>> myNotes(PageQuery pageQuery) {
        return Result.success(noteService.pageMyNotes(pageQuery));
    }

    @Operation(summary = "公开笔记列表（分页）", description = "只返回 PUBLIC 的笔记，未登录也可以访问。")
    @SecurityRequirements
    @GetMapping("/public")
    public Result<PageResult<NoteListVO>> publicNotes(PageQuery pageQuery) {
        return Result.success(noteService.pagePublicNotes(pageQuery));
    }

    @Operation(summary = "按点赞数排行（分页）",
            description = "只统计公开笔记，按点赞数降序、创建时间降序排列；未登录也可以访问。")
    @SecurityRequirements
    @GetMapping("/rank")
    public Result<PageResult<NoteRankVO>> rank(PageQuery pageQuery) {
        return Result.success(noteService.pageRankedNotes(pageQuery));
    }

    @Operation(summary = "笔记详情",
            description = "返回正文、图书名、全部配图、AI 辅助结果（基础阶段为空）、点赞数与当前用户是否已赞。"
                    + "公开笔记未登录也能看；私有笔记仅作者本人或管理员可看，其他人返回 20004。"
                    + "注意：带不带令牌结果不同（私有可能看不到、liked 与 editable 会变），所以该接口不标注为匿名，"
                    + "在 Swagger 里点过 Authorize 之后会自动带上令牌。")
    @GetMapping("/{id}")
    public Result<NoteVO> detail(@PathVariable("id") Long id) {
        return Result.success(noteService.getNoteDetail(id));
    }

    @Operation(summary = "编辑笔记",
            description = "仅作者本人或管理员，其他人返回 20002。imageUrls 传 null 表示不改配图，传空数组表示清空。")
    @OperationLog(module = "读书笔记", action = "编辑笔记", description = "编辑笔记（公开的会重新进入待审核）")
    @PutMapping("/{id}")
    public Result<NoteVO> update(@PathVariable("id") Long id,
                                 @Valid @RequestBody UpdateNoteRequest request) {
        return Result.success(noteService.updateNote(id, request));
    }

    @Operation(summary = "重新提交审核",
            description = "把「已驳回」的公开笔记重新送回待审核队列（不用改内容）。"
                    + "只有作者本人或管理员可操作；私有笔记或非已驳回状态返回 20006。")
    @OperationLog(module = "笔记审核", action = "重新提交审核", description = "作者把被驳回的笔记重新提交")
    @PutMapping("/{id}/resubmit")
    public Result<NoteVO> resubmit(@PathVariable("id") Long id) {
        return Result.success(noteService.resubmitForAudit(id));
    }

    @Operation(summary = "删除笔记",
            description = "仅作者本人或管理员。笔记、配图记录、点赞记录一并删除；磁盘上的图片文件暂不清理。")
    @OperationLog(module = "读书笔记", action = "删除笔记", description = "删除笔记及其配图、点赞与评论")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        noteService.deleteNote(id);
        return Result.success();
    }
}
