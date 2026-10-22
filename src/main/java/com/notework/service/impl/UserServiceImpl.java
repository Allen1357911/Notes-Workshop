package com.notework.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.notework.common.enums.Role;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.common.util.BizAssert;
import com.notework.dto.request.ChangePasswordRequest;
import com.notework.dto.request.LoginRequest;
import com.notework.dto.request.RegisterRequest;
import com.notework.dto.request.UpdateProfileRequest;
import com.notework.dto.response.LoginVO;
import com.notework.dto.response.UserVO;
import com.notework.entity.User;
import com.notework.interceptor.UserContext;
import com.notework.mapper.UserMapper;
import com.notework.service.UserService;
import com.notework.util.FileUtil;
import com.notework.util.JwtUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户业务实现。
 * <p>
 * 业务规则、权限判断、事务边界都在这里；Mapper 只负责数据库读写。
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FileUtil fileUtil;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil, FileUtil fileUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.fileUtil = fileUtil;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO register(RegisterRequest request) {
        // 先查一次给出友好提示；同时靠数据库唯一索引兜住并发注册
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }

        User user = new User();
        user.setUsername(request.getUsername());
        // 密码只以 BCrypt 哈希形式落库，任何地方都不保存明文
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname().trim()
                : request.getUsername());
        user.setRole(Role.USER);
        user.setStatus(User.STATUS_NORMAL);

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        return UserVO.from(userMapper.selectById(user.getId()));
    }

    @Override
    public LoginVO login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        // 用户不存在与密码错误返回同一个提示，避免暴露"这个用户名是否存在"
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        // 扩展部分：被管理员禁用的账号不能登录（密码校验通过之后才提示，避免暴露账号是否存在）
        if (!User.STATUS_NORMAL.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        return new LoginVO(token, jwtUtil.getExpireSeconds(), UserVO.from(user));
    }

    @Override
    public UserVO getCurrentUser() {
        return UserVO.from(loadCurrentUser());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateProfile(UpdateProfileRequest request) {
        User current = loadCurrentUser();

        User update = new User();
        update.setId(current.getId());
        update.setNickname(request.getNickname().trim());
        userMapper.updateById(update);

        return UserVO.from(userMapper.selectById(current.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        User current = loadCurrentUser();

        if (!passwordEncoder.matches(request.getOldPassword(), current.getPassword())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }

        User update = new User();
        update.setId(current.getId());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(update);
        // 说明：已签发的旧令牌不因此失效（纯 JWT 无状态），这是基础阶段的既定取舍
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserVO updateAvatar(MultipartFile file) {
        User current = loadCurrentUser();

        // 文件先落盘拿到 URL，数据库只存这个 URL
        String avatarUrl = fileUtil.saveImage(file, "avatar");

        User update = new User();
        update.setId(current.getId());
        update.setAvatarUrl(avatarUrl);
        userMapper.updateById(update);

        return UserVO.from(userMapper.selectById(current.getId()));
    }

    /** 取当前登录用户；登录态由拦截器保证，这里再兜一层"用户是否还存在" */
    private User loadCurrentUser() {
        Long userId = UserContext.requireUserId();
        User user = userMapper.selectById(userId);
        BizAssert.notNull(user, ErrorCode.USER_NOT_FOUND);
        return user;
    }
}
