# 笔记工坊 Note-Workshop

面向在校学生的读书笔记系统。学生录入书籍信息、撰写读书笔记、上传书页截图与手写笔记照片，笔记可设为**私有**（仅自己与管理员可见）或**公开**（所有人可见）；公开笔记可以被浏览、点赞，并按点赞数排行。

**平台愿景：辅助深度思考，而不是代替思考。** 笔记本体完全由用户创作，AI 只作为辅助工具（已实现：笔记总结/适宜人群/评分 + 平台答疑客服），AI 产出必须经用户手动确认后才会绑定到笔记上，绝不自动修改笔记内容。

---

## 一、功能现状（这边列举了目前完成的项目功能，以及包含了各项功能的对应限制条件的介绍）

当前完成的是**基础要求**的全部内容（三大业务模块后端 + 数据库 + 前端页面 + 接口文档 + 接口测试记录），以及进阶要求里的**管理员后台**、**笔记审核（含用户侧重提）**、**社区互动（关注 / 粉丝 / 热门作者 / 关注流 / 评论与多级回复）**、**性能与健壮性（注解 + AOP 操作日志、并发与压测用例）**、**AI 功能（笔记总结 / 适宜人群推荐 / 笔记评分 + 调用统计）**与 **AI 进阶功能（平台答疑智能客服：多轮上下文 + 会话隔离 + 审计）**。除"仓库推送"（按要求暂缓）外，作业要求的功能已全部实现并有测试留档。

| 模块 | 已实现 | 说明 |
| --- | --- | --- |
| 用户模块 | 注册、登录、JWT 鉴权、修改资料、修改密码、头像上传 | 密码 BCrypt 哈希存储；未登录无法访问受保护接口 |
| 读书笔记模块 | 新建 / 编辑 / 删除、私有与公开、我的笔记分页、公开笔记列表、详情聚合、配图上传 | 笔记必须指定图书名，且**必须以书名号开头**（如 `《活着》`，也允许 `《老人与海》读后感`；没有书名号返回 400"图书名要用书名号括起来"；首尾空格会自动去掉再校验）；只有作者本人或管理员可改删；详情返回正文、图书名、全部配图、点赞数、评论数与已保存的 AI 结果（作者与管理员可见，或作者公开后所有人可见） |
| 点赞模块 | 点赞、取消点赞、我点赞过的笔记分页、按点赞数排行 | 只能点赞公开笔记；点赞与取消都幂等；点赞数实时聚合 |
| 公共基础设施 | 统一响应体、统一错误码与全局异常处理、分页对象、常量与枚举 | 业务代码不写 try-catch，异常由全局处理器统一翻译成 HTTP 响应 |
| 前端页面 | 15 个页面：首页、公开笔记广场、点赞排行、我赞过的、我的笔记、笔记详情（含评论区）、写/改笔记、个人资料、登录、注册、作者主页、关注流、热门作者、管理后台、**AI 客服** | 原生 HTML/CSS/JS 同源多页，无构建链；未登录也能浏览公开内容与作者主页 |
| **管理员后台**（扩展） | 用户列表与禁用/启用、全部内容管理、公开笔记审核队列与审核、违规内容下架与恢复、违规评论管理与逻辑删除 | 全部由拦截器校验管理员角色；公开笔记先审后发；禁用账号立即生效；页面入口在导航栏「管理后台」 |
| **笔记审核**（扩展） | 公开笔记待审核 → 通过 / 驳回状态机；作者可看到状态与驳回原因；改内容自动重提，也可一键「重新提交审核」 | 审核状态只对作者本人与管理员下发；其他人访问未通过审核的笔记一律按「笔记不存在」处理 |
| **社区互动**（扩展） | 关注 / 取关（幂等、不能关注自己）、粉丝与关注列表、作者主页与 TA 的公开笔记、热门作者排行、关注流、评论与多级回复、删除自己的评论 | 计数全部实时聚合；关注流只含「公开且已通过审核」的笔记；评论树支持任意层级回复，删父评论级联删除子树 |
| **操作日志**（扩展·性能与健壮性） | 自定义注解 `@OperationLog` + AOP 切面自动记录：谁、何时、对什么、做了什么、成功还是失败、耗时多少；管理员可在后台按模块/操作人/结果查询 | 业务代码只加一行注解，日志逻辑零侵入；参数摘要自动脱敏（字段名含 password 一律 `***`）；日志用独立事务写入，**业务回滚也留痕**，写日志失败不影响业务 |
| **并发 / 压测**（扩展·性能与健壮性） | 并发正确性用例 9 个（点赞/关注/注册/审核/重提/评论/删除的幂等与唯一性）+ 读接口压测（800 次请求，输出 QPS 与 p95 延迟） | 并发下"只有一个赢家"；顺带修掉了审核/下架/恢复/重提的 check-then-act 竞态（改成条件更新）与评论并发删除结果不一致的问题 |
| **AI 阅读辅助**（AI 功能） | 笔记总结、适宜人群推荐、笔记评分三个能力；结果**只作参考、必须用户手动确认**才保存到笔记；**一篇笔记最多保存一种**；作者可决定 AI 结果**是否对其他人公开** | 生成与保存是两个接口：生成只写 `t_ai_log`（不碰笔记），确认时才写笔记，且确认只传 `aiLogId`（结果由服务端取，客户端改不了结论）。输入正文截断 4000 字、输出上限 800 token、**每人每天 20 次配额**；提示词强制 JSON 输出并按能力校验（评分必须 1~10 的整数），防注入与幻觉 |
| **AI 调用统计**（AI 功能） | 管理后台「AI 统计」页签 + `GET /api/admin/ai-stats`：总调用、成功/失败、成功率、token 总用量、平均/最大耗时、被确认保存次数、按能力分布、最近 7 天趋势 | 每次调用都落 `t_ai_log`（模型、输入长度、prompt/completion/total token、耗时、成功与否、错误原因、结果原文、是否被确认）；**调用失败也留痕**，便于看失败率与排查 |
| **AI 智能客服**（AI 进阶功能） | 平台答疑助手：新建会话、查看自己的历史会话、切换并继续某个会话；支持多轮追问（理解"它"指代上一轮内容）；长回复折叠、删除二次确认 | 会话归属唯一用户，**所有会话级操作都走同一个归属校验入口**，越权统一 60007 且不返回任何内容；上下文**只按会话取**，同一用户不同会话互不污染；滑动窗口只带最近 10 轮；只存成功的对话（失败不入库、不扣配额），但**每次调用都记审计**；system prompt 限定只答平台使用问题，无关问题礼貌拒绝。另有两条用量护栏：**每人每天 100 条提问**（60009）、**每人每天最多新建 100 个会话**（60011） |

共 **49 个接口操作（40 个路径）**，详见 [接口文档](#五接口文档)。

---

## 二、技术栈（这边是按照学习文档里面的视频学习过程中安装的一些软件以及插件和使用方法，以及我给AI助手接的deepseek）

| 类别 | 技术 | 版本 |
| --- | --- | --- |
| 语言 | Java（编译目标 Java 21） | JDK 25.0.4.1 运行 |
| 框架 | Spring Boot / Spring Framework / 内嵌 Tomcat | 3.5.16 / 6.2.19 / 10.1.55 |
| 数据访问 | MyBatis-Plus（含分页插件所需 mybatis-plus-jsqlparser） | 3.5.17 |
| 数据库 | MySQL | 9.7.2（8.0+ 均可） |
| 鉴权 | JJWT（HS256 签名） | 0.12.7 |
| 密码加密 | spring-security-crypto 的 BCrypt（只引加密库，不引 Security 过滤器链） | 随 Spring Boot 管理 |
| 参数校验 | Jakarta Bean Validation（Hibernate Validator） | 8.0.3 |
| 切面 | Spring AOP（操作日志用注解 + 切面实现，不改业务代码） | 随 Spring Boot 管理 |
| AI 接入 | DeepSeek 开放平台 **OpenAI 兼容接口**（Spring 自带 `RestClient` 直接发 HTTP，**不引第三方 SDK**） | 模型 `deepseek-flash` |
| 接口文档 | springdoc-openapi（Swagger UI） | 2.8.17 |
| 日志 | SLF4J + Logback | 2.0.18 / 1.5.34 |
| 测试 | JUnit 5 + MockMvc + Mockito | 5.12.2 |
| 构建 | Maven | 3.9.16 |

---

## 三、快速开始（这个是我自己额外加的内容，方便演示的时候开启项目）

### 0. 一键启动（Windows）

已经打包过（`target/note-workshop-1.0.0.jar` 存在）的情况下，双击即可：

| 文件 | 作用 |
| --- | --- |
| [tools/start.bat](tools/start.bat) | **一键启动**：检查 MySQL → 检查 8080 是否被占用 → 检查 jar（没有就自动 `mvn package`）→ 读取 `DEEPSEEK_API_KEY` → 后台启动 → 等就绪 → 打开浏览器 |
| [tools/stop.bat](tools/stop.bat) | **停止服务**（关闭占用 8080 的 java 进程） |



启动脚本已经做了兜底：MySQL 没启动会提示去 `services.msc`；8080 已被占用会提示"项目可能已在运行"并直接打开浏览器（不会重复启动）；读不到 Key 会说明"AI 接口返回 60001，其他功能不受影响"。日志写在 `target/app.out.log` 与 `target/app.err.log`。


### 1. 建库建表

```bash
mysql -u root -p < sql/schema.sql
```

脚本使用 `CREATE TABLE IF NOT EXISTS`，可以重复执行，不会删除已有数据。

### 2. 配置数据源

默认连接 `localhost:3306/note_workshop`，账号 `root`。修改 [src/main/resources/application.yml](src/main/resources/application.yml)，或用环境变量覆盖（推荐，避免把密码写进仓库）：

```bash
# Windows PowerShell
$env:DB_USERNAME="root"; $env:DB_PASSWORD="你的密码"
# Linux / macOS
export DB_USERNAME=root DB_PASSWORD=你的密码
```

### 3. 启动

```bash
# 方式一：开发模式
mvn spring-boot:run

# 方式二：打包后运行
mvn clean package -DskipTests
java -jar target/note-workshop-1.0.0.jar
```

启动后访问：

- **前端页面**：<http://localhost:8080/>（首页，可直接注册登录、写笔记、点赞）
- 接口文档（可视化，可直接调试）：<http://localhost:8080/swagger-ui.html>
- OpenAPI 规范：<http://localhost:8080/v3/api-docs>

### 4. 跑测试

```bash
mvn test          # 单元测试：236 个用例
```

---

## 四、配置说明（这边是一些配置相关的说明，包括一些相关文件大小和使用次数的限制条件）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | 8080 | 服务端口 |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/note_workshop...` | 数据库连接串 |
| `spring.datasource.username` / `password` | `root` / `123456` | 支持环境变量 `DB_USERNAME` / `DB_PASSWORD` 覆盖 |
| `spring.servlet.multipart.max-file-size` | 5MB | 单个上传文件上限 |
| `app.jwt.secret` | 开发用默认值 | **生产环境必须用环境变量 `JWT_SECRET` 覆盖**；HS256 要求至少 32 个字符，太短会直接启动失败 |
| `app.jwt.expire-minutes` | 1440 | 令牌有效期（分钟），默认 24 小时 |
| `app.upload.dir` | `./uploads` | 上传根目录，相对路径，换机器无需改代码 |
| `app.upload.url-prefix` | `/uploads` | 图片访问 URL 前缀，与静态资源映射一致 |
| `app.upload.max-image-size` | 5MB | 单张图片大小上限 |
| `app.ai.api-key` | 空 | **只从环境变量 `DEEPSEEK_API_KEY` 读取**，绝不写进代码或提交仓库；为空时 AI 接口返回 `60001`，其他功能不受影响 |
| `app.ai.base-url` / `model` | `https://api.deepseek.com` / `deepseek-flash` | OpenAI 兼容接口地址与模型名 |
| `app.ai.mock` | `false` | 设为 `true` 时用内置模拟实现：不联网、不花 token，接口测试与离线演示用 |
| `app.ai.timeout-seconds` | 30 | 单次调用超时（连接与读取） |
| `app.ai.max-input-chars` | 4000 | 送入模型的正文最大字数（超出截断，控制成本） |
| `app.ai.max-output-tokens` | 800 | 单次生成的最大输出 token |
| `app.ai.daily-quota` | 20 | 每人每天的 AI 调用配额（笔记 AI 用） |
| `app.ai.chat-quota` | 100 | AI 客服每人每天可发多少条提问（**独立配额**，不与笔记 AI 共享；失败不计数） |
| `app.ai.chat-window-turns` | 10 | 客服滑动窗口轮数（1 轮 = 用户消息 + AI 回复），只带最近 N 轮上下文 |
| `app.ai.chat-history-limit` | 50 | 会话详情一次返回最近多少条消息 |
| `app.ai.chat-max-chars` | 1000 | 单条提问的最大字符数，超出返回 60008 并提示当前字数 |
| `app.ai.chat-daily-session-limit` | 100 | 每人**每天**最多新建多少个会话（防止空会话被无限刷；删掉的也计入当天数量，超出返回 60011） |

---

## 五、接口文档（这边是链接的一些接口）

| 形式 | 位置 |
| --- | --- |
| Swagger UI（可视化调试） | 启动后访问 `/swagger-ui.html`，点右上角 Authorize 填入登录返回的 token 即可调受保护接口 |
| OpenAPI 规范快照 | [docs/openapi.json](docs/openapi.json) |
| 可读版接口文档（由 OpenAPI 生成） | [docs/api.md](docs/api.md) |
| Postman 集合（可直接导入运行） | [docs/note-workshop.postman_collection.json](docs/note-workshop.postman_collection.json) —— **10 组 66 个请求**，每个请求都带断言脚本；含 AI 辅助（生成/确认/公开开关/撤销/统计）与 AI 智能客服（新建/列表/详情/发送/删除） |
| 演示截图 | [docs/screenshots/](docs/screenshots/) —— 首页、公开笔记广场、笔记详情（含 AI 面板）、热门作者、Swagger UI |

**接口一览（49 个操作 / 40 个路径）**

| 模块 | 方法与路径 | 鉴权 |
| --- | --- | --- |
| 认证 | `POST /api/auth/register`、`POST /api/auth/login` | 匿名 |
| 用户 | `GET /api/users/me`、`PUT /api/users/me`、`PUT /api/users/me/password`、`POST /api/users/me/avatar` | 登录 |
| 文件 | `POST /api/files/note-images` | 登录 |
| 笔记 | `POST /api/notes`、`GET /api/notes/my`、`PUT /api/notes/{id}`、`DELETE /api/notes/{id}` | 登录（写操作仅作者或管理员） |
| 笔记 | `GET /api/notes/public`、`GET /api/notes/rank`、`GET /api/notes/{id}` | 公开内容可匿名 |
| 点赞 | `POST /api/notes/{id}/like`、`DELETE /api/notes/{id}/like`、`GET /api/likes/my` | 登录 |
| 管理员-用户 | `GET /api/admin/users`、`PUT /api/admin/users/{id}/status` | 仅管理员 |
| 管理员-内容 | `GET /api/admin/notes`、`GET /api/admin/notes/pending`、`PUT /api/admin/notes/{id}/audit`、`PUT /api/admin/notes/{id}/offline`、`PUT /api/admin/notes/{id}/restore` | 仅管理员 |
| 管理员-评论 | `GET /api/admin/comments`、`DELETE /api/admin/comments/{id}` | 仅管理员 |
| 管理员-操作日志 | `GET /api/admin/logs` | 仅管理员 |
| 管理员-AI 统计 | `GET /api/admin/ai-stats` | 仅管理员 |
| 审核-用户侧 | `PUT /api/notes/{id}/resubmit` | 登录（仅作者或管理员） |
| 社区-关注 | `POST /api/users/{id}/follow`、`DELETE /api/users/{id}/follow` | 登录 |
| 社区-作者 | `GET /api/users/{id}`、`GET /api/users/{id}/notes`、`GET /api/users/{id}/followers`、`GET /api/users/{id}/following`、`GET /api/authors/hot` | 匿名可看 |
| 社区-关注流 | `GET /api/feed/following` | 登录 |
| 社区-评论 | `POST /api/notes/{noteId}/comments` | 登录 |
| 社区-评论 | `GET /api/notes/{noteId}/comments`、`DELETE /api/comments/{id}` | 查看可匿名；删除需本人或管理员 |
| AI 辅助 | `POST /api/notes/{id}/ai/{capability}`、`PUT /api/notes/{id}/ai-result`、`DELETE /api/notes/{id}/ai-result`、`PUT /api/notes/{id}/ai-result/visibility` | 登录（仅作者或管理员） |
| AI 智能客服 | `POST /api/ai/chat/sessions`、`GET /api/ai/chat/sessions`、`GET /api/ai/chat/sessions/{id}`、`POST /api/ai/chat/sessions/{id}/messages`、`DELETE /api/ai/chat/sessions/{id}` | 登录（会话归属本人，越权返回 60007 且不返回内容） |

**公开笔记审核规则（扩展部分）**

```
用户新建/编辑公开笔记 ──► PENDING（待审核，只有作者本人与管理员能看到）
                              │
              管理员审核通过 ──┴──► APPROVED（公开列表 / 排行 / 详情对外可见）
              管理员审核驳回 ─────► REJECTED（作者可见驳回原因）
                                     │
                    作者一键重新提交 ─┘ 或 修改内容后自动重提 ──► 回到 PENDING
              APPROVED ──管理员下架──► TAKEN_DOWN（对外不可见；可恢复 → 回到 APPROVED）
```

- 私有笔记**不参与审核**，直接是已通过状态；只有"公开 **且** 已通过"的笔记才会出现在广场、排行、详情、作者主页与关注流。
- 其他人在**任何接口**都拿不到审核状态：访问待审核/已驳回/已下架的笔记一律按"笔记不存在"处理，不暴露存在性；作者与管理员能看到状态与原因。
- 被驳回的笔记有两条重提路径：**改内容**（编辑后自动回到待审核）或**一键重新提交**（`PUT /api/notes/{id}/resubmit`，内容不用改）；只有"已驳回"状态能重提，重复重提返回 20006。
- 账号禁用**立即生效**：登录态校验之后会再按主键读一次用户，被禁用账号即使手里有旧令牌也会被拦下（403 / 10005），且无法重新登录。
- 评论只能发在"公开且已通过审核"的笔记上；**看**评论的规则与笔记详情一致：公开且已通过的笔记任何人（含未登录）都能看，待审核/已驳回/已下架/私有笔记只有**作者本人与管理员**能看（其他人 404 / 20001，不暴露存在性）。
- 也就是说：笔记被下架后，作者在详情页仍然能看到正文与评论区（只是不能再发新评论），而其他人两者都看不到 —— 读与写的门槛分开。

**统一响应格式**

```json
{ "code": 200, "message": "成功", "data": { } }
```

业务码约定：`200` 成功；`400` 参数错误；`401` 未登录；`403` 无权限；`404` 不存在；`405` 方法不支持；`415` 内容类型不支持；`500` 系统错误；`1xxxx` 用户模块（含 10005 账号已禁用、10006 不能禁用自己）；`2xxxx` 笔记模块（含 20005 不在待审核状态、20006 状态不允许该操作）；`3xxxx` 文件模块；`4xxxx` 评论模块（40001 评论不存在、40002 只能删自己的评论、40003 回复目标不合法）；`5xxxx` 关注模块（50001 不能关注自己）。响应同时带对应的 HTTP 状态码，方便接口测试时一眼看出成功失败。

---

## 六、测试（这边是一些测试留痕）

| 类型 | 内容 | 结果 | 记录 |
| --- | --- | --- | --- |
| 单元测试 | JUnit 5 + MockMvc + Mockito，覆盖业务规则、权限判定、JWT、文件校验、异常处理、审核状态机与重提、账号禁用、关注关系、评论树与级联删除、评论区可见性、日志脱敏与长度截断、并发冲突分支、AI 结果解析校验（Markdown 包裹 / 缺字段 / 分数越界 / 非 JSON）、AI 权限与配额、确认保存与撤销、公开开关、统计汇总、AI 客服的会话归属/滑动窗口/标题规则/失败语义/删除级联/每日建会话上限 | **236 个用例全部通过** | `mvn test` |
| 接口测试 | 真实启动应用后逐个调用全部接口（含越权、幂等、参数校验、上传限制、分页与排行、审核流转与重提、禁用生效、下架后内容不泄露、多级回复与评论树、删笔记连带清理、创建响应的时间字段、超长说明校验、操作日志与脱敏、AI 生成/确认/公开开关/撤销/统计与配额、AI 客服的新建会话/多轮上下文/跨用户隔离/超长与空白消息/删除后不可读/每日建会话上限（60011）） | **178 个用例全部通过** | [docs/api-test-report.md](docs/api-test-report.md)、原始日志 [docs/api-test-raw.log](docs/api-test-raw.log) |
| 并发 / 压测 | 30 并发重复点赞/关注、20 并发同名注册、10 并发审核与重提、20 并发评论与点赞、5 并发重复删除；再以 10 并发压测 3 个读接口共 800 次 | **并发用例 9/9 通过，压测 0 失败**（QPS 约 150~420，p95 ≤ 15 ms，随机器负载浮动） | [docs/load-test-report.md](docs/load-test-report.md)、原始日志 [docs/load-test-raw.log](docs/load-test-raw.log) |
| 静态检查 | 主代码 76 个文件 + 测试 16 个文件，`javac -Xlint:all` | **零告警零错误** | 见下方复现命令 |
| 前端页面验证 | 用 Edge 无头模式真实渲染页面，检查 JS 是否取到后端数据并渲染（卡片、分页、详情、评论区、作者卡片、关注按钮、登录态跳转） | **12 项检查全部通过** | 见下 |

前端验证覆盖：首页卡片与排行预览、广场页卡片与分页控件、排行页名次、详情页正文 / AI 阅读辅助面板 / 点赞按钮 / 评论区 / 作者卡片、作者主页（粉丝数与关注按钮）、热门作者排行、登录与注册表单渲染，以及未登录访问"我的笔记""个人资料""关注流""管理后台"时自动跳转登录页。

复现方式：

```bash
# 重新跑一遍接口测试（会启动应用、跑完全部用例、并重新生成 docs 下的记录与 openapi.json）
powershell -ExecutionPolicy Bypass -File tools/api-test.ps1

# 并发正确性用例 + 读接口压测（同样自己启停应用，产出 docs/load-test-report.md）
powershell -ExecutionPolicy Bypass -File tools/load-test.ps1

# 由 openapi.json 生成可读版接口文档 docs/api.md
python tools/gen-api-md.py
```

---

## 七、数据库设计（这个是数据库设计，包括七张实现基础功能的表加上三张AI客服整进来之后加的表，以及各表里面的不同字段，包括每张表都会有的一些id和更新时间修改时间这些）

| 表 | 作用 | 关键设计 |
| --- | --- | --- |
| `t_user` | 用户 | `username` 唯一索引；`password` 存 BCrypt 哈希（60 字符）；`role` 区分 USER / ADMIN；`status`（1 正常 / 0 禁用）由管理员后台使用 |
| `t_note` | 读书笔记 | `user_id` 索引；`(visibility, create_time)` 联合索引服务于公开列表分页；`idx_audit_status (audit_status, create_time)` 服务于待审核队列；`audit_status` / `audit_remark` / `audit_time` 三个审核字段（默认 `APPROVED`，历史数据不受影响）；图书名只存字符串，不建图书表 |
| `t_note_image` | 笔记配图 | `note_id` 索引；一篇笔记 0~N 张图，按 `sort_order` 排序；只存 URL，文件在磁盘 |
| `t_note_like` | 点赞 | `(note_id, user_id)` **唯一索引**防重复点赞；`user_id` 索引服务于"我赞过的" |
| `t_comment` | 笔记评论（扩展） | `parent_id` + `root_id` 支持多级回复；`deleted` 逻辑删除标记，`idx_note_root (note_id, root_id)` 支持按评论树查询与级联删除。删除规则：删顶层评论 = 整棵树；删一条回复 = 只删它自己和它的后代（按 `parent_id` 逐层收集），不会误删别人的评论与兄弟分支 |
| `t_follow` | 关注关系（扩展） | `(follower_id, followee_id)` **唯一索引**从数据库层面防止重复关注；`idx_followee_id` 服务于粉丝数与热门作者排行 |
| `t_operation_log` | 操作日志（扩展·性能与健壮性） | 由 `@OperationLog` 注解 + AOP 切面自动写入；`params` 只存脱敏后的参数摘要（≤500 字符）；三个索引分别服务于按操作人、按时间、按模块+动作查询 |
| `t_ai_log` | AI 调用日志（AI 功能 + 智能客服） | 每次调用一条：`capability`（SUMMARY / AUDIENCE / SCORE / **CHAT**）、`model`、`input_chars`、`prompt_tokens` / `completion_tokens` / `total_tokens`、`cost_ms`、`success` / `error_msg`、`result`、`confirmed`、**`session_id`**（客服有值、笔记为空；`note_id` 对客服为空）；`(user_id, create_time)` 服务于每日配额与统计，`capability`、`success` 服务于分组统计 |
| t_note 的 AI 列 | 指向"当前生效"的 AI 结果 | `ai_type` / `ai_result` / `ai_confirmed_at` / `ai_public`：一篇笔记最多一种结果，历史都在 `t_ai_log`（与审核状态"当前状态在实体、历史在日志"同一思路） |
| `t_ai_chat_session` | AI 客服会话（AI 进阶功能） | 归属唯一用户；`title` 默认"新会话"，出现首条成功消息后取它的前 30 字；`last_message_time` 用于列表倒序（空会话等于创建时间）；`idx_user_time (user_id, last_message_time)` |
| `t_ai_chat_message` | AI 客服消息（AI 进阶功能） | 只存**成功**的一轮对话（失败不入库、不扣配额）；`role` 为 USER / ASSISTANT；AI 回复带 token 与耗时供前端展示成本；`idx_session_id (session_id, id)` 服务于"最近 N 条"与滑动窗口 |

**设计说明**

- 点赞数**不冗余存在笔记表**，一律由 `t_note_like` 实时聚合，避免计数与实际记录不一致。
- 表之间**不加外键**，一致性由 Service 层事务保证：删笔记时同时清理配图记录与点赞记录。
- 关于 `PRIVATE` 的准确语义：**作者本人 + 管理员**可见（管理员可见是为了保证私有笔记的合规），其他普通用户在任何接口下都读不到。
- 扩展部分只做加法：新表用 `CREATE TABLE IF NOT EXISTS`，新列用"先查 `information_schema` 再 ALTER"的幂等写法，`sql/schema.sql` 里没有任何 DROP / DELETE，可以反复执行。
- 写接口返回的数据一律**回查数据库**再组装（新建笔记、编辑笔记、发表评论、审核动作都如此）：`create_time` / `update_time` 由数据库默认值生成，插入后的实体上并没有这两个值，直接用入参实体返回会让响应里的时间字段是 `null`（同一个资源随后查询却有时间，接口自相矛盾）。
- 删笔记时按顺序清理**配图记录 → 点赞记录 → 评论（逻辑删除）→ 笔记本体**，避免留下"笔记已不存在但评论还在"的悬空数据。
- 删除评论的语义：删**顶层评论** = 整棵评论树；删**一条回复** = 只删它自己和它的后代（按 `parent_id` 逐层收集，带层数上限），不会误删别人的评论与兄弟分支。管理后台删违规评论复用同一套实现。

---

## 八、项目结构（这个就是项目的结构，都按照对应的功能分好类了）

```
note-workshop/
├─ pom.xml
├─ README.md
├─ sql/schema.sql                 建表脚本（可重复执行）
├─ docs/                          接口文档与接口测试记录
├─ tools/                         api-test.ps1（接口测试脚本）、gen-api-md.py（接口文档生成脚本）
└─ src/
   ├─ main/java/com/notework/
   │  ├─ NoteWorkshopApplication.java
   │  ├─ common/      统一响应体、错误码、异常处理、分页对象、常量与枚举、业务断言、@OperationLog 注解
   │  ├─ config/      拦截器注册与静态资源映射、MyBatis-Plus 分页插件、Swagger、密码编码器、上传配置
   │  ├─ interceptor/ 登录态拦截器、账号状态拦截器、管理员拦截器、当前用户上下文
   │  ├─ aspect/      OperationLogAspect：注解 + AOP 统一记录操作日志（含参数脱敏）
   │  ├─ ai/          AI 接入层：AiClient（单轮 complete + 多轮 chat）、DeepSeekAiClient（OpenAI 兼容）、
   │  │               MockAiClient（离线/mock 模式）、PromptTemplates（笔记三套提示词）、
   │  │               ChatPromptTemplates（客服系统提示）、AiResultParser（JSON 提取 + 校验）
   │  ├─ util/        JWT 工具、本地文件存储工具
   │  ├─ controller/  Auth、User、Note、Like、File、Comment、Follow、Chat（AI 客服）与三个 Admin 控制器
   │  ├─ service/     业务接口与实现（impl）
   │  ├─ mapper/      数据访问接口（含手写 SQL 的 XML 在 resources/mapper/）
   │  ├─ entity/      表映射对象
   │  └─ dto/         请求与响应对象
   ├─ main/resources/
   │  ├─ application.yml
   │  ├─ mapper/      NoteMapper.xml、NoteLikeMapper.xml（排行、我赞过的）、FollowMapper.xml（热门作者）
   │  └─ static/      前端页面（同源多页，直接由应用托管）
   │     ├─ index.html  plaza.html  rank.html  likes.html  note-my.html
   │     ├─ note-detail.html  note-edit.html  profile.html  login.html  register.html
   │     ├─ author.html  following.html  hot-authors.html  admin.html  chat.html（AI 客服）
   │     ├─ css/style.css
   │     └─ js/       api.js（统一请求封装）、common.js（导航/卡片/分页/审核标签）、
   │                  social.js（作者卡片/关注按钮/评论渲染）、page-chat.js（会话列表与多轮对话）、各页面脚本
   ├─ tools/          start.ps1 / stop.ps1（一键启停）、api-test.ps1（端到端接口测试）、load-test.ps1（并发与压测）、ai-smoke.ps1（AI 真实调用冒烟）、gen-api-md.py
   ├─ docs/           openapi.json、api.md、api-test-report.md、load-test-report.md、Postman 集合、screenshots/（演示截图）
   ├─ sql/schema.sql  建表脚本（幂等，可反复执行）
   └─ test/java/com/notework/   单元测试
```

---

## 九、分层架构与职责边界（这个是执行的架构）

```
浏览器 / 接口测试工具
   ↓ HTTP
Controller   接收请求、参数校验、调用 Service、包装统一响应（不写业务规则）
   ↓
Service      业务规则、权限判断、事务边界、多表组装（不组装 HTTP 响应）
   ↓
Mapper       只做数据库 CRUD 与查询（不写业务、不写权限）
   ↓
MySQL
```

- **统一出口**：所有响应都是 `{code, message, data}`；所有异常都由 `GlobalExceptionHandler` 翻译成 HTTP 响应，业务代码只负责抛业务异常。
- **鉴权**：JWT + 拦截器。拦截器只判断"有没有登录、令牌是否有效、当前用户是谁"，**业务权限一律在 Service 层**（例如"只能改自己的笔记"）。
- **放行规则**：注册、登录、公开笔记列表、排行、笔记详情允许匿名；其余 `/api/**` 必须带 `Authorization: Bearer {token}`。失效令牌访问公开接口时按访客处理，避免前端本地残留的过期令牌把公开页面也挡掉。