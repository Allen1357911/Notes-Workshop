package com.notework.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.notework.common.api.PageQuery;
import com.notework.common.api.PageResult;
import com.notework.common.enums.Role;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.dto.response.AdminUserVO;
import com.notework.entity.User;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员后台：用户管理单元测试。
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    private static final Long ADMIN_ID = 99L;
    private static final Long TARGET_ID = 5L;

    @Mock
    private UserMapper userMapper;

    private AdminUserServiceImpl adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserServiceImpl(userMapper);
        UserContext.set(new LoginUser(ADMIN_ID, "admin", Role.ADMIN));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("用户列表：分页返回，且不含密码字段")
    void pageUsersReturnsSafeFields() {
        when(userMapper.<Page<User>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<User> page = invocation.getArgument(0);
            page.setRecords(List.of(user(TARGET_ID, "zhangsan", 1), user(6L, "lisi", 0)));
            page.setTotal(2);
            return page;
        });

        PageResult<AdminUserVO> result = adminUserService.pageUsers(null, null, new PageQuery());

        assertEquals(2L, result.getTotal());
        AdminUserVO first = result.getList().get(0);
        assertEquals("zhangsan", first.getUsername());
        assertEquals("正常", first.getStatusLabel());
        assertEquals("已禁用", result.getList().get(1).getStatusLabel());
        // VO 里没有 password 属性，防止密码被带出去
        assertFalse(hasPasswordProperty(first));
    }

    @Test
    @DisplayName("禁用用户：状态写入数据库")
    void disableUserUpdatesStatus() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "zhangsan", 1));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userMapper.updateById(captor.capture())).thenReturn(1);

        adminUserService.updateStatus(TARGET_ID, 0);

        assertEquals(0, captor.getValue().getStatus());
        assertEquals(TARGET_ID, captor.getValue().getId());
    }

    @Test
    @DisplayName("启用用户：状态写回 1")
    void enableUserUpdatesStatus() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(user(TARGET_ID, "zhangsan", 0));
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userMapper.updateById(captor.capture())).thenReturn(1);

        adminUserService.updateStatus(TARGET_ID, 1);

        assertEquals(1, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("不能禁用自己的账号")
    void cannotDisableSelf() {
        when(userMapper.selectById(ADMIN_ID)).thenReturn(user(ADMIN_ID, "admin", 1));

        BusinessException e = assertThrows(BusinessException.class,
                () -> adminUserService.updateStatus(ADMIN_ID, 0));

        assertEquals(ErrorCode.CANNOT_DISABLE_SELF, e.getErrorCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("状态值非法：拒绝")
    void rejectsInvalidStatus() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> adminUserService.updateStatus(TARGET_ID, 7));

        assertEquals(ErrorCode.PARAM_ERROR, e.getErrorCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    @DisplayName("用户不存在：抛 10001")
    void rejectsMissingUser() {
        when(userMapper.selectById(TARGET_ID)).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> adminUserService.updateStatus(TARGET_ID, 0));

        assertEquals(ErrorCode.USER_NOT_FOUND, e.getErrorCode());
    }

    @Test
    @DisplayName("没有任何用户时：返回空分页而不是 null")
    void pageUsersEmpty() {
        when(userMapper.<Page<User>>selectPage(any(), any())).thenAnswer(invocation -> {
            Page<User> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        PageResult<AdminUserVO> result = adminUserService.pageUsers(null, null, new PageQuery());

        assertEquals(0L, result.getTotal());
        assertTrue(result.getList().isEmpty());
    }

    private boolean hasPasswordProperty(AdminUserVO vo) {
        return java.util.Arrays.stream(AdminUserVO.class.getDeclaredFields())
                .anyMatch(field -> "password".equals(field.getName()));
    }

    private User user(Long id, String username, int status) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setNickname(username);
        user.setRole(Role.USER);
        user.setStatus(status);
        return user;
    }
}
