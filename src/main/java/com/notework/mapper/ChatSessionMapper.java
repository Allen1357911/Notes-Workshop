package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 客服会话数据访问层。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
