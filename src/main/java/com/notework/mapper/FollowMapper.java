package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.Follow;
import com.notework.mapper.row.HotAuthorRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 关注关系数据访问层。
 * <p>
 * 单表 CRUD 由 MyBatis-Plus 提供；热门作者排行需要聚合，手写在 FollowMapper.xml 里。
 */
@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    /**
     * 热门作者排行：按粉丝数从多到少，粉丝数相同时按用户 id 升序（保证分页稳定）。
     *
     * @param offset 起始行号，从 0 开始
     * @param size   每页条数
     */
    List<HotAuthorRow> selectHotAuthors(@Param("offset") int offset, @Param("size") int size);

    /** 有粉丝的作者总数（用于分页总数） */
    long countHotAuthors();
}
