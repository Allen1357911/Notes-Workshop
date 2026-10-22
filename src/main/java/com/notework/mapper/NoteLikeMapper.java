package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.NoteLike;
import com.notework.mapper.row.NoteWithLikeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 点赞数据访问层。
 * <p>
 * 单表 CRUD 由 MyBatis-Plus 提供；"我点赞过的笔记"需要联表，手写在 NoteLikeMapper.xml 里。
 */
@Mapper
public interface NoteLikeMapper extends BaseMapper<NoteLike> {

    /**
     * 我点赞过的笔记（按点赞时间倒序）。
     *
     * @param userId 当前用户 id
     * @param offset 起始行号，从 0 开始
     * @param size   每页条数
     */
    List<NoteWithLikeRow> selectLikedNoteList(@Param("userId") Long userId,
                                             @Param("offset") int offset,
                                             @Param("size") int size);

    /** 我点赞过的笔记总数 */
    long countLikedNotes(@Param("userId") Long userId);
}
