package com.notework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.notework.entity.OperationLogRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志数据访问层（单表 CRUD，全部由 MyBatis-Plus 提供）。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogRecord> {
}
