package com.notework.controller;

import com.notework.common.annotation.OperationLog;
import com.notework.common.api.Result;
import com.notework.dto.request.LoginRequest;
import com.notework.dto.request.RegisterRequest;
import com.notework.dto.response.LoginVO;
import com.notework.dto.response.UserVO;
import com.notework.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册与登录。这两个接口无需登录即可访问。
 * <p>
 * Controller 只做参数接收与校验、调用 Service、包装统一响应，不写业务规则。
 */
@Tag(name = "认证接口", description = "注册与登录，无需令牌即可访问")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "注册",
            description = "用户名 4~20 位字母数字下划线，密码 6~20 位；密码以 BCrypt 哈希存储。用户名重复返回 10002。")
    @SecurityRequirements
    @OperationLog(module = "认证", action = "注册", description = "新用户注册（密码不落日志）")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(userService.register(request));
    }

    @Operation(summary = "登录",
            description = "校验通过后返回 JWT 令牌，前端后续请求放在请求头 Authorization: Bearer {token}。用户名或密码错误统一返回 10003。")
    @SecurityRequirements
    @OperationLog(module = "认证", action = "登录", description = "登录尝试（含失败尝试，密码字段已打码）")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }
}
