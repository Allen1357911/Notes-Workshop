package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.response.OperationLogVO;
import com.notework.entity.OperationLogRecord;
import com.notework.mapper.OperationLogMapper;
import com.notework.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 操作日志实现。
 * <p>
 * 只做两件事：写入与查询。写入走独立事务，保证"业务失败也要留痕"。
 */
@Service
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogServiceImpl(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(OperationLogRecord record) {
        operationLogMapper.insert(record);
    }

    @Override
    public PageResult<OperationLogVO> pageLogs(String module, String username, Boolean success, PageQuery pageQuery) {
        LambdaQueryWrapper<OperationLogRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(module)) {
            wrapper.eq(OperationLogRecord::getModule, module.trim());
        }
        if (StringUtils.hasText(username)) {
            wrapper.like(OperationLogRecord::getUsername, username.trim());
        }
        if (success != null) {
            wrapper.eq(OperationLogRecord::getSuccess, success ? 1 : 0);
        }
        wrapper.orderByDesc(OperationLogRecord::getId);

        Page<OperationLogRecord> page = operationLogMapper.selectPage(
                new Page<>(pageQuery.getPage(), pageQuery.getSize()), wrapper);
        List<OperationLogVO> list = page.getRecords().stream().map(OperationLogVO::from).toList();
        int current = (int) page.getCurrent();
        int size = (int) page.getSize();
        if (list.isEmpty()) {
            return PageResult.empty(current, size);
        }
        return PageResult.of(list, page.getTotal(), current, size);
    }
}
