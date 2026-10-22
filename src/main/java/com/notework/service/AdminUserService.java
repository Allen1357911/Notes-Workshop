package com.notework.service;

import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.dto.response.AdminUserVO;

/**
 * 管理员后台：用户管理。
 */
public interface AdminUserService {

    /**
     * 查看所有用户列表（分页）。
     *
     * @param keyword 用户名或昵称的模糊匹配，可为空
     * @param status  1 正常 / 0 禁用，可为空表示不过滤
     */
    PageResult<AdminUserVO> pageUsers(String keyword, Integer status, PageQuery pageQuery);

    /** 禁用（status=0）或启用（status=1）用户账号 */
    AdminUserVO updateStatus(Long userId, Integer status);
}
