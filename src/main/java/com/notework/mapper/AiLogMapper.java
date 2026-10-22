package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.AiLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用日志数据访问层（单表 CRUD 由 MyBatis-Plus 提供）。
 */
@Mapper
public interface AiLogMapper extends BaseMapper<AiLog> {
}
