package com.notework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置。
 * <p>
 * 只引入 spring-security-crypto 拿 BCrypt，不引入 Spring Security 的过滤器链 ——
 * 项目的鉴权走 JWT + 拦截器，不需要 Security 那一整套。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
