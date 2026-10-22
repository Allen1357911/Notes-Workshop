package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论数据访问层，只做数据库 CRUD。
 * <p>
 * 逻辑删除由实体上的 @TableLogic 处理：查询自动过滤已删除的评论。
 * 管理员需要看"包含已删除"的列表时，用下面的自定义查询绕开逻辑删除过滤。
 */
@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
