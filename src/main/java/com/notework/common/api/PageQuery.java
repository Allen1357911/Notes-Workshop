package com.notework.common.api;

import com.notework.common.constant.CommonConstants;

/**
 * 分页查询入参。
 * <p>
 * page 从 1 开始。越界值（null、小于 1、超过上限）在这里统一纠偏，
 * 业务层不需要再判断分页参数是否合法。
 */
public class PageQuery {

    /** 页码，从 1 开始 */
    private Integer page;

    /** 每页条数 */
    private Integer size;

    /** 纠偏后的页码，永远不会小于 1 */
    public int getPage() {
        if (page == null || page < 1) {
            return CommonConstants.DEFAULT_PAGE;
        }
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    /** 纠偏后的每页条数，范围 [1, MAX_PAGE_SIZE] */
    public int getSize() {
        if (size == null || size < 1) {
            return CommonConstants.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, CommonConstants.MAX_PAGE_SIZE);
    }

    public void setSize(Integer size) {
        this.size = size;
    }
}
