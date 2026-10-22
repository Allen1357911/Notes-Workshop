package com.notework.util;

import com.notework.common.enums.Role;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.interceptor.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 令牌工具：负责签发与解析。
 * <p>
 * 只做令牌本身的事，不做权限判断（权限判断在 Service 层）。
 * 令牌用 HS256 对称签名，密钥来自配置 app.jwt.secret，可用环境变量 JWT_SECRET 覆盖。
 */
@Component
public class JwtUtil {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    /** HS256 要求密钥至少 256 位，即 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final long expireSeconds;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expire-minutes}") long expireMinutes) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT 密钥太短：HS256 至少需要 " + MIN_SECRET_BYTES + " 字节，请检查配置 app.jwt.secret");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expireSeconds = expireMinutes * 60;
    }

    /**
     * 签发令牌。
     * subject 放用户 id，另带 username 与 role 两个自定义声明。
     */
    public String generateToken(Long userId, String username, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLE, role == null ? Role.USER.name() : role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌。
     * 签名不符、格式错误、已过期、被篡改，统一抛 401 业务异常，不向调用方暴露细节。
     */
    public LoginUser parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get(CLAIM_USERNAME, String.class);
            String roleName = claims.get(CLAIM_ROLE, String.class);
            Role role = roleName == null ? Role.USER : Role.valueOf(roleName);
            return new LoginUser(userId, username, role);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 令牌有效期（秒），登录接口返回给前端 */
    public long getExpireSeconds() {
        return expireSeconds;
    }
}
