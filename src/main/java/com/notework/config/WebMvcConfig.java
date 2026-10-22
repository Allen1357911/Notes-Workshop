package com.notework.config;

import com.notework.interceptor.AccountStatusInterceptor;
import com.notework.interceptor.AdminAuthInterceptor;
import com.notework.interceptor.JwtAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 层配置：注册登录态拦截器 + 把上传目录映射成可访问的静态资源。
 * <p>
 * 注意：哪些接口允许匿名访问由 {@link JwtAuthInterceptor} 自己判断
 * （因为需要结合请求方法，路径模式表达不了），这里只负责拦截范围。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final AccountStatusInterceptor accountStatusInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final UploadProperties uploadProperties;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor,
                        AccountStatusInterceptor accountStatusInterceptor,
                        AdminAuthInterceptor adminAuthInterceptor,
                        UploadProperties uploadProperties) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.accountStatusInterceptor = accountStatusInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.uploadProperties = uploadProperties;
    }

    /**
     * 拦截 /api/** 下的全部接口；静态页面与 /uploads/** 图片不拦截。
     * <p>
     * 执行顺序即注册顺序：登录态 → 账号状态（禁用立即生效、角色取最新）→ 管理员权限（仅 /api/admin/**）。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(accountStatusInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**");
    }

    /** 让 /uploads/** 能直接访问到本地上传目录里的图片 */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(uploadProperties.getUrlPrefix() + "/**")
                .addResourceLocations(location);
    }
}
