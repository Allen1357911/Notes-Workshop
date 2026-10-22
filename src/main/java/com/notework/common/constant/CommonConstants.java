package com.notework.common.constant;

/**
 * 全局通用常量。
 * <p>
 * 只放跨模块共用的常量；某个模块专属的常量不要放这里。
 */
public final class CommonConstants {

    private CommonConstants() {
    }

    /** 认证请求头名称 */
    public static final String TOKEN_HEADER = "Authorization";

    /** 认证请求头前缀，注意结尾有一个空格 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 默认页码，从 1 开始 */
    public static final int DEFAULT_PAGE = 1;

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /** 每页最大条数，防止前端传入过大的 size */
    public static final int MAX_PAGE_SIZE = 100;
}
