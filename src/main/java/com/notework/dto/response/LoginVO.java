package com.notework.dto.response;

/**
 * 登录成功返回对象：令牌 + 有效期 + 用户信息。
 */
public class LoginVO {

    /** JWT 令牌 */
    private final String token;

    /** 令牌类型，固定 Bearer，前端拼在 Authorization 头里 */
    private final String tokenType;

    /** 有效期（秒） */
    private final long expireSeconds;

    /** 登录用户信息 */
    private final UserVO user;

    public LoginVO(String token, long expireSeconds, UserVO user) {
        this.token = token;
        this.tokenType = "Bearer";
        this.expireSeconds = expireSeconds;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    public UserVO getUser() {
        return user;
    }
}
