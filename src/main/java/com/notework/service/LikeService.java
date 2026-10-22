package com.notework.service;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.response.NoteRankVO;

/**
 * 点赞业务接口。
 */
public interface LikeService {

    /**
     * 点赞公开笔记，返回最新点赞数。
     * 重复点赞按幂等处理（不会报错、也不会产生第二条记录）。
     */
    long like(Long noteId);

    /**
     * 取消点赞，返回最新点赞数。
     * 没点过赞也按幂等处理（不会报错）。
     */
    long unlike(Long noteId);

    /** 我点赞过的笔记，分页（按点赞时间倒序） */
    PageResult<NoteRankVO> pageMyLikedNotes(PageQuery pageQuery);

    /** 某篇笔记的点赞总数 */
    long countByNoteId(Long noteId);

    /** 某个用户是否点赞过某篇笔记 */
    boolean hasLiked(Long noteId, Long userId);

    /**
     * 删除某篇笔记的全部点赞记录。
     * 笔记被删除时由 NoteService 调用，避免留下指向已删笔记的孤儿点赞数据
     * （否则"我赞过的"总数会大于实际能查出来的列表条数）。
     */
    void deleteLikesByNoteId(Long noteId);
}
