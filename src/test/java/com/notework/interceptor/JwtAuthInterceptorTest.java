package com.notework.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notework.common.api.Result;
import com.notework.common.constant.CommonConstants;
import com.notework.common.enums.Role;
import com.notework.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录态拦截器的行为验证：既要挡住未登录的受保护接口，也要放行公开接口。
 * <p>
 * 用 MockMvc standalone 挂上真实拦截器，不启动服务器、不连数据库。
 */
class JwtAuthInterceptorTest {

    private static final String SECRET = "interceptor-test-secret-key-32-bytes-min!!";

    private JwtUtil jwtUtil;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, 60);
        JwtAuthInterceptor interceptor = new JwtAuthInterceptor(jwtUtil, new ObjectMapper());

        // standalone 模式下拦截器直接挂到全部路径上，放行规则由拦截器自己判断
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController(), new NoteFixtureController(),
                        new AuthFixtureController(), new SocialFixtureController())
                .addInterceptors(interceptor)
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    /* ---------------- 受保护接口 ---------------- */

    @Test
    @DisplayName("不带令牌访问受保护接口：401，且响应结构与其他错误一致")
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/fixture/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录或登录状态已过期"));
    }

    @Test
    @DisplayName("令牌格式非法：401")
    void malformedTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/fixture/me")
                        .header(CommonConstants.TOKEN_HEADER, CommonConstants.TOKEN_PREFIX + "abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("没带 Bearer 前缀：401")
    void missingPrefixReturnsUnauthorized() throws Exception {
        String token = jwtUtil.generateToken(1L, "zhangsan", Role.USER);

        mockMvc.perform(get("/api/fixture/me").header(CommonConstants.TOKEN_HEADER, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("我的笔记列表必须登录：不带令牌 401")
    void myNotesRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/notes/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("新建笔记必须登录：不带令牌 401")
    void createNoteRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/notes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("非数字的笔记路径不算详情，必须登录")
    void nonNumericNotePathRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/notes/abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("我的资料 /api/users/me 必须登录：不能因为作者主页放行规则被误放行")
    void usersMeStillRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("关注流必须登录：不带令牌 401")
    void followingFeedRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/feed/following"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("发表评论必须登录：不带令牌 401")
    void createCommentRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/notes/12/comments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("关注他人必须登录：不带令牌 401")
    void followRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/users/8/follow"))
                .andExpect(status().isUnauthorized());
    }

    /* ---------------- 允许匿名的接口 ---------------- */

    @Test
    @DisplayName("公开笔记列表可匿名访问")
    void publicNotesAllowsAnonymous() throws Exception {
        mockMvc.perform(get("/api/notes/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("public-list"));
    }

    @Test
    @DisplayName("笔记详情可匿名访问（是否为私有笔记由 Service 判断）")
    void noteDetailAllowsAnonymous() throws Exception {
        mockMvc.perform(get("/api/notes/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("detail-12"));
    }

    @Test
    @DisplayName("排行榜可匿名访问")
    void rankAllowsAnonymous() throws Exception {
        mockMvc.perform(get("/api/notes/rank"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("rank"));
    }

    @Test
    @DisplayName("笔记的评论列表可匿名访问")
    void noteCommentsAllowAnonymous() throws Exception {
        mockMvc.perform(get("/api/notes/12/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("comments-12"));
    }

    @Test
    @DisplayName("作者主页、他的笔记、粉丝与关注列表都可匿名访问")
    void authorProfilePathsAllowAnonymous() throws Exception {
        mockMvc.perform(get("/api/users/8")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("author-8"));
        mockMvc.perform(get("/api/users/8/notes")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("author-notes-8"));
        mockMvc.perform(get("/api/users/8/followers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("followers-8"));
        mockMvc.perform(get("/api/users/8/following")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("following-8"));
    }

    @Test
    @DisplayName("热门作者排行可匿名访问")
    void hotAuthorsAllowAnonymous() throws Exception {
        mockMvc.perform(get("/api/authors/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("hot-authors"));
    }

    @Test
    @DisplayName("登录接口可匿名访问")
    void loginAllowsAnonymous() throws Exception {
        mockMvc.perform(post("/api/auth/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("login"));
    }

    @Test
    @DisplayName("公开接口上带了失效令牌：按访客处理，不把公开页面挡掉")
    void invalidTokenOnAnonymousEndpointIsTreatedAsVisitor() throws Exception {
        mockMvc.perform(get("/api/notes/public")
                        .header(CommonConstants.TOKEN_HEADER, CommonConstants.TOKEN_PREFIX + "expired.token.here"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("public-list"));
    }

    /* ---------------- 上下文 ---------------- */

    @Test
    @DisplayName("令牌有效：放行，且业务代码能取到当前用户")
    void validTokenPassesAndFillsContext() throws Exception {
        String token = jwtUtil.generateToken(9L, "zhangsan", Role.USER);

        mockMvc.perform(get("/api/fixture/me")
                        .header(CommonConstants.TOKEN_HEADER, CommonConstants.TOKEN_PREFIX + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").value(9))
                .andExpect(jsonPath("$.data.username").value("zhangsan"))
                .andExpect(jsonPath("$.data.admin").value(false));
    }

    @Test
    @DisplayName("请求结束后上下文被清理，不会把登录用户带给下一个请求")
    void contextIsClearedAfterRequest() throws Exception {
        String token = jwtUtil.generateToken(9L, "zhangsan", Role.USER);

        mockMvc.perform(get("/api/fixture/me")
                        .header(CommonConstants.TOKEN_HEADER, CommonConstants.TOKEN_PREFIX + token))
                .andExpect(status().isOk());

        assertFalse(UserContext.isLogin());
        assertNull(UserContext.getUserId());
    }

    /** 只用于测试的临时 Controller：把当前上下文里的用户读出来 */
    @RestController
    @RequestMapping("/api/fixture")
    static class FixtureController {

        @GetMapping("/me")
        public Result<CurrentUserView> me() {
            LoginUser user = UserContext.get();
            return Result.success(new CurrentUserView(user.getUserId(), user.getUsername(), user.isAdmin()));
        }
    }

    /** 只用于测试的临时 Controller：模拟公开/受保护的笔记接口路径 */
    @RestController
    @RequestMapping("/api/notes")
    static class NoteFixtureController {

        @GetMapping("/public")
        public Result<String> publicList() {
            return Result.success("public-list");
        }

        @GetMapping("/my")
        public Result<String> myList() {
            return Result.success("my-list");
        }

        @GetMapping("/rank")
        public Result<String> rank() {
            return Result.success("rank");
        }

        @GetMapping("/{id}")
        public Result<String> detail(@PathVariable("id") String id) {
            return Result.success("detail-" + id);
        }

        @PostMapping
        public Result<String> create() {
            return Result.success("created");
        }

        @GetMapping("/{id}/comments")
        public Result<String> comments(@PathVariable("id") String id) {
            return Result.success("comments-" + id);
        }

        @PostMapping("/{id}/comments")
        public Result<String> createComment(@PathVariable("id") String id) {
            return Result.success("comment-created-" + id);
        }
    }

    /** 只用于测试的临时 Controller：模拟作者主页、粉丝/关注、热门作者与关注流 */
    @RestController
    static class SocialFixtureController {

        @GetMapping("/api/users/{id}")
        public Result<String> author(@PathVariable("id") String id) {
            return Result.success("author-" + id);
        }

        @GetMapping("/api/users/{id}/notes")
        public Result<String> authorNotes(@PathVariable("id") String id) {
            return Result.success("author-notes-" + id);
        }

        @GetMapping("/api/users/{id}/followers")
        public Result<String> followers(@PathVariable("id") String id) {
            return Result.success("followers-" + id);
        }

        @GetMapping("/api/users/{id}/following")
        public Result<String> following(@PathVariable("id") String id) {
            return Result.success("following-" + id);
        }

        @GetMapping("/api/authors/hot")
        public Result<String> hotAuthors() {
            return Result.success("hot-authors");
        }

        @GetMapping("/api/feed/following")
        public Result<String> feed() {
            return Result.success("feed");
        }

        @GetMapping("/api/users/me")
        public Result<String> me() {
            return Result.success("me");
        }

        @PostMapping("/api/users/{id}/follow")
        public Result<String> follow(@PathVariable("id") String id) {
            return Result.success("followed-" + id);
        }
    }

    /** 只用于测试的临时 Controller：模拟登录接口 */
    @RestController
    @RequestMapping("/api/auth")
    static class AuthFixtureController {

        @PostMapping("/login")
        public Result<String> login() {
            return Result.success("login");
        }
    }

    /** 只用于测试的视图对象 */
    static class CurrentUserView {

        private final Long userId;
        private final String username;
        private final boolean admin;

        CurrentUserView(Long userId, String username, boolean admin) {
            this.userId = userId;
            this.username = username;
            this.admin = admin;
        }

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public boolean isAdmin() {
            return admin;
        }
    }
}
