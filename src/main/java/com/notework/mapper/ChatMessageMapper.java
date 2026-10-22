package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 客服消息数据访问层。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
