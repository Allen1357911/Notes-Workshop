package com.notework.service.impl;

import com.notework.common.enums.Role;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.config.UploadProperties;
import com.notework.dto.request.ChangePasswordRequest;
import com.notework.dto.request.LoginRequest;
import com.notework.dto.request.RegisterRequest;
import com.notework.dto.request.UpdateProfileRequest;
import com.notework.dto.response.LoginVO;
import com.notework.dto.response.UserVO;
import com.notework.entity.User;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.UserMapper;
import com.notework.util.FileUtil;
import com.notework.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户业务层单元测试。
 * <p>
 * Mapper 用 Mockito 替身，密码与 JWT 用真实实现（这两处正是要重点验证的），
 * 所以整个测试不依赖数据库，也不需要启动 Spring 容器。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final String SECRET = "service-test-secret-key-at-least-32-bytes!!!";

    @Mock
    private UserMapper userMapper;

    @TempDir
    Path tempDir;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private JwtUtil jwtUtil;
    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        UploadProperties uploadProperties = new UploadProperties();
        uploadProperties.setDir(tempDir.toString());
        uploadProperties.setUrlPrefix("/uploads");
        uploadProperties.setMaxImageSize(DataSize.ofMegabytes(1));

        jwtUtil = new JwtUtil(SECRET, 60);
        userService = new UserServiceImpl(userMapper, passwordEncoder, jwtUtil, new FileUtil(uploadProperties));

        // 模拟已登录：编号 1 的普通用户
        UserContext.set(new LoginUser(1L, "zhangsan", Role.USER));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /* ---------------- 注册 ---------------- */

    @Test
    @DisplayName("注册成功：密码以 BCrypt 存储，昵称默认取用户名")
    void registerStoresBcryptPassword() {
        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(inserted.capture())).thenReturn(1);
        when(userMapper.selectById(any())).thenAnswer(invocation -> inserted.getValue());

        UserVO vo = userService.register(registerRequest("zhangsan", "123456", null));

        User saved = inserted.getValue();
        assertNotEquals("123456", saved.getPassword(), "数据库里绝不能是明文密码");
        assertTrue(saved.getPassword().startsWith("$2"), "应该是 BCrypt 哈希：" + saved.getPassword());
        assertTrue(passwordEncoder.matches("123456", saved.getPassword()), "哈希应能校验回原密码");
        assertEquals("zhangsan", saved.getNickname());
        assertEquals(Role.USER, saved.getRole());
        assertEquals(User.STATUS_NORMAL, saved.getStatus());
        assertEquals("zhangsan", vo.getUsername());
    }

    @Test
    @DisplayName("注册时填了昵称：使用传入的昵称")
    void registerUsesGivenNickname() {
        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.insert(inserted.capture())).thenReturn(1);
        when(userMapper.selectById(any())).thenAnswer(invocation -> inserted.getValue());

        userService.register(registerRequest("lisi", "123456", "李四"));

        assertEquals("李四", inserted.getValue().getNickname());
    }

    @Test
    @DisplayName("用户名已存在：拒绝注册且不写库")
    void registerRejectsDuplicateUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.register(registerRequest("zhangsan", "123456", null)));

        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, e.getErrorCode());
        verify(userMapper, never()).insert(any(User.class));
    }

    /* ---------------- 登录 ---------------- */

    @Test
    @DisplayName("登录成功：返回的令牌能解析出该用户")
    void loginReturnsParsableToken() {
        when(userMapper.selectOne(any())).thenReturn(existingUser(5L, "zhangsan", "123456"));

        LoginVO loginVO = userService.login(loginRequest("zhangsan", "123456"));

        assertEquals("Bearer", loginVO.getTokenType());
        assertEquals(3600L, loginVO.getExpireSeconds());
        assertEquals(5L, jwtUtil.parseToken(loginVO.getToken()).getUserId().longValue());
        assertEquals("zhangsan", loginVO.getUser().getUsername());
        assertNull(loginVO.getUser().getAvatarUrl());
    }

    @Test
    @DisplayName("密码错误：统一返回登录失败，不暴露用户名是否存在")
    void loginRejectsWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(existingUser(5L, "zhangsan", "123456"));

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("zhangsan", "wrong-password")));

        assertEquals(ErrorCode.LOGIN_FAILED, e.getErrorCode());
    }

    @Test
    @DisplayName("用户不存在：同样返回登录失败")
    void loginRejectsUnknownUser() {
        when(userMapper.selectOne(any())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("nobody", "123456")));

        assertEquals(ErrorCode.LOGIN_FAILED, e.getErrorCode());
    }

    @Test
    @DisplayName("账号被禁用：即使密码正确也不能登录")
    void loginRejectsDisabledAccount() {
        User disabled = existingUser(5L, "zhangsan", "123456");
        disabled.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(disabled);

        BusinessException e = assertThrows(BusinessException.class,
                () -> userService.login(loginRequest("zhangsan", "123456")));

        assertEquals(ErrorCode.ACCOUNT_DISABLED, e.getErrorCode());
    }

    /* ---------------- 个人资料 ---------------- */

    @Test
    @DisplayName("修改资料：昵称被更新")
    void updateProfileChangesNickname() {
        when(userMapper.selectById(1L)).thenReturn(existingUser(1L, "zhangsan", "123456"));
        ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
        when(userMapper.updateById(updated.capture())).thenReturn(1);

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname("张三丰");
        userService.updateProfile(request);

        assertEquals(1L, updated.getValue().getId().longValue());
        assertEquals("张三丰", updated.getValue().getNickname());
    }

    @Test
    @DisplayName("未登录调用个人接口：抛 401")
    void personalApiRequiresLogin() {
        UserContext.clear();

        BusinessException e = assertThrows(BusinessException.class, () -> userService.getCurrentUser());

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    /* ---------------- 改密码 ---------------- */

    @Test
    @DisplayName("原密码错误：拒绝改密且不写库")
    void changePasswordRejectsWrongOldPassword() {
        when(userMapper.selectById(1L)).thenReturn(existingUser(1L, "zhangsan", "123456"));
        ChangePasswordRequest request = changePasswordRequest("wrong-password", "new-password");

        BusinessException e = assertThrows(BusinessException.class, () -> userService.changePassword(request));

        assertEquals(ErrorCode.OLD_PASSWORD_INCORRECT, e.getErrorCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("改密成功：新密码以 BCrypt 存储，旧密码不再有效")
    void changePasswordStoresNewBcryptHash() {
        when(userMapper.selectById(1L)).thenReturn(existingUser(1L, "zhangsan", "123456"));
        ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
        when(userMapper.updateById(updated.capture())).thenReturn(1);

        userService.changePassword(changePasswordRequest("123456", "new-password"));

        String newHash = updated.getValue().getPassword();
        assertTrue(passwordEncoder.matches("new-password", newHash));
        assertFalse(passwordEncoder.matches("123456", newHash), "旧密码不应还能通过校验");
    }

    /* ---------------- 头像 ---------------- */

    @Test
    @DisplayName("上传头像：文件落盘，数据库只保存访问 URL")
    void updateAvatarStoresUrl() {
        when(userMapper.selectById(1L)).thenReturn(existingUser(1L, "zhangsan", "123456"));
        ArgumentCaptor<User> updated = ArgumentCaptor.forClass(User.class);
        when(userMapper.updateById(updated.capture())).thenReturn(1);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[64]);

        userService.updateAvatar(file);

        String url = updated.getValue().getAvatarUrl();
        assertNotNull(url);
        assertTrue(url.startsWith("/uploads/avatar/"), url);
        assertTrue(tempDir.resolve(url.substring("/uploads/".length())).toFile().exists(), "头像文件应真实落盘");
    }

    /* ---------------- 辅助方法 ---------------- */

    private RegisterRequest registerRequest(String username, String password, String nickname) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword(password);
        request.setNickname(nickname);
        return request;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private ChangePasswordRequest changePasswordRequest(String oldPassword, String newPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword(oldPassword);
        request.setNewPassword(newPassword);
        return request;
    }

    private User existingUser(Long id, String username, String rawPassword) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setStatus(User.STATUS_NORMAL);
        return user;
    }
}
