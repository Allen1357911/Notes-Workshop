package com.notework.service;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.response.OperationLogVO;
import com.notework.entity.OperationLogRecord;

/**
 * 操作日志服务（性能与健壮性模块）。
 */
public interface OperationLogService {

    /**
     * 写入一条操作日志。
     * <p>
     * 用独立事务（REQUIRES_NEW）：业务事务回滚时日志仍要保留（失败也要留痕）；
     * 调用方（切面）还要再兜一层 try-catch —— 记日志失败绝不能影响业务。
     */
    void record(OperationLogRecord record);

    /** 管理员查询操作日志（分页），可按模块、操作人、成功与否过滤 */
    PageResult<OperationLogVO> pageLogs(String module, String username, Boolean success, PageQuery pageQuery);
}
