package com.notework.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.api.Result;
import com.notework.common.constant.CommonConstants;
import com.notework.common.exception.BusinessException;
import com.notework.common.exception.ErrorCode;
import com.notework.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Set;

/**
 * 登录态拦截器。
 * <p>
 * 职责边界：只判断"有没有带令牌、令牌是否有效、当前用户是谁"，
 * 不做任何业务权限判断（例如"只能改自己的笔记"在 Service 层做）。
 * <p>
 * 放行规则写在这里，而不是用 WebMvcConfigurer 的 excludePathPatterns，
 * 因为"笔记详情"要求 GET /api/notes/{数字} 允许匿名、而 /api/notes/my 必须登录，
 * 光靠路径模式无法区分这两者，必须结合请求方法一起判断。
 * <p>
 * 未通过时直接写出统一格式的 401 响应 —— 这里绕过了 Controller，
 * 所以必须自己写 JSON，保证前端拿到的结构和全局异常处理器一致。
 */
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    /** 允许匿名访问的固定 GET 路径 */
    private static final Set<String> ANONYMOUS_GET_PATHS = Set.of(
            "/api/notes/public",
            "/api/notes/rank",
            "/api/authors/hot"
    );

    /** 允许匿名访问的 POST 接口：注册与登录 */
    private static final Set<String> ANONYMOUS_POST_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login"
    );

    private static final String NOTE_PREFIX = "/api/notes/";
    private static final String USER_PREFIX = "/api/users/";
    /** 作者主页下允许匿名查看的子路径 */
    private static final Set<String> ANONYMOUS_USER_SUB_PATHS = Set.of("", "/notes", "/followers", "/following");

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtAuthInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String token = resolveToken(request);

        if (token != null) {
            try {
                // 令牌有效：无论是公开接口还是受保护接口，都把当前用户放进上下文
                UserContext.set(jwtUtil.parseToken(token));
                return true;
            } catch (BusinessException e) {
                // 公开接口上带了失效令牌时按访客处理，避免前端本地残留的过期令牌把公开页面也挡掉
                if (isAnonymousAllowed(request)) {
                    return true;
                }
                writeUnauthorized(response);
                return false;
            }
        }

        if (isAnonymousAllowed(request)) {
            return true;
        }

        writeUnauthorized(response);
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 必须清理，避免线程复用把上一个请求的登录用户带给下一个请求
        UserContext.clear();
    }

    /** 从 Authorization 头里取出令牌；没有或不带 Bearer 前缀时返回 null */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (header == null || !header.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return null;
        }
        String token = header.substring(CommonConstants.TOKEN_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /** 当前请求是否属于"允许匿名"的接口 */
    private boolean isAnonymousAllowed(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("GET".equalsIgnoreCase(method)) {
            return ANONYMOUS_GET_PATHS.contains(path)
                    || isNoteDetailPath(path)
                    || isNoteCommentsPath(path)
                    || isUserProfilePath(path);
        }
        if ("POST".equalsIgnoreCase(method)) {
            return ANONYMOUS_POST_PATHS.contains(path);
        }
        return false;
    }

    /** /api/notes/{数字id}：公开笔记详情（/api/notes/my 这类固定路径不算） */
    private boolean isNoteDetailPath(String path) {
        return isId(tailAfter(path, NOTE_PREFIX));
    }

    /** /api/notes/{数字id}/comments：笔记的评论列表，允许未登录查看 */
    private boolean isNoteCommentsPath(String path) {
        String tail = tailAfter(path, NOTE_PREFIX);
        if (tail == null || !tail.endsWith("/comments")) {
            return false;
        }
        return isId(tail.substring(0, tail.length() - "/comments".length()));
    }

    /**
     * /api/users/{数字id} 及其作者主页子路径（/notes、/followers、/following）允许未登录查看。
     * 注意 /api/users/me 不是数字 id，依然要求登录。
     */
    private boolean isUserProfilePath(String path) {
        String tail = tailAfter(path, USER_PREFIX);
        if (tail == null) {
            return false;
        }
        for (String sub : ANONYMOUS_USER_SUB_PATHS) {
            if (sub.isEmpty()) {
                continue;
            }
            if (tail.endsWith(sub)) {
                tail = tail.substring(0, tail.length() - sub.length());
                break;
            }
        }
        return isId(tail);
    }

    /** 去掉前缀后的剩余部分；不以该前缀开头时返回 null */
    private String tailAfter(String path, String prefix) {
        return path.startsWith(prefix) ? path.substring(prefix.length()) : null;
    }

    /** 是否是非空且长度合理的纯数字 id */
    private boolean isId(String value) {
        if (value == null || value.isEmpty() || value.length() > 18) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.UNAUTHORIZED.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.UNAUTHORIZED)));
    }
}
