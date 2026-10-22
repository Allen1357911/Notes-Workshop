package com.notework.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（性能与健壮性模块）。
 * <p>
 * 标在 Controller 的写操作方法上，由 {@code OperationLogAspect} 统一拦截并落库。
 * 业务代码只需要加一行注解，不需要写任何日志代码 —— 这就是"注解 + AOP"的用意：
 * 日志逻辑与业务逻辑解耦，以后新增接口只要加注解即可被记录。
 * <p>
 * 注意：参数摘要由切面自动脱敏，凡是字段名里带 password 的一律记成 {@code ***}，
 * 所以即使标在登录/注册/改密码这些接口上也不会把密码写进日志表。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 模块名，例如「用户管理」「笔记审核」「社区互动」 */
    String module();

    /** 动作名，例如「下架」「禁用账号」「发表评论」 */
    String action();

    /** 补充说明，写进日志的 description 字段 */
    String description() default "";

    /** 是否记录参数摘要（默认记录；参数摘要一定会脱敏） */
    boolean recordParams() default true;
}
