package com.notework.aspect;

import com.notework.common.annotation.OperationLog;
import com.notework.common.exception.BusinessException;
import com.notework.entity.OperationLogRecord;
import com.notework.interceptor.LoginUser;
import com.notework.interceptor.UserContext;
import com.notework.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 操作日志切面：拦截所有标了 {@link OperationLog} 的方法，自动记录"谁、什么时候、对什么做了什么、结果如何"。
 * <p>
 * 三个设计要点：
 * <ol>
 *     <li><b>绝不影响业务</b>：记录过程整体包在 try-catch 里，写日志失败只打一条 warn，不往上抛。</li>
 *     <li><b>失败也要留痕</b>：业务方法抛异常时同样记录一条 success=0 的日志（含业务错误提示），
 *         便于排查"谁试图做了什么但被拒绝"。日志本身用独立事务写入，业务回滚不会把它一起回滚。</li>
 *     <li><b>不记录敏感信息</b>：参数摘要由 {@link #summarizeParams(Object[])} 生成，
 *         字段名里带 password 的一律写成 ***，文件只记大小与文件名。</li>
 * </ol>
 */
@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 参数摘要与错误信息的最大长度（与表结构 VARCHAR(500) 对齐） */
    static final int MAX_TEXT = 500;
    /** description / uri 对应的列是 VARCHAR(255) */
    static final int MAX_SHORT_TEXT = 255;
    /** ip 对应的列是 VARCHAR(64)：X-Forwarded-For 由客户端控制，必须截断，否则超长会让整条日志写不进去（等于抹掉审计痕迹） */
    static final int MAX_IP_TEXT = 64;
    /** 判断敏感字段用的关键字 */
    private static final String SENSITIVE_KEYWORD = "password";
    /** 本机 IPv6 回环地址，读起来费劲，统一显示成 127.0.0.1 */
    private static final String IPV6_LOOPBACK = "0:0:0:0:0:0:0:1";

    private final OperationLogService operationLogService;

    public OperationLogAspect(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long start = System.nanoTime();
        Throwable error = null;
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long costMs = (System.nanoTime() - start) / 1_000_000;
            saveQuietly(joinPoint, operationLog, error, costMs);
        }
    }

    /** 记录日志；任何异常都被吞掉并降级为一条 warn，保证不影响业务 */
    private void saveQuietly(ProceedingJoinPoint joinPoint, OperationLog annotation, Throwable error, long costMs) {
        try {
            OperationLogRecord record = new OperationLogRecord();
            record.setModule(annotation.module());
            record.setAction(annotation.action());
            record.setDescription(truncate(annotation.description(), MAX_SHORT_TEXT));
            record.setSuccess(error == null ? 1 : 0);
            record.setCostMs(costMs);
            if (error != null) {
                record.setErrorMsg(truncate(describeError(error), MAX_TEXT));
            }

            LoginUser loginUser = UserContext.get();
            if (loginUser != null) {
                record.setUserId(loginUser.getUserId());
                record.setUsername(loginUser.getUsername());
            }

            Object[] args = joinPoint.getArgs();
            record.setTargetId(findTargetId(args));
            if (annotation.recordParams()) {
                record.setParams(truncate(paramsOf(args), MAX_TEXT));
            }

            HttpServletRequest request = currentRequest();
            if (request != null) {
                record.setUri(truncate(request.getRequestURI(), MAX_SHORT_TEXT));
                record.setMethod(request.getMethod());
                record.setIp(truncate(resolveIp(request), MAX_IP_TEXT));
            }

            operationLogService.record(record);
        } catch (Exception e) {
            log.warn("操作日志写入失败（不影响业务）：{}", e.toString());
        }
    }

    /* ---------------- 参数脱敏与摘要 ---------------- */

    private String paramsOf(Object[] args) {
        return summarizeParams(args);
    }

    /**
     * 把方法参数拼成可读摘要。对自定义对象用反射取字段，字段名含 password 的一律打码。
     * 包可见（无 private）是为了能被单元测试直接验证脱敏行为。
     */
    static String summarizeParams(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Object arg : args) {
            if (arg == null || arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
                continue;
            }
            if (arg instanceof MultipartFile file) {
                parts.add("file=" + file.getOriginalFilename() + "(" + file.getSize() + "B)");
            } else if (arg instanceof Number || arg instanceof CharSequence || arg instanceof Boolean
                    || arg instanceof Enum<?>) {
                parts.add(String.valueOf(arg));
            } else {
                String text = describeBean(arg);
                if (!text.isEmpty()) {
                    parts.add(text);
                }
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String describeBean(Object bean) {
        List<String> fields = new ArrayList<>();
        for (Field field : bean.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(bean);
                if (value == null) {
                    continue;
                }
                boolean sensitive = field.getName().toLowerCase().contains(SENSITIVE_KEYWORD);
                fields.add(field.getName() + "=" + (sensitive ? "***" : String.valueOf(value)));
            } catch (ReflectiveOperationException | RuntimeException e) {
                // 取不到就跳过这个字段（例如模块化访问限制），不影响其它字段
            }
        }
        return String.join(" ", fields);
    }

    /* ---------------- 请求信息 ---------------- */

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 取第一个地址（最靠近客户端的那个）
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = request.getRemoteAddr();
        return IPV6_LOOPBACK.equals(remote) ? "127.0.0.1" : remote;
    }

    /** 取第一个数字型参数作为操作对象 id（本项目所有写接口都把被操作对象的 id 放在第一个参数） */
    private Long findTargetId(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Long value) {
                return value;
            }
            if (arg instanceof Integer value) {
                return value.longValue();
            }
        }
        return null;
    }

    private static String describeError(Throwable error) {
        if (error instanceof BusinessException businessException) {
            return businessException.getMessage();
        }
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    /** 截断到指定长度：包可见是为了能被单元测试直接验证（日志字段必须放得进对应的列） */
    static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
