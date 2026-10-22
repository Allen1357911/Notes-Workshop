package com.notework.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.api.Result;
import com.notework.common.enums.Role;
import com.notework.entity.User;
import com.notework.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 账号状态拦截器验证：禁用立即生效、角色取数据库最新值、匿名请求不查库。
 */
@ExtendWith(MockitoExtension.class)
class AccountStatusInterceptorTest {

    private static final Long USER_ID = 5L;

    @Mock
    private UserMapper userMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .addInterceptors(new AccountStatusInterceptor(userMapper, new ObjectMapper()))
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("匿名请求：直接放行，不查用户表")
    void anonymousSkipsDatabase() throws Exception {
        mockMvc.perform(get("/api/fixture/who"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.login").value(false));

        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("账号正常：放行")
    void enabledAccountPasses() throws Exception {
        UserContext.set(new LoginUser(USER_ID, "zhangsan", Role.USER));
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, User.STATUS_NORMAL, Role.USER));

        mockMvc.perform(get("/api/fixture/who"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.admin").value(false));
    }

    @Test
    @DisplayName("账号被禁用：403 且提示账号已被禁用（已签发的旧令牌同样失效）")
    void disabledAccountRejected() throws Exception {
        UserContext.set(new LoginUser(USER_ID, "zhangsan", Role.USER));
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, 0, Role.USER));

        mockMvc.perform(get("/api/fixture/who"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(10005))
                .andExpect(jsonPath("$.message").value("账号已被禁用，请联系管理员"));
    }

    @Test
    @DisplayName("用户已被删除：401")
    void deletedAccountRejected() throws Exception {
        UserContext.set(new LoginUser(USER_ID, "zhangsan", Role.USER));
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        mockMvc.perform(get("/api/fixture/who"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("角色以数据库为准：令牌里是普通用户、库里已提权为管理员 → 立即拥有管理员身份")
    void roleRefreshedFromDatabase() throws Exception {
        UserContext.set(new LoginUser(USER_ID, "zhangsan", Role.USER));
        when(userMapper.selectById(USER_ID)).thenReturn(user(USER_ID, User.STATUS_NORMAL, Role.ADMIN));

        mockMvc.perform(get("/api/fixture/who"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.admin").value(true));
    }

    private User user(Long id, Integer status, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername("zhangsan");
        user.setNickname("zhangsan");
        user.setStatus(status);
        user.setRole(role);
        return user;
    }

    @RestController
    @RequestMapping("/api/fixture")
    static class FixtureController {

        @GetMapping("/who")
        public Result<WhoView> who() {
            return Result.success(new WhoView(UserContext.isLogin(), UserContext.isAdmin()));
        }
    }

    static class WhoView {

        private final boolean login;
        private final boolean admin;

        WhoView(boolean login, boolean admin) {
            this.login = login;
            this.admin = admin;
        }

        public boolean isLogin() {
            return login;
        }

        public boolean isAdmin() {
            return admin;
        }
    }
}
