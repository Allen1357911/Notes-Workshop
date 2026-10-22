package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.api.Result;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Visibility;
import com.notework.dto.request.AuditNoteRequest;
import com.notework.dto.request.OfflineNoteRequest;
import com.notework.dto.response.AdminNoteVO;
import com.notework.service.AdminNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员后台：内容管理与公开笔记审核。
 */
@Tag(name = "管理员-内容管理与审核", description = "查看全部笔记、审核公开笔记、下架与恢复违规内容，需要管理员权限")
@RestController
@RequestMapping("/api/admin/notes")
public class AdminNoteController {

    private final AdminNoteService adminNoteService;

    public AdminNoteController(AdminNoteService adminNoteService) {
        this.adminNoteService = adminNoteService;
    }

    @Operation(summary = "笔记列表（分页）",
            description = "visibility 传 PRIVATE/PUBLIC 过滤，auditStatus 传 PENDING/APPROVED/REJECTED/TAKEN_DOWN 过滤。")
    @GetMapping
    public Result<PageResult<AdminNoteVO>> list(@RequestParam(value = "visibility", required = false) Visibility visibility,
                                                @RequestParam(value = "auditStatus", required = false) AuditStatus auditStatus,
                                                PageQuery pageQuery) {
        return Result.success(adminNoteService.pageNotes(visibility, auditStatus, pageQuery));
    }

    @Operation(summary = "待审核队列（分页）",
            description = "按提交时间从早到晚排列（先提交先审）。用户提交的公开笔记不会直接对外可见，先进入这里。")
    @GetMapping("/pending")
    public Result<PageResult<AdminNoteVO>> pending(PageQuery pageQuery) {
        return Result.success(adminNoteService.pagePendingNotes(pageQuery));
    }

    @Operation(summary = "审核笔记",
            description = "approved 传 true 通过（立即出现在公开广场），传 false 驳回（可带 remark 作为驳回原因，作者可见并可修改后重新提交）。"
                    + "只有待审核状态的笔记能审核，否则返回 20005。")
    @OperationLog(module = "管理员-内容审核", action = "审核笔记", description = "通过或驳回公开笔记")
    @PutMapping("/{id}/audit")
    public Result<AdminNoteVO> audit(@PathVariable("id") Long id,
                                     @Valid @RequestBody AuditNoteRequest request) {
        return Result.success(adminNoteService.audit(id, request));
    }

    @Operation(summary = "下架违规笔记",
            description = "只有已通过审核的公开笔记能下架；下架后立即从公开列表、排行与详情中消失。remark 为下架说明，作者可见。")
    @OperationLog(module = "管理员-内容审核", action = "下架笔记", description = "下架违规内容")
    @PutMapping("/{id}/offline")
    public Result<AdminNoteVO> offline(@PathVariable("id") Long id,
                                       @Valid @RequestBody(required = false) OfflineNoteRequest request) {
        String remark = request == null ? null : request.getRemark();
        return Result.success(adminNoteService.offline(id, remark));
    }

    @Operation(summary = "恢复已下架的笔记", description = "把已下架的笔记恢复为已通过状态，重新对外可见。")
    @OperationLog(module = "管理员-内容审核", action = "恢复笔记", description = "恢复已下架内容")
    @PutMapping("/{id}/restore")
    public Result<AdminNoteVO> restore(@PathVariable("id") Long id) {
        return Result.success(adminNoteService.restore(id));
    }
}
