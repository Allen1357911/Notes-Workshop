package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.api.Result;
import com.notework.dto.request.UpdateUserStatusRequest;
import com.notework.dto.response.AdminUserVO;
import com.notework.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员后台：用户管理。
 * <p>
 * /api/admin/** 由 AdminAuthInterceptor 统一校验管理员身份，未登录 401、非管理员 403。
 */
@Tag(name = "管理员-用户管理", description = "查看所有用户、禁用/启用账号，需要管理员权限")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @Operation(summary = "用户列表（分页）",
            description = "keyword 可按用户名或昵称模糊搜索；status 传 1 只看正常、传 0 只看已禁用、不传看全部。")
    @GetMapping
    public Result<PageResult<AdminUserVO>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                               @RequestParam(value = "status", required = false) Integer status,
                                               PageQuery pageQuery) {
        return Result.success(adminUserService.pageUsers(keyword, status, pageQuery));
    }

    @Operation(summary = "禁用 / 启用账号",
            description = "status 传 0 表示禁用、1 表示启用。不能禁用自己的账号（返回 10006）；"
                    + "禁用立即生效，该用户手里已签发的令牌也会被拦下。")
    @OperationLog(module = "管理员-用户管理", action = "修改账号状态", description = "禁用或启用某个用户账号")
    @PutMapping("/{id}/status")
    public Result<AdminUserVO> updateStatus(@PathVariable("id") Long id,
                                           @Valid @RequestBody UpdateUserStatusRequest request) {
        return Result.success(adminUserService.updateStatus(id, request.getStatus()));
    }
}
