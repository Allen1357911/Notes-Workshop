package com.notework.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.response.OperationLogVO;
import com.notework.entity.OperationLogRecord;
import com.notework.mapper.OperationLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 操作日志服务单元测试：写入与查询。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogServiceImplTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    private OperationLogServiceImpl operationLogService;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, OperationLogRecord.class);
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        operationLogService = new OperationLogServiceImpl(operationLogMapper);
    }

    @Test
    @DisplayName("写入日志：字段原样落库")
    void recordsLog() {
        OperationLogRecord record = new OperationLogRecord();
        record.setModule("管理员-内容审核");
        record.setAction("审核笔记");
        record.setSuccess(1);

        operationLogService.record(record);

        ArgumentCaptor<OperationLogRecord> captor = ArgumentCaptor.forClass(OperationLogRecord.class);
        verify(operationLogMapper).insert(captor.capture());
        assertEquals("管理员-内容审核", captor.getValue().getModule());
        assertEquals("审核笔记", captor.getValue().getAction());
    }

    @Test
    @DisplayName("查询日志：分页返回，成功标志正确映射")
    void pagesLogs() {
        when(operationLogMapper.<Page<OperationLogRecord>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<OperationLogRecord> page = invocation.getArgument(0);
            page.setRecords(List.of(log(1L, 1), log(2L, 0)));
            page.setTotal(2);
            return page;
        });

        PageResult<OperationLogVO> result = operationLogService.pageLogs(null, null, null, new PageQuery());

        assertEquals(2L, result.getTotal());
        assertTrue(result.getList().get(0).isSuccess());
        assertFalse(result.getList().get(1).isSuccess());
    }

    @Test
    @DisplayName("查询日志：没有任何记录时返回空分页")
    void pagesLogsEmpty() {
        when(operationLogMapper.<Page<OperationLogRecord>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<OperationLogRecord> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        PageResult<OperationLogVO> result = operationLogService.pageLogs("认证", "alice", true, new PageQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    private OperationLogRecord log(Long id, int success) {
        OperationLogRecord record = new OperationLogRecord();
        record.setId(id);
        record.setModule("认证");
        record.setAction("登录");
        record.setSuccess(success);
        record.setCostMs(5L);
        return record;
    }
}
