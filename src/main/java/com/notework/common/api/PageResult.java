package com.notework.common.api;

import java.util.List;

/**
 * 统一分页返回结构。
 * <p>
 * 三个需要分页的列表（我的笔记、我点赞过的笔记、按点赞数排行）都用它，
 * 保证前端只需要写一套分页渲染逻辑。
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> {

    /** 当前页数据，永不为 null */
    private final List<T> list;
    /** 总条数 */
    private final long total;
    /** 当前页码，从 1 开始 */
    private final int page;
    /** 每页条数 */
    private final int size;

    private PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /** 组装分页结果；list 传 null 时按空列表处理 */
    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list == null ? List.of() : list, total, page, size);
    }

    /** 空结果 */
    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0L, page, size);
    }

    public List<T> getList() {
        return list;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
