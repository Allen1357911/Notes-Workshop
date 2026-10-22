package com.notework.service;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.AuditStatus;
import com.notework.common.enums.Visibility;
import com.notework.dto.request.AuditNoteRequest;
import com.notework.dto.response.AdminNoteVO;

/**
 * 管理员后台：内容管理与公开笔记审核。
 */
public interface AdminNoteService {

    /** 全部笔记列表（分页），可按可见性与审核状态过滤 */
    PageResult<AdminNoteVO> pageNotes(Visibility visibility, AuditStatus auditStatus, PageQuery pageQuery);

    /** 待审核队列（分页） */
    PageResult<AdminNoteVO> pagePendingNotes(PageQuery pageQuery);

    /** 审核：通过或驳回（驳回可带原因），只有待审核状态的笔记能审核 */
    AdminNoteVO audit(Long noteId, AuditNoteRequest request);

    /** 下架违规的已公开笔记 */
    AdminNoteVO offline(Long noteId, String remark);

    /** 恢复被下架的笔记（重新对外可见） */
    AdminNoteVO restore(Long noteId);
}
