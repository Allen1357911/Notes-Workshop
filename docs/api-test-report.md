# 笔记工坊 Note-Workshop 接口测试记录

> 本文档由 [tools/api-test.ps1](../tools/api-test.ps1) 自动生成，记录真实运行结果的留痕。

## 一、测试环境

| 项目 | 内容 |
| --- | --- |
| 被测应用 | note-workshop 1.0.0（Spring Boot 3.5.16） |
| 服务地址 | http://127.0.0.1:8080 |
| 数据库 | MySQL 9.7.2，库名 note_workshop |
| 运行环境 | JDK 25.0.4.1（编译目标 Java 21） |
| 测试时间 | 2026-10-22 19:24:12 |
| 测试方式 | 真实启动应用后按 HTTP 协议逐个调用接口，校验 HTTP 状态码与业务 code |
| 原始日志 | 同目录 api-test-raw.log |
| 接口文档 | 同目录 openapi.json 与 api.md，或应用启动后访问 /swagger-ui.html |

## 二、测试结果汇总

| 用例总数 | 通过 | 失败 |
| --- | --- | --- |
| 185 | 185 | 0 |

## 三、用例明细

| # | 用例 | 方法 | 路径 | 预期 | 实际 | 附加校验 | 结果 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 注册成功 | POST | /api/auth/register | HTTP 200 / code 200 | HTTP 200 / code 200 | username=reg_77343，响应不含 password 字段 | 通过 |
| 2 | 注册失败：用户名重复 | POST | /api/auth/register | HTTP 400 / code 10002 | HTTP 400 / code 10002 | — | 通过 |
| 3 | 注册失败：用户名不合法 | POST | /api/auth/register | HTTP 400 / code 400 | HTTP 400 / code 400 | 用户名只能是 4~20 位字母、数字或下划线 | 通过 |
| 4 | 注册失败：密码过短 | POST | /api/auth/register | HTTP 400 / code 400 | HTTP 400 / code 400 | — | 通过 |
| 5 | 登录失败：密码错误 | POST | /api/auth/login | HTTP 400 / code 10003 | HTTP 400 / code 10003 | — | 通过 |
| 6 | 登录失败：用户不存在（提示与密码错误一致，不暴露用户是否存在） | POST | /api/auth/login | HTTP 400 / code 10003 | HTTP 400 / code 10003 | — | 通过 |
| 7 | 查看我的资料（带令牌） | GET | /api/users/me | HTTP 200 / code 200 | HTTP 200 / code 200 | username=doc_author_77343，响应不含 password 字段 | 通过 |
| 8 | 查看我的资料（不带令牌） | GET | /api/users/me | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 9 | 查看我的资料（伪造令牌） | GET | /api/users/me | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 10 | 修改昵称 | PUT | /api/users/me | HTTP 200 / code 200 | HTTP 200 / code 200 | nickname=改过的昵称 | 通过 |
| 11 | 修改昵称失败：昵称为空 | PUT | /api/users/me | HTTP 400 / code 400 | HTTP 400 / code 400 | — | 通过 |
| 12 | 修改密码失败：原密码错误 | PUT | /api/users/me/password | HTTP 400 / code 10004 | HTTP 400 / code 10004 | — | 通过 |
| 13 | 修改密码成功 | PUT | /api/users/me/password | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 14 | 改密后：新密码可以登录 | POST | /api/auth/login | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 15 | 改密后：旧密码不能再登录 | POST | /api/auth/login | HTTP 400 / code 10003 | HTTP 400 / code 10003 | — | 通过 |
| 16 | 上传头像成功 | POST | /api/users/me/avatar | HTTP 200 / code 200 | HTTP 200 / code 200 | avatarUrl=/uploads/avatar/2026/10/22/4bd0977541df4a26b25da0ef8392d055.png | 通过 |
| 17 | 上传后的头像可通过 URL 访问 | GET | /uploads/avatar/2026/10/22/4bd0977541df4a26b25da0ef8392d055.png | HTTP 200 | HTTP 200 | 静态资源映射生效 | 通过 |
| 18 | 上传失败：非图片格式 | POST | /api/users/me/avatar | HTTP 400 / code 30002 | code 30002 | txt 文件被拒绝 | 通过 |
| 19 | 上传失败：超过 5MB 大小限制 | POST | /api/users/me/avatar | HTTP 400 / code 30003 | code 30003 | 6MB 图片被拒绝 | 通过 |
| 20 | 上传笔记配图成功 | POST | /api/files/note-images | HTTP 200 / code 200 | HTTP 200 / code 200 | url=/uploads/note/2026/10/22/13818adb66d8494f99200afd8deba460.png | 通过 |
| 21 | 新建公开笔记（带 2 张配图，同一 URL 应去重） | POST | /api/notes | HTTP 200 / code 200 | HTTP 200 / code 200 | visibility=PUBLIC，配图（去重后）=1，createTime=2026-10-22T19:24:07 | 通过 |
| 22 | 新建私有笔记 | POST | /api/notes | HTTP 200 / code 200 | HTTP 200 / code 200 | visibility=PRIVATE，editable=True | 通过 |
| 23 | 新建笔记失败：缺图书名 | POST | /api/notes | HTTP 400 / code 400 | HTTP 400 / code 400 | 图书名不能为空 | 通过 |
| 24 | 新建笔记失败：可见性非法值 | POST | /api/notes | HTTP 400 / code 400 | HTTP 400 / code 400 | — | 通过 |
| 25 | 新建笔记失败：图书名没有书名号 | POST | /api/notes | HTTP 400 / code 400 | HTTP 400 / code 400 | 图书名要用书名号括起来，例如《活着》 | 通过 |
| 26 | 新建笔记失败：书名号不完整（只有前半） | POST | /api/notes | HTTP 400 / code 400 | HTTP 400 / code 400 | 图书名要用书名号括起来，例如《活着》 | 通过 |
| 27 | 新建笔记失败：书名号里是空的 | POST | /api/notes | HTTP 400 / code 400 | HTTP 400 / code 400 | 图书名要用书名号括起来，例如《活着》 | 通过 |
| 28 | 新建笔记：书名号后面可以跟说明（《老人与海》读后感） | POST | /api/notes | HTTP 200 / code 200 | HTTP 200 / code 200 | 保存后的书名=《老人与海》读后感 | 通过 |
| 29 | 清理书名后缀测试用的笔记 | DELETE | /api/notes/185 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 30 | 新建笔记：书名前后有空格也能通过（后端会 trim） | POST | /api/notes | HTTP 200 / code 200 | HTTP 200 / code 200 | 保存后的书名=《空格测试》 | 通过 |
| 31 | 清理空格测试用的笔记 | DELETE | /api/notes/186 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 32 | 新建笔记失败：配图地址来自外站 | POST | /api/notes | HTTP 400 / code 400 | HTTP 400 / code 400 | 配图地址必须来自本系统的图片上传接口 | 通过 |
| 33 | 新建笔记失败：未登录 | POST | /api/notes | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 34 | 公开笔记列表（匿名可访问，且不含待审核与私有笔记） | GET | /api/notes/public?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | total=6，列表不含私有笔记 id=184 | 通过 |
| 35 | 我的笔记列表（分页，含私有与公开） | GET | /api/notes/my?page=1&size=1 | HTTP 200 / code 200 | HTTP 200 / code 200 | total=2，page=1，size=1，本页条数=1 | 通过 |
| 36 | 我的笔记列表失败：未登录 | GET | /api/notes/my?page=1&size=10 | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 37 | 笔记详情失败：他人看私有笔记 | GET | /api/notes/184 | HTTP 403 / code 20004 | HTTP 403 / code 20004 | 无权查看他人的私有笔记 | 通过 |
| 38 | 笔记详情：管理员可看私有笔记 | GET | /api/notes/184 | HTTP 200 / code 200 | HTTP 200 / code 200 | editable=True（管理员可管理） | 通过 |
| 39 | 笔记详情失败：笔记不存在 | GET | /api/notes/99999999 | HTTP 404 / code 20001 | HTTP 404 / code 20001 | — | 通过 |
| 40 | 待审核的公开笔记：匿名看不到（按不存在处理） | GET | /api/notes/183 | HTTP 404 / code 20001 | HTTP 404 / code 20001 | 未审核前匿名访问被拒绝 | 通过 |
| 41 | 待审核队列里能看到这篇笔记 | GET | /api/admin/notes/pending?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 队列 total=2，包含 id=183 | 通过 |
| 42 | 管理员审核通过：笔记进入已通过状态 | PUT | /api/admin/notes/183/audit | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=APPROVED | 通过 |
| 43 | 审核通过后：匿名公开列表能看到它了 | GET | /api/notes/public?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 列表包含 id=183 | 通过 |
| 44 | 笔记详情（匿名看公开笔记，editable=false、aiResult 为空） | GET | /api/notes/183 | HTTP 200 / code 200 | HTTP 200 / code 200 | editable=False，aiResult 为空，其他人看不到审核状态 | 通过 |
| 45 | 编辑笔记失败：他人修改 | PUT | /api/notes/183 | HTTP 403 / code 20002 | HTTP 403 / code 20002 | — | 通过 |
| 46 | 编辑笔记：管理员可改他人笔记 | PUT | /api/notes/183 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 47 | 编辑笔记：作者清空配图（imageUrls 传空数组） | PUT | /api/notes/183 | HTTP 200 / code 200 | HTTP 200 / code 200 | 配图数=0 | 通过 |
| 48 | 编辑后重新审核通过（后续点赞与详情断言的前提） | PUT | /api/admin/notes/183/audit | HTTP 200 / code 200 | HTTP 200 / code 200 | 编辑后重新审核 → APPROVED | 通过 |
| 49 | 点赞公开笔记 | POST | /api/notes/183/like | HTTP 200 / code 200 | HTTP 200 / code 200 | 最新点赞数=1 | 通过 |
| 50 | 重复点赞（幂等，仍为 1） | POST | /api/notes/183/like | HTTP 200 / code 200 | HTTP 200 / code 200 | 最新点赞数=1 | 通过 |
| 51 | 点赞失败：私有笔记不可点赞 | POST | /api/notes/184/like | HTTP 400 / code 20003 | HTTP 400 / code 20003 | — | 通过 |
| 52 | 点赞失败：未登录 | POST | /api/notes/183/like | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 53 | 笔记详情显示真实点赞数与已赞状态 | GET | /api/notes/183 | HTTP 200 / code 200 | HTTP 200 / code 200 | likeCount=1，liked=True | 通过 |
| 54 | 我点赞过的笔记（分页） | GET | /api/likes/my?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | total=1，首条 id=183，赞数=1 | 通过 |
| 55 | 我点赞过的笔记失败：未登录 | GET | /api/likes/my | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 56 | 按点赞数排行（匿名可访问） | GET | /api/notes/rank?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | 首条 id=151，赞数=2（按赞数降序） | 通过 |
| 57 | 排行榜不含私有笔记 | GET | /api/notes/rank | 私有笔记不出现 | 含私有笔记=False | 匿名接口不泄露私有内容 | 通过 |
| 58 | 取消点赞 | DELETE | /api/notes/183/like | HTTP 200 / code 200 | HTTP 200 / code 200 | 最新点赞数=0 | 通过 |
| 59 | 重复取消点赞（幂等） | DELETE | /api/notes/183/like | HTTP 200 / code 200 | HTTP 200 / code 200 | 最新点赞数=0 | 通过 |
| 60 | 删除笔记失败：他人删除 | DELETE | /api/notes/183 | HTTP 403 / code 20002 | HTTP 403 / code 20002 | — | 通过 |
| 61 | 删除笔记（作者本人） | DELETE | /api/notes/183 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 62 | 删除后查详情返回 20001 | GET | /api/notes/183 | HTTP 404 / code 20001 | HTTP 404 / code 20001 | — | 通过 |
| 63 | 请求方法不支持返回 405（GET 打只有 POST 的接口） | GET | /api/notes | HTTP 405 / code 405 | HTTP 405 / code 405 | — | 通过 |
| 64 | 访问不存在的地址返回 404 | GET | /not-exist-page | HTTP 404 / code 404 | HTTP 404 / code 404 | — | 通过 |
| 65 | 作者再发一篇公开笔记（自动进入待审核） | POST | /api/notes | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=PENDING | 通过 |
| 66 | 管理员驳回：记录驳回原因 | PUT | /api/admin/notes/187/audit | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=REJECTED，原因=请补充自己的思考，不要只摘抄原文 | 通过 |
| 67 | 作者能看到自己笔记的驳回原因 | GET | /api/notes/187 | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=REJECTED | 通过 |
| 68 | 作者修改后重新提交：回到待审核并清掉上次的原因 | PUT | /api/notes/187 | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=PENDING，原因已清空 | 通过 |
| 69 | 重新提交后可以再次审核通过 | PUT | /api/admin/notes/187/audit | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=APPROVED | 通过 |
| 70 | 重复审核（已不是待审核状态）：20005 | PUT | /api/admin/notes/187/audit | HTTP 400 / code 20005 | HTTP 400 / code 20005 | — | 通过 |
| 71 | 新建一篇公开笔记用于下架演示 | POST | /api/notes | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=PENDING | 通过 |
| 72 | 先审核通过（下架的前提） | PUT | /api/admin/notes/188/audit | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 73 | 其他人点赞这篇笔记（后面验证下架后不再泄露） | POST | /api/notes/188/like | HTTP 200 / code 200 | HTTP 200 / code 200 | 点赞数=1 | 通过 |
| 74 | 下架说明超过 255 字：400（请求体校验生效，不能变成 500） | PUT | /api/admin/notes/188/offline | HTTP 400 / code 400 | HTTP 400 / code 400 | — | 通过 |
| 75 | 管理员下架违规笔记（已通过 → 已下架） | PUT | /api/admin/notes/188/offline | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=TAKEN_DOWN，说明=包含违规内容，先下架 | 通过 |
| 76 | 下架后：匿名看不到这篇笔记 | GET | /api/notes/188 | HTTP 404 / code 20001 | HTTP 404 / code 20001 | — | 通过 |
| 77 | 下架后：排行榜里也没有它 | GET | /api/notes/rank?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 排行榜已剔除已下架笔记 | 通过 |
| 78 | 下架后：点过赞的人在【我赞过的】里也看不到它了（防内容泄露） | GET | /api/likes/my?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 我赞过的 total=0，已剔除已下架笔记 | 通过 |
| 79 | 管理员恢复上架：重新对外可见 | PUT | /api/admin/notes/188/restore | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=APPROVED | 通过 |
| 80 | 恢复后：匿名又能看到它了 | GET | /api/notes/188 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 81 | 恢复后：点过赞的人也能在【我赞过的】里重新看到它 | GET | /api/likes/my?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 恢复后重新出现在我赞过的列表里 | 通过 |
| 82 | 下架不存在的笔记：404 | PUT | /api/admin/notes/99999999/offline | HTTP 404 / code 20001 | HTTP 404 / code 20001 | — | 通过 |
| 83 | 后台接口：未登录 401 | GET | /api/admin/users?page=1&size=10 | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 84 | 后台接口：普通用户 403（需要管理员权限） | GET | /api/admin/users?page=1&size=10 | HTTP 403 / code 403 | HTTP 403 / code 403 | 需要管理员权限 | 通过 |
| 85 | 管理员查看用户列表（按用户名搜索，不返回密码） | GET | /api/admin/users?page=1&size=10&keyword=doc_other_77343 | HTTP 200 / code 200 | HTTP 200 / code 200 | 命中 1 个用户 | 通过 |
| 86 | 管理员不能禁用自己的账号（10006） | PUT | /api/admin/users/422/status | HTTP 400 / code 10006 | HTTP 400 / code 10006 | — | 通过 |
| 87 | 管理员禁用普通用户 | PUT | /api/admin/users/421/status | HTTP 200 / code 200 | HTTP 200 / code 200 | status=0（已禁用） | 通过 |
| 88 | 被禁用的用户：已签发的旧令牌立刻失效 | GET | /api/users/me | HTTP 403 / code 10005 | HTTP 403 / code 10005 | 账号已被禁用，请联系管理员 | 通过 |
| 89 | 被禁用的用户：不能重新登录 | POST | /api/auth/login | HTTP 403 / code 10005 | HTTP 403 / code 10005 | — | 通过 |
| 90 | 管理员重新启用账号 | PUT | /api/admin/users/421/status | HTTP 200 / code 200 | HTTP 200 / code 200 | status=1 | 通过 |
| 91 | 启用后：该用户可以重新登录 | POST | /api/auth/login | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 92 | 管理员查看评论列表（评论功能属社区互动模块，当前为空分页） | GET | /api/admin/comments?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 93 | 删除不存在的评论：404（40001） | DELETE | /api/admin/comments/99999999 | HTTP 404 / code 40001 | HTTP 404 / code 40001 | — | 通过 |
| 94 | 操作日志：未登录 401 | GET | /api/admin/logs?page=1&size=10 | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 95 | 操作日志：普通用户 403 | GET | /api/admin/logs?page=1&size=10 | HTTP 403 / code 403 | HTTP 403 / code 403 | — | 通过 |
| 96 | 操作日志：记录了刚才的审核动作（按模块过滤） | GET | /api/admin/logs?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 日志共 45 条，含 AOP 自动记录的「审核笔记」 | 通过 |
| 97 | 操作日志：密码字段已脱敏（日志里不出现明文密码） | GET | /api/admin/logs?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 日志文本里不含明文密码（检查 45 条） | 通过 |
| 98 | 操作日志：失败的操作也留痕（success=false） | GET | /api/admin/logs?success=false&page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 失败记录 13 条，例如越权或状态冲突的尝试 | 通过 |
| 99 | 作者新建公开笔记（进入待审核） | POST | /api/notes | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=PENDING | 通过 |
| 100 | 待审核的公开笔记不能被评论（按不存在处理） | POST | /api/notes/189/comments | HTTP 404 / code 20001 | HTTP 404 / code 20001 | — | 通过 |
| 101 | 管理员驳回：作者可见驳回原因 | PUT | /api/admin/notes/189/audit | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=REJECTED，原因=请补充自己的思考 | 通过 |
| 102 | 作者重新提交（不改内容也可以重提） | PUT | /api/notes/189/resubmit | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=PENDING，原因已清空 | 通过 |
| 103 | 重复重提（已是待审核状态）：20006 | PUT | /api/notes/189/resubmit | HTTP 400 / code 20006 | HTTP 400 / code 20006 | — | 通过 |
| 104 | 他人不能替作者重提：20002 | PUT | /api/notes/189/resubmit | HTTP 403 / code 20002 | HTTP 403 / code 20002 | — | 通过 |
| 105 | 重提后管理员审核通过 | PUT | /api/admin/notes/189/audit | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=APPROVED | 通过 |
| 106 | 作者主页公开笔记列表里能看到它（可匿名） | GET | /api/users/420/notes?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 作者公开笔记 total=3 | 通过 |
| 107 | 关注自己：50001 | POST | /api/users/421/follow | HTTP 400 / code 50001 | HTTP 400 / code 50001 | — | 通过 |
| 108 | 关注不存在的用户：10001 | POST | /api/users/99999999/follow | HTTP 404 / code 10001 | HTTP 404 / code 10001 | — | 通过 |
| 109 | 普通用户关注作者 | POST | /api/users/420/follow | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 110 | 重复关注：幂等，仍返回成功 | POST | /api/users/420/follow | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 111 | 未登录不能关注：401 | POST | /api/users/420/follow | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 112 | 作者主页（粉丝视角）：粉丝数 1 且 followed=true | GET | /api/users/420 | HTTP 200 / code 200 | HTTP 200 / code 200 | 粉丝=1，已关注=True，公开笔记=3 | 通过 |
| 113 | 作者主页（匿名视角）：followed=false | GET | /api/users/420 | HTTP 200 / code 200 | HTTP 200 / code 200 | 匿名看到的粉丝数=1，followed=False | 通过 |
| 114 | 作者主页（本人视角）：self=true | GET | /api/users/420 | HTTP 200 / code 200 | HTTP 200 / code 200 | self=True | 通过 |
| 115 | 粉丝列表（匿名可看） | GET | /api/users/420/followers?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | 粉丝数=1 | 通过 |
| 116 | 关注列表：能看到他关注了谁 | GET | /api/users/421/following?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | 关注了 1 人 | 通过 |
| 117 | 热门作者排行：该作者上榜且粉丝数正确 | GET | /api/authors/hot?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 热门作者排行包含该作者 | 通过 |
| 118 | 关注流：粉丝能看到作者的公开笔记 | GET | /api/feed/following?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 关注流 total=3 | 通过 |
| 119 | 关注流必须登录：401 | GET | /api/feed/following | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 120 | 取消关注 | DELETE | /api/users/420/follow | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 121 | 取关后：关注流里没有他的笔记了 | GET | /api/feed/following?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 关注流 total=0 | 通过 |
| 122 | 评论列表（匿名可看，此时为空） | GET | /api/notes/189/comments?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | total=0 | 通过 |
| 123 | 未登录不能发评论：401 | POST | /api/notes/189/comments | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 124 | 评论内容为空：400 | POST | /api/notes/189/comments | HTTP 400 / code 400 | HTTP 400 / code 400 | — | 通过 |
| 125 | 发表顶层评论 | POST | /api/notes/189/comments | HTTP 200 / code 200 | HTTP 200 / code 200 | 评论 id=236，可删除=True，createTime=2026-10-22T19:24:09 | 通过 |
| 126 | 回复评论（一级回复） | POST | /api/notes/189/comments | HTTP 200 / code 200 | HTTP 200 / code 200 | parentId=236 | 通过 |
| 127 | 回复回复（多级回复） | POST | /api/notes/189/comments | HTTP 200 / code 200 | HTTP 200 / code 200 | 多级回复 parentId=237 | 通过 |
| 128 | 回复别的笔记下的评论：40003 | POST | /api/notes/188/comments | HTTP 400 / code 40003 | HTTP 400 / code 40003 | — | 通过 |
| 129 | 评论列表：顶层评论与多级回复组装成树 | GET | /api/notes/189/comments?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | 顶层=1，一级回复=1，二级回复=1 | 通过 |
| 130 | 笔记详情里的评论数正确 | GET | /api/notes/189 | HTTP 200 / code 200 | HTTP 200 / code 200 | commentCount=3 | 通过 |
| 131 | 删除他人评论：40002 | DELETE | /api/comments/236 | HTTP 403 / code 40002 | HTTP 403 / code 40002 | — | 通过 |
| 132 | 管理员删除违规评论（此时评论树还在） | DELETE | /api/comments/237 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 133 | 删除自己的顶层评论：连同剩余子回复一起删除 | DELETE | /api/comments/236 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 134 | 删除后：评论树整体消失 | GET | /api/notes/189/comments?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | total=0 | 通过 |
| 135 | 删除不存在的评论：40001 | DELETE | /api/comments/99999999 | HTTP 404 / code 40001 | HTTP 404 / code 40001 | — | 通过 |
| 136 | 评论私有笔记：按不存在处理（20001） | POST | /api/notes/184/comments | HTTP 404 / code 20001 | HTTP 404 / code 20001 | — | 通过 |
| 137 | 新建一篇笔记并审核通过（验证删笔记连带清理评论） | POST | /api/notes | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=PENDING | 通过 |
| 138 | 审核通过它 | PUT | /api/admin/notes/190/audit | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 139 | 在它下面发一条评论 | POST | /api/notes/190/comments | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 140 | 管理员评论列表里能看到它 | GET | /api/admin/comments?noteId=190&page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | 该笔记的评论数=1 | 通过 |
| 141 | 下架这篇笔记（验证下架后评论区的可见性） | PUT | /api/admin/notes/190/offline | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 142 | 下架后：作者本人仍能看到这篇笔记的评论区 | GET | /api/notes/190/comments?page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | 作者可见评论数=1 | 通过 |
| 143 | 下架后：陌生人在评论区按不存在处理（20001） | GET | /api/notes/190/comments?page=1&size=10 | HTTP 404 / code 20001 | HTTP 404 / code 20001 | — | 通过 |
| 144 | 下架后：不能再发新评论（20001） | POST | /api/notes/190/comments | HTTP 404 / code 20001 | HTTP 404 / code 20001 | — | 通过 |
| 145 | 作者删除这篇笔记 | DELETE | /api/notes/190 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 146 | 删笔记后：评论被一起清理，不留悬空记录 | GET | /api/admin/comments?noteId=190&page=1&size=10 | HTTP 200 / code 200 | HTTP 200 / code 200 | 该笔记的评论数=0 | 通过 |
| 147 | AI：新建一篇公开笔记 | POST | /api/notes | HTTP 200 / code 200 | HTTP 200 / code 200 | auditStatus=PENDING | 通过 |
| 148 | AI：先审核通过这篇笔记 | PUT | /api/admin/notes/191/audit | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 149 | AI：未登录不能调用（401） | POST | /api/notes/191/ai/SUMMARY | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 150 | AI：不能对别人的笔记调用（20002） | POST | /api/notes/191/ai/SUMMARY | HTTP 403 / code 20002 | HTTP 403 / code 20002 | — | 通过 |
| 151 | AI：非法 capability → 400 | POST | /api/notes/191/ai/UNKNOWN | HTTP 400 / code 400 | HTTP 400 / code 400 | — | 通过 |
| 152 | AI：生成总结（返回 aiLogId / token / 剩余配额） | POST | /api/notes/191/ai/SUMMARY | HTTP 200 / code 200 | HTTP 200 / code 200 | aiLogId=54，tokens=151，今日剩余 19 次 | 通过 |
| 153 | AI：生成评分 | POST | /api/notes/191/ai/SCORE | HTTP 200 / code 200 | HTTP 200 / code 200 | 预览内容：评分：8/10 / 理由：有自己的观点，不只是摘抄原文；结构与层次清楚 / 改进建议：可以补一两个书中的具体例子，让论点更有说服力。 | 通过 |
| 154 | AI：确认保存（只传 aiLogId，结果由服务端取，客户端改不了） | PUT | /api/notes/191/ai-result | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 155 | AI：作者详情页能看到已保存结果 | GET | /api/notes/191 | HTTP 200 / code 200 | HTTP 200 / code 200 | aiType=SCORE，公开=False | 通过 |
| 156 | AI：默认不公开，匿名看不到 AI 结果 | GET | /api/notes/191 | HTTP 200 / code 200 | HTTP 200 / code 200 | 匿名拿到的 aiResult 为空 | 通过 |
| 157 | AI：作者把 AI 结果设为公开 | PUT | /api/notes/191/ai-result/visibility | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 158 | AI：公开后匿名也能看到 AI 结果 | GET | /api/notes/191 | HTTP 200 / code 200 | HTTP 200 / code 200 | 匿名能看到作者公开的 AI 结果 | 通过 |
| 159 | AI：确认不存在的生成记录 → 60003 | PUT | /api/notes/191/ai-result | HTTP 404 / code 60003 | HTTP 404 / code 60003 | — | 通过 |
| 160 | AI：撤销已保存的结果 | DELETE | /api/notes/191/ai-result | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 161 | AI：撤销后详情里没有结果了 | GET | /api/notes/191 | HTTP 200 / code 200 | HTTP 200 / code 200 | aiResult 已清空 | 通过 |
| 162 | AI 统计：未登录 401 | GET | /api/admin/ai-stats | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 163 | AI 统计：普通用户 403 | GET | /api/admin/ai-stats | HTTP 403 / code 403 | HTTP 403 / code 403 | — | 通过 |
| 164 | AI 统计：管理员可查，次数与成功率正确 | GET | /api/admin/ai-stats | HTTP 200 / code 200 | HTTP 200 / code 200 | 总调用 34 次，成功率 100.0%，模型 deepseek-flash | 通过 |
| 165 | AI：清理测试笔记 | DELETE | /api/notes/191 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 166 | AI 客服：未登录新建会话 → 401 | POST | /api/ai/chat/sessions | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 167 | AI 客服：未登录查会话列表 → 401 | GET | /api/ai/chat/sessions | HTTP 401 / code 401 | HTTP 401 / code 401 | — | 通过 |
| 168 | AI 客服：新建空会话（标题=新会话、消息数 0） | POST | /api/ai/chat/sessions | HTTP 200 / code 200 | HTTP 200 / code 200 | id=15，标题=新会话，创建时间=2026-10-22T19:24:09 | 通过 |
| 169 | AI 客服：会话列表里能看到刚建的会话 | GET | /api/ai/chat/sessions?page=1&size=50 | HTTP 200 / code 200 | HTTP 200 / code 200 | 共 1 个会话 | 通过 |
| 170 | AI 客服：第 1 条消息（标题取首条成功消息、返回 token 与剩余次数） | POST | /api/ai/chat/sessions/15/messages | HTTP 200 / code 200 | HTTP 200 / code 200 | 标题=笔记可以设成仅自己可见吗？，token=345，剩余 99 次 | 通过 |
| 171 | AI 客服：第 2 条消息带上了历史（上下文 3 条） | POST | /api/ai/chat/sessions/15/messages | HTTP 200 / code 200 | HTTP 200 / code 200 | （模拟客服·上下文 3 条）关于「那它后面还能改成公开吗？」：这是离线模拟回复，用于验证多轮链路与上下文裁剪；真实环境会由 DeepSeek 结合上文作答。 | 通过 |
| 172 | AI 客服：会话详情 4 条消息，顺序为用户/客服/用户/客服 | GET | /api/ai/chat/sessions/15 | HTTP 200 / code 200 | HTTP 200 / code 200 | USER → ASSISTANT → USER → ASSISTANT | 通过 |
| 173 | AI 客服：他人读历史 → 60007 | GET | /api/ai/chat/sessions/15 | HTTP 404 / code 60007 | HTTP 404 / code 60007 | — | 通过 |
| 174 | AI 客服：他人发消息 → 60007 | POST | /api/ai/chat/sessions/15/messages | HTTP 404 / code 60007 | HTTP 404 / code 60007 | — | 通过 |
| 175 | AI 客服：他人删会话 → 60007 | DELETE | /api/ai/chat/sessions/15 | HTTP 404 / code 60007 | HTTP 404 / code 60007 | — | 通过 |
| 176 | AI 客服：超长消息 1001 字 → 60008（提示当前字数） | POST | /api/ai/chat/sessions/15/messages | HTTP 400 / code 60008 | HTTP 400 / code 60008 | 消息最多 1000 字，当前 1001 字，请精简后再发 | 通过 |
| 177 | AI 客服：空白消息 → 400 | POST | /api/ai/chat/sessions/15/messages | HTTP 400 / code 400 | HTTP 400 / code 400 | — | 通过 |
| 178 | AI 客服：管理员 AI 统计里能看到客服分组 | GET | /api/admin/ai-stats | HTTP 200 / code 200 | HTTP 200 / code 200 | 总结:7次，适宜人群:3次，评分:4次，智能客服:22次 | 通过 |
| 179 | AI 客服：删除会话（消息一并物理删除） | DELETE | /api/ai/chat/sessions/15 | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 180 | AI 客服：删除后再读 → 60007 | GET | /api/ai/chat/sessions/15 | HTTP 404 / code 60007 | HTTP 404 / code 60007 | — | 通过 |
| 181 | AI 客服：当日第 1 个会话（未达上限） | POST | /api/ai/chat/sessions | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 182 | AI 客服：当日第 2 个会话（达到上限） | POST | /api/ai/chat/sessions | HTTP 200 / code 200 | HTTP 200 / code 200 | — | 通过 |
| 183 | AI 客服：超过当日建会话上限 → 60011（提示当前数量与上限） | POST | /api/ai/chat/sessions | HTTP 429 / code 60011 | HTTP 429 / code 60011 | 今天已经新建了 2 个会话（上限 2 个），明天再来吧 | 通过 |
| 184 | OpenAPI 规范可导出（/v3/api-docs） | GET | /v3/api-docs | HTTP 200 | HTTP 200 | 接口路径数=40 | 通过 |
| 185 | Swagger UI 页面可访问 | GET | /swagger-ui/index.html | HTTP 200 | HTTP 200 | 浏览器可视化文档 | 通过 |

## 四、说明

1. 业务 code 约定：200 成功；400 参数错误；401 未登录；403 无权限；404 资源不存在；405 方法不支持；500 系统错误；1xxxx 用户模块；2xxxx 笔记模块；3xxxx 文件模块。
2. 覆盖了全部 17 个接口的正常流程与异常流程（含越权、幂等、参数校验、上传限制、分页与排行）。
3. 未覆盖：AI 辅助相关接口（后续模块）、管理员后台接口（进阶要求）。
4. 复现方式：启动 MySQL 与应用后执行 tools/api-test.ps1；也可以用同目录的 Postman 集合导入后运行。
