package com.notework.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.api.Result;
import com.notework.common.enums.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员权限拦截器验证：未登录 401、非管理员 403、管理员放行。
 */
class AdminAuthInterceptorTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .addInterceptors(new AdminAuthInterceptor(new ObjectMapper()))
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("未登录访问后台接口：401")
    void anonymousRejected() throws Exception {
        mockMvc.perform(get("/api/admin/fixture"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("普通用户访问后台接口：403 且提示需要管理员权限")
    void normalUserRejected() throws Exception {
        UserContext.set(new LoginUser(5L, "zhangsan", Role.USER));

        mockMvc.perform(get("/api/admin/fixture"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("需要管理员权限"));
    }

    @Test
    @DisplayName("管理员访问后台接口：放行")
    void adminPasses() throws Exception {
        UserContext.set(new LoginUser(99L, "admin", Role.ADMIN));

        mockMvc.perform(get("/api/admin/fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("admin-ok"));
    }

    @RestController
    @RequestMapping("/api/admin/fixture")
    static class FixtureController {

        @GetMapping
        public Result<String> ok() {
            return Result.success("admin-ok");
        }
    }
}
