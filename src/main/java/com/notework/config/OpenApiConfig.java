package com.notework.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Swagger UI / OpenAPI 3）。
 * <p>
 * 启动后访问 /swagger-ui.html 即可看到全部接口，点右上角 Authorize 填入登录返回的令牌，
 * 就能直接在页面上调试需要登录的接口。
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案名称，与 SecurityScheme 里注册的名字一致 */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI noteWorkshopOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("笔记工坊 Note-Workshop 接口文档")
                        .version("1.0.0")
                        .description("""
                                面向在校学生的读书笔记系统（基础功能：用户、读书笔记、点赞）。

                                使用说明：
                                1. 先调用「认证接口」里的注册、登录，拿到返回的 token；
                                2. 点右上角 Authorize 按钮，把 token 填进去（不用手写 Bearer 前缀）；
                                3. 之后即可调试所有需要登录的接口。

                                未登录时仍可访问：注册、登录、公开笔记列表、笔记详情（公开笔记）、按点赞数排行。
                                """)
                        .contact(new Contact().name("Note-Workshop")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录接口返回的 JWT 令牌")))
                // 全局默认需要令牌；对允许匿名的接口，用 @SecurityRequirements 单独覆盖
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
