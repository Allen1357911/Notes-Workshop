package com.notework.util;

import com.notework.common.enums.Role;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.interceptor.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 工具的单元测试：不依赖 Spring 容器，也不依赖数据库。
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-at-least-32-bytes-long!!";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 60);

    @Test
    @DisplayName("签发的令牌能解析回原始用户信息")
    void generateAndParse() {
        String token = jwtUtil.generateToken(7L, "zhangsan", Role.USER);

        LoginUser loginUser = jwtUtil.parseToken(token);

        assertEquals(7L, loginUser.getUserId().longValue());
        assertEquals("zhangsan", loginUser.getUsername());
        assertEquals(Role.USER, loginUser.getRole());
        assertFalse(loginUser.isAdmin());
    }

    @Test
    @DisplayName("管理员角色能随令牌带出")
    void adminRoleIsCarried() {
        LoginUser loginUser = jwtUtil.parseToken(jwtUtil.generateToken(1L, "admin", Role.ADMIN));

        assertEquals(Role.ADMIN, loginUser.getRole());
        assertTrue(loginUser.isAdmin());
    }

    @Test
    @DisplayName("被篡改的令牌解析失败并抛 401")
    void tamperedTokenRejected() {
        String token = jwtUtil.generateToken(7L, "zhangsan", Role.USER);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        BusinessException e = assertThrows(BusinessException.class, () -> jwtUtil.parseToken(tampered));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    @Test
    @DisplayName("用其他密钥签发的令牌解析失败")
    void tokenSignedByAnotherSecretRejected() {
        JwtUtil another = new JwtUtil("another-secret-key-also-32-bytes-long!!!!", 60);
        String token = another.generateToken(7L, "zhangsan", Role.USER);

        assertThrows(BusinessException.class, () -> jwtUtil.parseToken(token));
    }

    @Test
    @DisplayName("过期令牌解析失败并抛 401")
    void expiredTokenRejected() {
        JwtUtil alreadyExpired = new JwtUtil(SECRET, -1);
        String token = alreadyExpired.generateToken(7L, "zhangsan", Role.USER);

        BusinessException e = assertThrows(BusinessException.class, () -> jwtUtil.parseToken(token));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    @Test
    @DisplayName("格式非法的字符串解析失败")
    void malformedTokenRejected() {
        assertThrows(BusinessException.class, () -> jwtUtil.parseToken("not-a-jwt"));
    }

    @Test
    @DisplayName("密钥短于 32 字节时拒绝构造")
    void shortSecretRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("too-short", 60));
    }

    @Test
    @DisplayName("有效期以秒返回")
    void expireSecondsCalculated() {
        assertEquals(3600L, new JwtUtil(SECRET, 60).getExpireSeconds());
    }
}
