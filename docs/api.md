# 笔记工坊 Note-Workshop 接口文档

> 版本 1.0.0　｜　本文档由 `docs/openapi.json`（应用运行时自动生成）转换而来。

面向在校学生的读书笔记系统（基础功能：用户、读书笔记、点赞）。

使用说明：
1. 先调用「认证接口」里的注册、登录，拿到返回的 token；
2. 点右上角 Authorize 按钮，把 token 填进去（不用手写 Bearer 前缀）；
3. 之后即可调试所有需要登录的接口。

未登录时仍可访问：注册、登录、公开笔记列表、笔记详情（公开笔记）、按点赞数排行。

## 鉴权说明

| 方案 | 类型 | 说明 |
| --- | --- | --- |
| `bearerAuth` | http bearer | 登录接口返回的 JWT 令牌 |

请求时在请求头带上：`Authorization: Bearer {token}`（token 来自登录接口）。标注「匿名」的接口不需要令牌；标注「需要令牌」的接口也可以在未登录时访问公开内容，但带不带令牌会影响能看到的数据（例如笔记详情里的私有笔记与 liked / editable 字段）。

## 接口清单

| 分组 | 方法 | 路径 | 说明 | 鉴权 |
| --- | --- | --- | --- | --- |
| 认证接口 | POST | `/api/auth/register` | 注册 | 匿名 |
| 认证接口 | POST | `/api/auth/login` | 登录 | 匿名 |
| 用户资料接口 | GET | `/api/users/me` | 查看当前登录用户资料 | 需要令牌 |
| 用户资料接口 | PUT | `/api/users/me` | 修改个人资料 | 需要令牌 |
| 用户资料接口 | PUT | `/api/users/me/password` | 修改密码 | 需要令牌 |
| 用户资料接口 | POST | `/api/users/me/avatar` | 上传头像 | 需要令牌 |
| 文件上传接口 | POST | `/api/files/note-images` | 上传笔记配图 | 需要令牌 |
| 读书笔记接口 | GET | `/api/notes/{id}` | 笔记详情 | 需要令牌 |
| 读书笔记接口 | PUT | `/api/notes/{id}` | 编辑笔记 | 需要令牌 |
| 读书笔记接口 | DELETE | `/api/notes/{id}` | 删除笔记 | 需要令牌 |
| 读书笔记接口 | PUT | `/api/notes/{id}/resubmit` | 重新提交审核 | 需要令牌 |
| 读书笔记接口 | POST | `/api/notes` | 新建笔记 | 需要令牌 |
| 读书笔记接口 | GET | `/api/notes/rank` | 按点赞数排行（分页） | 匿名 |
| 读书笔记接口 | GET | `/api/notes/public` | 公开笔记列表（分页） | 匿名 |
| 读书笔记接口 | GET | `/api/notes/my` | 我的笔记列表（分页） | 需要令牌 |
| 点赞接口 | POST | `/api/notes/{id}/like` | 点赞笔记 | 需要令牌 |
| 点赞接口 | DELETE | `/api/notes/{id}/like` | 取消点赞 | 需要令牌 |
| 点赞接口 | GET | `/api/likes/my` | 我点赞过的笔记（分页） | 需要令牌 |
| AI 辅助 | PUT | `/api/notes/{noteId}/ai-result` | 确认保存 AI 结果 | 需要令牌 |
| AI 辅助 | DELETE | `/api/notes/{noteId}/ai-result` | 撤销已保存的 AI 结果 | 需要令牌 |
| AI 辅助 | PUT | `/api/notes/{noteId}/ai-result/visibility` | 设置 AI 结果是否公开 | 需要令牌 |
| AI 辅助 | POST | `/api/notes/{noteId}/ai/{capability}` | 生成 AI 结果（预览，不保存） | 需要令牌 |
| 管理员-用户管理 | PUT | `/api/admin/users/{id}/status` | 禁用 / 启用账号 | 需要令牌 |
| 管理员-用户管理 | GET | `/api/admin/users` | 用户列表（分页） | 需要令牌 |
| 管理员-内容管理与审核 | PUT | `/api/admin/notes/{id}/restore` | 恢复已下架的笔记 | 需要令牌 |
| 管理员-内容管理与审核 | PUT | `/api/admin/notes/{id}/offline` | 下架违规笔记 | 需要令牌 |
| 管理员-内容管理与审核 | PUT | `/api/admin/notes/{id}/audit` | 审核笔记 | 需要令牌 |
| 管理员-内容管理与审核 | GET | `/api/admin/notes` | 笔记列表（分页） | 需要令牌 |
| 管理员-内容管理与审核 | GET | `/api/admin/notes/pending` | 待审核队列（分页） | 需要令牌 |
| 社区互动-关注与作者 | POST | `/api/users/{id}/follow` | 关注用户 | 需要令牌 |
| 社区互动-关注与作者 | DELETE | `/api/users/{id}/follow` | 取消关注 | 需要令牌 |
| 社区互动-关注与作者 | GET | `/api/users/{id}` | 作者主页信息 | 需要令牌 |
| 社区互动-关注与作者 | GET | `/api/users/{id}/notes` | 作者的公开笔记 | 需要令牌 |
| 社区互动-关注与作者 | GET | `/api/users/{id}/following` | 关注列表 | 需要令牌 |
| 社区互动-关注与作者 | GET | `/api/users/{id}/followers` | 粉丝列表 | 需要令牌 |
| 社区互动-关注与作者 | GET | `/api/feed/following` | 关注流 | 需要令牌 |
| 社区互动-关注与作者 | GET | `/api/authors/hot` | 热门作者排行 | 需要令牌 |
| 社区互动-评论 | GET | `/api/notes/{noteId}/comments` | 评论列表（含多级回复） | 需要令牌 |
| 社区互动-评论 | POST | `/api/notes/{noteId}/comments` | 发表评论 / 回复 | 需要令牌 |
| 社区互动-评论 | DELETE | `/api/comments/{id}` | 删除评论 | 需要令牌 |
| AI 智能客服 | GET | `/api/ai/chat/sessions` | 我的会话列表 | 需要令牌 |
| AI 智能客服 | POST | `/api/ai/chat/sessions` | 新建会话 | 需要令牌 |
| AI 智能客服 | POST | `/api/ai/chat/sessions/{id}/messages` | 发送消息（多轮对话） | 需要令牌 |
| AI 智能客服 | GET | `/api/ai/chat/sessions/{id}` | 会话详情（含最近消息） | 需要令牌 |
| AI 智能客服 | DELETE | `/api/ai/chat/sessions/{id}` | 删除会话 | 需要令牌 |
| 管理员-操作日志 | GET | `/api/admin/logs` | 操作日志列表（分页） | 需要令牌 |
| 管理员-评论管理 | GET | `/api/admin/comments` | 评论列表（分页） | 需要令牌 |
| 管理员-评论管理 | DELETE | `/api/admin/comments/{id}` | 删除违规评论 | 需要令牌 |
| 管理员-AI 统计 | GET | `/api/admin/ai-stats` | AI 调用统计 | 需要令牌 |

## 认证接口

### 注册

`POST /api/auth/register`　｜　鉴权：匿名（无需令牌）

用户名 4~20 位字母数字下划线，密码 6~20 位；密码以 BCrypt 哈希存储。用户名重复返回 10002。

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 |  |
| `password` | string | 是 |  |
| `nickname` | string | 否 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, username, nickname, avatarUrl, role, roleLabel, createTime |

### 登录

`POST /api/auth/login`　｜　鉴权：匿名（无需令牌）

校验通过后返回 JWT 令牌，前端后续请求放在请求头 Authorization: Bearer {token}。用户名或密码错误统一返回 10003。

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 |  |
| `password` | string | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: token, tokenType, expireSeconds, user |

## 用户资料接口

### 查看当前登录用户资料

`GET /api/users/me`　｜　鉴权：需要令牌（未登录也能访问公开内容）

返回用户名、昵称、头像 URL、角色等；任何接口都不会返回密码字段。

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, username, nickname, avatarUrl, role, roleLabel, createTime |

### 修改个人资料

`PUT /api/users/me`　｜　鉴权：需要令牌（未登录也能访问公开内容）

基础阶段只支持修改昵称，最长 20 个字符。

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `nickname` | string | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, username, nickname, avatarUrl, role, roleLabel, createTime |

### 修改密码

`PUT /api/users/me/password`　｜　鉴权：需要令牌（未登录也能访问公开内容）

必须提供原密码，原密码错误返回 10004。改密成功后已签发的旧令牌不会立即失效。

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `oldPassword` | string | 是 |  |
| `newPassword` | string | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

### 上传头像

`POST /api/users/me/avatar`　｜　鉴权：需要令牌（未登录也能访问公开内容）

multipart/form-data，字段名 file；只允许 jpg/jpeg/png/gif/webp，单张不超过 5MB。图片存本地磁盘，数据库只保存访问 URL。

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | string | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, username, nickname, avatarUrl, role, roleLabel, createTime |

## 文件上传接口

### 上传笔记配图

`POST /api/files/note-images`　｜　鉴权：需要令牌（未登录也能访问公开内容）

multipart/form-data，字段名 file；只允许 jpg/jpeg/png/gif/webp，单张不超过 5MB。返回形如 /uploads/note/2026/10/15/xxx.png 的 URL。

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | string | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: string |

## 读书笔记接口

### 笔记详情

`GET /api/notes/{id}`　｜　鉴权：需要令牌（未登录也能访问公开内容）

返回正文、图书名、全部配图、AI 辅助结果（基础阶段为空）、点赞数与当前用户是否已赞。公开笔记未登录也能看；私有笔记仅作者本人或管理员可看，其他人返回 20004。注意：带不带令牌结果不同（私有可能看不到、liked 与 editable 会变），所以该接口不标注为匿名，在 Swagger 里点过 Authorize 之后会自动带上令牌。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, bookName, content, visibility, visibilityLabel, authorId, authorNickname, authorAvatarUrl, images, aiResult, aiType, aiTypeLabel, aiPublic, aiConfirmedAt, likeCount, liked, commentCount, editable, auditStatus, auditStatusLabel, auditRemark, auditTime, createTime, updateTime |

### 编辑笔记

`PUT /api/notes/{id}`　｜　鉴权：需要令牌（未登录也能访问公开内容）

仅作者本人或管理员，其他人返回 20002。imageUrls 传 null 表示不改配图，传空数组表示清空。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `bookName` | string | 是 | 图书名，最长 100 个字符 |
| `content` | string | 是 | 笔记正文，最长 20000 个字符 |
| `visibility` | string（枚举：PRIVATE / PUBLIC） | 是 | 可见性：PRIVATE 私有 / PUBLIC 公开 |
| `imageUrls` | array<string> | 否 | 配图 URL 列表：不传表示不改动配图，传空数组表示清空，传内容表示整体替换 |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, bookName, content, visibility, visibilityLabel, authorId, authorNickname, authorAvatarUrl, images, aiResult, aiType, aiTypeLabel, aiPublic, aiConfirmedAt, likeCount, liked, commentCount, editable, auditStatus, auditStatusLabel, auditRemark, auditTime, createTime, updateTime |

### 删除笔记

`DELETE /api/notes/{id}`　｜　鉴权：需要令牌（未登录也能访问公开内容）

仅作者本人或管理员。笔记、配图记录、点赞记录一并删除；磁盘上的图片文件暂不清理。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

### 重新提交审核

`PUT /api/notes/{id}/resubmit`　｜　鉴权：需要令牌（未登录也能访问公开内容）

把「已驳回」的公开笔记重新送回待审核队列（不用改内容）。只有作者本人或管理员可操作；私有笔记或非已驳回状态返回 20006。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, bookName, content, visibility, visibilityLabel, authorId, authorNickname, authorAvatarUrl, images, aiResult, aiType, aiTypeLabel, aiPublic, aiConfirmedAt, likeCount, liked, commentCount, editable, auditStatus, auditStatusLabel, auditRemark, auditTime, createTime, updateTime |

### 新建笔记

`POST /api/notes`　｜　鉴权：需要令牌（未登录也能访问公开内容）

必须指定图书名与可见性（PRIVATE 私有 / PUBLIC 公开）；配图先调上传接口拿 URL 再放进 imageUrls，最多 9 张。

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `bookName` | string | 是 | 图书名，最长 100 个字符 |
| `content` | string | 是 | 笔记正文，最长 20000 个字符 |
| `visibility` | string（枚举：PRIVATE / PUBLIC） | 是 | 可见性：PRIVATE 私有（作者与管理员可见）/ PUBLIC 公开（所有人可见） |
| `imageUrls` | array<string> | 否 | 配图 URL 列表（先调用配图上传接口拿到 URL），可为空表示不带配图，最多 9 张 |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, bookName, content, visibility, visibilityLabel, authorId, authorNickname, authorAvatarUrl, images, aiResult, aiType, aiTypeLabel, aiPublic, aiConfirmedAt, likeCount, liked, commentCount, editable, auditStatus, auditStatusLabel, auditRemark, auditTime, createTime, updateTime |

### 按点赞数排行（分页）

`GET /api/notes/rank`　｜　鉴权：匿名（无需令牌）

只统计公开笔记，按点赞数降序、创建时间降序排列；未登录也可以访问。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 公开笔记列表（分页）

`GET /api/notes/public`　｜　鉴权：匿名（无需令牌）

只返回 PUBLIC 的笔记，未登录也可以访问。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 我的笔记列表（分页）

`GET /api/notes/my`　｜　鉴权：需要令牌（未登录也能访问公开内容）

返回当前登录用户的全部笔记，包含私有与公开；page 从 1 开始，size 上限 100。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

## 点赞接口

### 点赞笔记

`POST /api/notes/{id}/like`　｜　鉴权：需要令牌（未登录也能访问公开内容）

只能点赞公开笔记，私有笔记返回 20003；重复点赞按幂等处理。返回该笔记最新点赞数。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: integer |

### 取消点赞

`DELETE /api/notes/{id}/like`　｜　鉴权：需要令牌（未登录也能访问公开内容）

没点过赞也按幂等处理。返回该笔记最新点赞数。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: integer |

### 我点赞过的笔记（分页）

`GET /api/likes/my`　｜　鉴权：需要令牌（未登录也能访问公开内容）

按点赞时间倒序，page 从 1 开始，size 上限 100。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

## AI 辅助

### 确认保存 AI 结果

`PUT /api/notes/{noteId}/ai-result`　｜　鉴权：需要令牌（未登录也能访问公开内容）

只传 aiLogId（不传结果内容，保证用户改不了 AI 的结论）。保存后覆盖这篇笔记之前的 AI 结果 —— 一篇笔记最多保存一种。aiPublic 传 true 表示对其他人公开。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `noteId` | path | integer | 是 |  |

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `aiLogId` | integer | 是 |  |
| `aiPublic` | boolean | 否 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

### 撤销已保存的 AI 结果

`DELETE /api/notes/{noteId}/ai-result`　｜　鉴权：需要令牌（未登录也能访问公开内容）

清空笔记上的 AI 结果，笔记正文不受影响。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `noteId` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

### 设置 AI 结果是否公开

`PUT /api/notes/{noteId}/ai-result/visibility`　｜　鉴权：需要令牌（未登录也能访问公开内容）

作者决定是否让别人看到这篇笔记的 AI 结果。公开的前提是笔记本身公开且已通过审核；关闭后只有作者本人与管理员可见。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `noteId` | path | integer | 是 |  |

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `aiPublic` | boolean | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

### 生成 AI 结果（预览，不保存）

`POST /api/notes/{noteId}/ai/{capability}`　｜　鉴权：需要令牌（未登录也能访问公开内容）

capability 传 SUMMARY / AUDIENCE / SCORE。返回结果与本次生成记录 id（aiLogId），确认保存时要把 aiLogId 传回来。只有作者本人或管理员能调用；每人每天有配额（默认 20 次），未配置 API Key 返回 60001。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `noteId` | path | integer | 是 |  |
| `capability` | path | string | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: aiLogId, capability, capabilityLabel, text, model, promptTokens, completionTokens, totalTokens, costMs, remainingQuota, disclaimer |

## 管理员-用户管理

### 禁用 / 启用账号

`PUT /api/admin/users/{id}/status`　｜　鉴权：需要令牌（未登录也能访问公开内容）

status 传 0 表示禁用、1 表示启用。不能禁用自己的账号（返回 10006）；禁用立即生效，该用户手里已签发的令牌也会被拦下。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, username, nickname, avatarUrl, role, roleLabel, status, statusLabel, createTime |

### 用户列表（分页）

`GET /api/admin/users`　｜　鉴权：需要令牌（未登录也能访问公开内容）

keyword 可按用户名或昵称模糊搜索；status 传 1 只看正常、传 0 只看已禁用、不传看全部。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `keyword` | query | string | 否 |  |
| `status` | query | integer | 否 |  |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

## 管理员-内容管理与审核

### 恢复已下架的笔记

`PUT /api/admin/notes/{id}/restore`　｜　鉴权：需要令牌（未登录也能访问公开内容）

把已下架的笔记恢复为已通过状态，重新对外可见。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, bookName, content, visibility, visibilityLabel, auditStatus, auditStatusLabel, auditRemark, auditTime, authorId, authorNickname, authorAvatarUrl, images, createTime, updateTime |

### 下架违规笔记

`PUT /api/admin/notes/{id}/offline`　｜　鉴权：需要令牌（未登录也能访问公开内容）

只有已通过审核的公开笔记能下架；下架后立即从公开列表、排行与详情中消失。remark 为下架说明，作者可见。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `remark` | string | 否 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, bookName, content, visibility, visibilityLabel, auditStatus, auditStatusLabel, auditRemark, auditTime, authorId, authorNickname, authorAvatarUrl, images, createTime, updateTime |

### 审核笔记

`PUT /api/admin/notes/{id}/audit`　｜　鉴权：需要令牌（未登录也能访问公开内容）

approved 传 true 通过（立即出现在公开广场），传 false 驳回（可带 remark 作为驳回原因，作者可见并可修改后重新提交）。只有待审核状态的笔记能审核，否则返回 20005。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `approved` | boolean | 是 |  |
| `remark` | string | 否 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, bookName, content, visibility, visibilityLabel, auditStatus, auditStatusLabel, auditRemark, auditTime, authorId, authorNickname, authorAvatarUrl, images, createTime, updateTime |

### 笔记列表（分页）

`GET /api/admin/notes`　｜　鉴权：需要令牌（未登录也能访问公开内容）

visibility 传 PRIVATE/PUBLIC 过滤，auditStatus 传 PENDING/APPROVED/REJECTED/TAKEN_DOWN 过滤。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `visibility` | query | string（枚举：PRIVATE / PUBLIC） | 否 |  |
| `auditStatus` | query | string（枚举：PENDING / APPROVED / REJECTED / TAKEN_DOWN） | 否 |  |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 待审核队列（分页）

`GET /api/admin/notes/pending`　｜　鉴权：需要令牌（未登录也能访问公开内容）

按提交时间从早到晚排列（先提交先审）。用户提交的公开笔记不会直接对外可见，先进入这里。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

## 社区互动-关注与作者

### 关注用户

`POST /api/users/{id}/follow`　｜　鉴权：需要令牌（未登录也能访问公开内容）

重复关注按幂等处理；不能关注自己（返回 50001）；未登录返回 401。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

### 取消关注

`DELETE /api/users/{id}/follow`　｜　鉴权：需要令牌（未登录也能访问公开内容）

没关注过时同样返回成功，保证幂等。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

### 作者主页信息

`GET /api/users/{id}`　｜　鉴权：需要令牌（未登录也能访问公开内容）

返回昵称、头像、粉丝数、关注数、公开笔记数，以及当前用户是否已关注（可匿名访问）。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, nickname, avatarUrl, followerCount, followingCount, noteCount, followed, self |

### 作者的公开笔记

`GET /api/users/{id}/notes`　｜　鉴权：需要令牌（未登录也能访问公开内容）

只返回该作者「公开且已通过审核」的笔记，可匿名访问。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 关注列表

`GET /api/users/{id}/following`　｜　鉴权：需要令牌（未登录也能访问公开内容）

他关注了谁，按关注时间倒序分页，可匿名访问。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 粉丝列表

`GET /api/users/{id}/followers`　｜　鉴权：需要令牌（未登录也能访问公开内容）

谁关注了他，按关注时间倒序分页，可匿名访问。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 关注流

`GET /api/feed/following`　｜　鉴权：需要令牌（未登录也能访问公开内容）

我关注的人发布的「公开且已通过审核」的笔记，按发布时间倒序分页；未登录返回 401。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 热门作者排行

`GET /api/authors/hot`　｜　鉴权：需要令牌（未登录也能访问公开内容）

按粉丝数从多到少，粉丝数相同时按用户 id 升序（保证分页稳定），可匿名访问。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

## 社区互动-评论

### 评论列表（含多级回复）

`GET /api/notes/{noteId}/comments`　｜　鉴权：需要令牌（未登录也能访问公开内容）

顶层评论倒序分页，每条评论的所有回复随它一起返回（前端按层级缩进展示），可匿名访问。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `noteId` | path | integer | 是 |  |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 发表评论 / 回复

`POST /api/notes/{noteId}/comments`　｜　鉴权：需要令牌（未登录也能访问公开内容）

parentId 不传表示对笔记发表顶层评论，传了表示回复某条评论（支持多级回复）。只能评论「公开且已通过审核」的笔记，其他情况返回 20001。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `noteId` | path | integer | 是 |  |

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | string | 是 |  |
| `parentId` | integer | 否 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, noteId, userId, nickname, avatarUrl, parentId, content, deletable, createTime, replies |

### 删除评论

`DELETE /api/comments/{id}`　｜　鉴权：需要令牌（未登录也能访问公开内容）

只能删自己的评论（管理员可删任意评论，否则返回 40002）；逻辑删除，删父评论时连同它下面的所有回复一起删除。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

## AI 智能客服

### 我的会话列表

`GET /api/ai/chat/sessions`　｜　鉴权：需要令牌（未登录也能访问公开内容）

只返回当前登录用户的会话，按最后消息时间倒序；空会话按创建时间参与排序。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 新建会话

`POST /api/ai/chat/sessions`　｜　鉴权：需要令牌（未登录也能访问公开内容）

创建一个空会话，标题为「新会话」；之后发消息时由首条成功消息取名。

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: id, title, messageCount, lastMessageTime, createTime |

### 发送消息（多轮对话）

`POST /api/ai/chat/sessions/{id}/messages`　｜　鉴权：需要令牌（未登录也能访问公开内容）

带最近 10 轮上下文调用模型并返回回复。单条消息上限 1000 字；每人每天 100 条提问配额（独立于笔记 AI）；调用失败时不写对话记录、不扣配额，但会计入 AI 统计的失败次数。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**请求体**（`application/json`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | string | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: userMessage, reply, sessionTitle, remainingQuota |

### 会话详情（含最近消息）

`GET /api/ai/chat/sessions/{id}`　｜　鉴权：需要令牌（未登录也能访问公开内容）

返回会话头信息与最近若干条消息（默认 50 条，按时间正序）。会话不存在或不属于当前用户时返回 60007，且不返回任何内容。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: session, messages |

### 删除会话

`DELETE /api/ai/chat/sessions/{id}`　｜　鉴权：需要令牌（未登录也能访问公开内容）

物理删除会话及其全部消息（聊天记录属于用户私有数据，删干净）。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

## 管理员-操作日志

### 操作日志列表（分页）

`GET /api/admin/logs`　｜　鉴权：需要令牌（未登录也能访问公开内容）

可按 module（模块）、username（操作人用户名，模糊匹配）、success（true 只看成功 / false 只看失败）过滤，按发生时间倒序返回。参数摘要在写入时已脱敏，密码一类字段一律是 ***。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `module` | query | string | 否 |  |
| `username` | query | string | 否 |  |
| `success` | query | boolean | 否 |  |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

## 管理员-评论管理

### 评论列表（分页）

`GET /api/admin/comments`　｜　鉴权：需要令牌（未登录也能访问公开内容）

可按 noteId 过滤某篇笔记的评论；只列出未被删除的评论，附带书名与评论人昵称，便于判定违规。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `noteId` | query | integer | 否 |  |
| `pageQuery` | query | object | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: list, total, page, size |

### 删除违规评论

`DELETE /api/admin/comments/{id}`　｜　鉴权：需要令牌（未登录也能访问公开内容）

逻辑删除（数据库里保留记录，只是标记为已删除）；删除父评论时会连同它下面的所有回复一起删除。

**请求参数**

| 名称 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | path | integer | 是 |  |

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: object |

## 管理员-AI 统计

### AI 调用统计

`GET /api/admin/ai-stats`　｜　鉴权：需要令牌（未登录也能访问公开内容）

返回总调用次数、成功/失败数、成功率、token 总用量、平均与最大耗时、被确认保存的次数，以及按能力（总结/适宜人群/评分）和最近 7 天的分布。

**响应**

| HTTP | 说明 | 数据结构 |
| --- | --- | --- |
| 200 | OK | data: totalCalls, successCalls, failedCalls, successRate, totalTokens, avgCostMs, maxCostMs, confirmedCalls, model, mock, byCapability, recentDays |
