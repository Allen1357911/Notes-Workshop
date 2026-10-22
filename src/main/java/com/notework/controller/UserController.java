package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.Result;
import com.notework.dto.request.ChangePasswordRequest;
import com.notework.dto.request.UpdateProfileRequest;
import com.notework.dto.response.UserVO;
import com.notework.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人资料接口，全部需要登录（拦截器统一校验）。
 */
@Tag(name = "用户资料接口", description = "查看与修改个人资料、改密码、上传头像，全部需要登录")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "查看当前登录用户资料",
            description = "返回用户名、昵称、头像 URL、角色等；任何接口都不会返回密码字段。")
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.success(userService.getCurrentUser());
    }

    @Operation(summary = "修改个人资料", description = "基础阶段只支持修改昵称，最长 20 个字符。")
    @OperationLog(module = "用户", action = "修改资料", description = "用户修改自己的昵称")
    @PutMapping("/me")
    public Result<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(userService.updateProfile(request));
    }

    @Operation(summary = "修改密码",
            description = "必须提供原密码，原密码错误返回 10004。改密成功后已签发的旧令牌不会立即失效。")
    @OperationLog(module = "用户", action = "修改密码", description = "参数摘要里的密码字段已打码")
    @PutMapping("/me/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return Result.success();
    }

    @Operation(summary = "上传头像",
            description = "multipart/form-data，字段名 file；只允许 jpg/jpeg/png/gif/webp，单张不超过 5MB。"
                    + "图片存本地磁盘，数据库只保存访问 URL。")
    @PostMapping("/me/avatar")
    public Result<UserVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.success(userService.updateAvatar(file));
    }
}
