package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.Note;
import com.notework.mapper.row.NoteWithLikeRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 笔记数据访问层。
 * <p>
 * 单表 CRUD 由 MyBatis-Plus 提供；需要联表聚合的"按点赞数排行"手写在 NoteMapper.xml 里。
 */
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /**
     * 按点赞数排行（只统计公开笔记，否则匿名排行接口会把私有笔记暴露出去）。
     * 排序规则：点赞数降序，再按创建时间降序。
     *
     * @param offset 起始行号，从 0 开始
     * @param size   每页条数
     */
    List<NoteWithLikeRow> selectRankList(@Param("offset") int offset, @Param("size") int size);

    /** 排行总数（公开笔记数） */
    long countRankNotes();
}
