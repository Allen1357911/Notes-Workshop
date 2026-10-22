package com.notework.service;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.request.CreateNoteRequest;
import com.notework.dto.request.UpdateNoteRequest;
import com.notework.dto.response.NoteListVO;
import com.notework.dto.response.NoteRankVO;
import com.notework.dto.response.NoteVO;

/**
 * 读书笔记业务接口。
 */
public interface NoteService {

    /** 新建笔记：必须指定图书名与可见性，可带 0~N 张配图 */
    NoteVO createNote(CreateNoteRequest request);

    /** 编辑笔记：仅作者本人或管理员 */
    NoteVO updateNote(Long noteId, UpdateNoteRequest request);

    /** 删除笔记：仅作者本人或管理员，配图记录一并删除 */
    void deleteNote(Long noteId);

    /** 我的全部笔记（私有 + 公开），分页 */
    PageResult<NoteListVO> pageMyNotes(PageQuery pageQuery);

    /** 公开笔记列表（所有人可见，含未登录访客），分页 */
    PageResult<NoteListVO> pagePublicNotes(PageQuery pageQuery);

    /**
     * 按点赞数排行，分页（可匿名）。
     * 只统计公开笔记：这个接口允许匿名访问，绝不能把私有笔记带出去。
     */
    PageResult<NoteRankVO> pageRankedNotes(PageQuery pageQuery);

    /** 笔记详情：公开笔记任何人可看；私有笔记仅作者本人或管理员 */
    NoteVO getNoteDetail(Long noteId);

    /**
     * 重新提交审核（扩展部分）：把被驳回的公开笔记重新送回待审核队列。
     * 作者不改内容也可以重提；编辑笔记本身也会自动回到待审核。
     */
    NoteVO resubmitForAudit(Long noteId);

    /** 某个作者已公开且通过审核的笔记列表，分页（可匿名，用于作者主页） */
    PageResult<NoteListVO> pageUserPublicNotes(Long authorId, PageQuery pageQuery);

    /** 关注流：我关注的人发布的公开且已通过的笔记，按发布时间倒序，分页 */
    PageResult<NoteListVO> pageFollowingFeed(PageQuery pageQuery);
}
