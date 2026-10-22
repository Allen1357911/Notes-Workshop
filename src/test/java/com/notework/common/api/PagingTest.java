package com.notework.common.api;

import com.notework.common.constant.CommonConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagingTest {

    @Test
    @DisplayName("未传分页参数时使用默认值")
    void defaultWhenNotProvided() {
        PageQuery query = new PageQuery();
        assertEquals(CommonConstants.DEFAULT_PAGE, query.getPage());
        assertEquals(CommonConstants.DEFAULT_PAGE_SIZE, query.getSize());
    }

    @Test
    @DisplayName("非法页码与条数被自动纠偏")
    void outOfRangeValuesAreFixed() {
        PageQuery query = new PageQuery();
        query.setPage(0);
        query.setSize(-5);
        assertEquals(1, query.getPage());
        assertEquals(10, query.getSize());

        query.setPage(-1);
        query.setSize(null);
        assertEquals(1, query.getPage());
        assertEquals(10, query.getSize());
    }

    @Test
    @DisplayName("超出上限的 size 被压到最大值")
    void sizeIsCapped() {
        PageQuery query = new PageQuery();
        query.setSize(1000);
        assertEquals(CommonConstants.MAX_PAGE_SIZE, query.getSize());
    }

    @Test
    @DisplayName("合法值原样返回")
    void normalValuesPassThrough() {
        PageQuery query = new PageQuery();
        query.setPage(3);
        query.setSize(20);
        assertEquals(3, query.getPage());
        assertEquals(20, query.getSize());
    }

    @Test
    @DisplayName("PageResult 组装分页数据")
    void pageResultAssembly() {
        PageResult<String> result = PageResult.of(List.of("a", "b"), 25L, 2, 10);
        assertEquals(2, result.getList().size());
        assertEquals(25L, result.getTotal());
        assertEquals(2, result.getPage());
        assertEquals(10, result.getSize());
    }

    @Test
    @DisplayName("PageResult 的 list 永远不为 null")
    void pageResultListNeverNull() {
        assertTrue(PageResult.of(null, 0L, 1, 10).getList().isEmpty());
        assertTrue(PageResult.empty(1, 10).getList().isEmpty());
        assertEquals(0L, PageResult.empty(1, 10).getTotal());
    }
}
