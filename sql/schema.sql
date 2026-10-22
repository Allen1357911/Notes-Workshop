-- ============================================================
-- 笔记工坊 Note-Workshop 建表脚本
-- 数据库：MySQL 8.0+ / 9.x
-- 说明：按模块推进，本文件随模块增加表。
--       模块 2：用户表 t_user
--       模块 3：读书笔记表 t_note、笔记配图表 t_note_image
-- 注意：全部使用 CREATE TABLE IF NOT EXISTS，重复执行不会删掉已有数据。
-- ============================================================

CREATE DATABASE IF NOT EXISTS note_workshop
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE note_workshop;

-- ------------------------------------------------------------
-- 用户表（模块 2）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)     NOT NULL COMMENT '登录用户名，唯一',
    password    VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希后的密码，绝不存明文',
    nickname    VARCHAR(50)              DEFAULT NULL COMMENT '昵称',
    avatar_url  VARCHAR(255)             DEFAULT NULL COMMENT '头像访问 URL（只存 URL，不存文件本身）',
    role        VARCHAR(20)     NOT NULL DEFAULT 'USER' COMMENT '角色：USER 普通用户 / ADMIN 管理员',
    status      TINYINT         NOT NULL DEFAULT 1 COMMENT '账号状态：1 正常，0 禁用。基础阶段只留位，不实现禁用功能',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- ------------------------------------------------------------
-- 读书笔记表（模块 3）
-- visibility 取值 PRIVATE / PUBLIC，与 Java 枚举 Visibility 按名字对应
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_note
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '作者用户 id',
    book_name   VARCHAR(100)    NOT NULL COMMENT '图书名（基础阶段只存字符串，不建图书表）',
    content     TEXT            NOT NULL COMMENT '笔记正文',
    visibility  VARCHAR(20)     NOT NULL DEFAULT 'PRIVATE' COMMENT '可见性：PRIVATE 私有（作者+管理员可见）/ PUBLIC 公开（所有人可见）',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_visibility_create (visibility, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='读书笔记表';

-- ------------------------------------------------------------
-- 笔记配图表（模块 3）
-- 一篇笔记 0~N 张图；数据库只存 URL，图片文件在磁盘上
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_note_image
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    note_id     BIGINT UNSIGNED NOT NULL COMMENT '所属笔记 id',
    url         VARCHAR(255)    NOT NULL COMMENT '图片访问 URL',
    sort_order  INT             NOT NULL DEFAULT 0 COMMENT '排序号，小的在前',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_note_id (note_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='笔记配图表';

-- ------------------------------------------------------------
-- 点赞表（模块 4）
-- (note_id, user_id) 唯一：同一个人对同一篇笔记只能有一条点赞记录，
-- 重复点赞靠这个唯一索引兜底，业务层再做幂等处理
-- 点赞数不冗余存字段，一律由这张表实时聚合
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_note_like
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    note_id     BIGINT UNSIGNED NOT NULL COMMENT '被点赞的笔记 id',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '点赞用户 id',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_user (note_id, user_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='笔记点赞表';

-- ============================================================
-- 扩展部分（进阶要求）
-- 原则：只做加法。新表用 CREATE TABLE IF NOT EXISTS；
--       已有表新增列用"先查 information_schema 再决定是否 ALTER"的幂等写法，
--       脚本里没有任何 DROP / DELETE，可以反复执行。
-- ============================================================

-- ------------------------------------------------------------
-- 评论表（管理员违规评论管理需要；用户侧发评论/回复接口属于社区互动模块）
-- parent_id：回复的父评论 id，顶层评论为 NULL
-- root_id  ：所属顶层评论 id，顶层评论等于自身 id；删除父评论时按 root_id 一次性级联逻辑删除整棵子树
-- deleted  ：逻辑删除标记，1 表示已删除（查询自动过滤）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_comment
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    note_id     BIGINT UNSIGNED NOT NULL COMMENT '所属笔记 id',
    user_id     BIGINT UNSIGNED NOT NULL COMMENT '评论人 id',
    parent_id   BIGINT UNSIGNED          DEFAULT NULL COMMENT '父评论 id，顶层评论为 NULL',
    root_id     BIGINT UNSIGNED NOT NULL COMMENT '所属顶层评论 id（顶层评论等于自身 id）',
    content     VARCHAR(1000)   NOT NULL COMMENT '评论内容',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_note_root (note_id, root_id),
    KEY idx_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='笔记评论表';

-- ------------------------------------------------------------
-- 关注关系表（社区互动模块）
-- (follower_id, followee_id) 唯一索引：从数据库层面保证不会重复关注
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_follow
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    follower_id BIGINT UNSIGNED NOT NULL COMMENT '关注者 id',
    followee_id BIGINT UNSIGNED NOT NULL COMMENT '被关注者 id',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_follower_followee (follower_id, followee_id),
    KEY idx_followee_id (followee_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户关注关系表';

-- ------------------------------------------------------------
-- 操作日志表（性能与健壮性模块）
-- 由 @OperationLog 注解 + AOP 切面自动写入，业务代码不感知
-- params 只记录脱敏后的参数摘要（password 一类字段一律打码），最长 500 字符
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_operation_log
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED          DEFAULT NULL COMMENT '操作人 id（未登录为空）',
    username    VARCHAR(50)              DEFAULT NULL COMMENT '操作人用户名（冗余一份，便于直接看）',
    module      VARCHAR(50)     NOT NULL COMMENT '模块，例如 用户管理 / 笔记审核',
    action      VARCHAR(50)     NOT NULL COMMENT '动作，例如 下架 / 禁用账号',
    description VARCHAR(255)             DEFAULT NULL COMMENT '补充说明',
    target_id   BIGINT UNSIGNED          DEFAULT NULL COMMENT '操作对象 id（一般是被操作的笔记/用户/评论 id）',
    method      VARCHAR(10)              DEFAULT NULL COMMENT 'HTTP 方法',
    uri         VARCHAR(255)             DEFAULT NULL COMMENT '请求路径',
    ip          VARCHAR(64)              DEFAULT NULL COMMENT '来源 IP',
    params      VARCHAR(500)             DEFAULT NULL COMMENT '脱敏后的参数摘要',
    success     TINYINT         NOT NULL DEFAULT 1 COMMENT '是否成功：1 成功，0 失败',
    error_msg   VARCHAR(500)             DEFAULT NULL COMMENT '失败原因',
    cost_ms     BIGINT          NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_create_time (create_time),
    KEY idx_module_action (module, action)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志表';

-- ------------------------------------------------------------
-- t_note 新增审核字段（默认 APPROVED：历史数据与现有功能不受影响）
-- ------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_note'
                      AND COLUMN_NAME = 'audit_status');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE t_note ADD COLUMN audit_status VARCHAR(20) NOT NULL DEFAULT ''APPROVED'' COMMENT ''审核状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回 / TAKEN_DOWN 已下架''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_note'
                      AND COLUMN_NAME = 'audit_remark');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE t_note ADD COLUMN audit_remark VARCHAR(255) DEFAULT NULL COMMENT ''驳回或下架的原因''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_note'
                      AND COLUMN_NAME = 'audit_time');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE t_note ADD COLUMN audit_time DATETIME DEFAULT NULL COMMENT ''最近一次审核时间''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 待审核队列与"公开且已通过"的过滤查询需要它
SET @idx_exists := (SELECT COUNT(*)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_note'
                      AND INDEX_NAME = 'idx_audit_status');
SET @ddl := IF(@idx_exists = 0,
               'ALTER TABLE t_note ADD INDEX idx_audit_status (audit_status, create_time)',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- AI 调用日志表（AI 模块）
-- 每次调用模型都记一条，既做"调用统计"的数据源，也让"用户确认的是哪一次生成"可追溯
-- result 存通过结构校验后的结果原文；confirmed 标记是否被用户手动确认保存到笔记
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_ai_log
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    note_id           BIGINT UNSIGNED NOT NULL COMMENT '笔记 id',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '调用者 id',
    capability        VARCHAR(20)     NOT NULL COMMENT '能力：SUMMARY 总结 / AUDIENCE 适宜人群 / SCORE 评分',
    model             VARCHAR(50)              DEFAULT NULL COMMENT '模型名',
    input_chars       INT             NOT NULL DEFAULT 0 COMMENT '送入模型的正文长度（截断后）',
    prompt_tokens     INT             NOT NULL DEFAULT 0 COMMENT '输入 token 数',
    completion_tokens INT             NOT NULL DEFAULT 0 COMMENT '输出 token 数',
    total_tokens      INT             NOT NULL DEFAULT 0 COMMENT '总 token 数',
    cost_ms           BIGINT          NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    success           TINYINT         NOT NULL DEFAULT 1 COMMENT '是否成功：1 成功，0 失败',
    error_msg         VARCHAR(500)             DEFAULT NULL COMMENT '失败原因',
    result            TEXT                     COMMENT 'AI 结果（已通过结构校验）',
    confirmed         TINYINT         NOT NULL DEFAULT 0 COMMENT '是否被用户确认保存：1 是，0 否',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (id),
    KEY idx_note_id (note_id),
    KEY idx_user_time (user_id, create_time),
    KEY idx_capability (capability),
    KEY idx_success (success)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 调用日志表';

-- ------------------------------------------------------------
-- t_note 新增 AI 结果字段（默认全空：历史数据与现有功能不受影响）
-- 与审核状态同一个思路：当前生效的那一份结果挂在笔记上，历史都在 t_ai_log
-- ------------------------------------------------------------
SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_note'
                      AND COLUMN_NAME = 'ai_type');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE t_note ADD COLUMN ai_type VARCHAR(20) DEFAULT NULL COMMENT ''已保存的 AI 结果类型：SUMMARY / AUDIENCE / SCORE，一篇笔记最多一种''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_note'
                      AND COLUMN_NAME = 'ai_result');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE t_note ADD COLUMN ai_result TEXT COMMENT ''已确认保存的 AI 结果（只作参考）''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_note'
                      AND COLUMN_NAME = 'ai_confirmed_at');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE t_note ADD COLUMN ai_confirmed_at DATETIME DEFAULT NULL COMMENT ''用户确认保存 AI 结果的时间''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_note'
                      AND COLUMN_NAME = 'ai_public');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE t_note ADD COLUMN ai_public TINYINT NOT NULL DEFAULT 0 COMMENT ''AI 结果是否对其他人公开：0 仅自己与管理员可见，1 随笔记公开''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ------------------------------------------------------------
-- AI 智能客服：会话表 + 消息表（AI 进阶功能）
-- 会话归属唯一用户；消息按会话维度隔离；同一用户不同会话上下文互不污染
-- 物理删除：删会话时消息一并删除（用户私有聊天记录，不需要留痕在业务表）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_ai_chat_session
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '归属用户 id',
    title             VARCHAR(50)     NOT NULL DEFAULT '新会话' COMMENT '会话标题：首条成功消息前 30 字；尚未有成功消息时保持“新会话”',
    message_count     INT             NOT NULL DEFAULT 0 COMMENT '消息条数（用户消息 + AI 回复）',
    last_message_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一条消息时间；空会话等于创建时间，用于列表倒序',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, last_message_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 客服会话表';

CREATE TABLE IF NOT EXISTS t_ai_chat_message
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_id        BIGINT UNSIGNED NOT NULL COMMENT '所属会话 id',
    user_id           BIGINT UNSIGNED NOT NULL COMMENT '归属用户 id（冗余，便于排查与二次校验）',
    role              VARCHAR(20)     NOT NULL COMMENT '角色：USER 用户 / ASSISTANT 客服',
    content           TEXT            NOT NULL COMMENT '消息内容',
    prompt_tokens     INT             NOT NULL DEFAULT 0 COMMENT '本轮输入 token（仅 ASSISTANT 有）',
    completion_tokens INT             NOT NULL DEFAULT 0 COMMENT '本轮输出 token（仅 ASSISTANT 有）',
    total_tokens      INT             NOT NULL DEFAULT 0 COMMENT '本轮总 token',
    cost_ms           BIGINT          NOT NULL DEFAULT 0 COMMENT '本轮 AI 耗时（毫秒）',
    create_time       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='AI 客服消息表';

-- ------------------------------------------------------------
-- t_ai_log 适配客服对话：笔记维度改为可空，新增会话维度
-- 作用：客服每轮调用与笔记 AI 共用同一张审计表，统计口径统一
-- ------------------------------------------------------------
SET @nullable := (SELECT IS_NULLABLE
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = 'note_workshop'
                    AND TABLE_NAME = 't_ai_log'
                    AND COLUMN_NAME = 'note_id');
SET @ddl := IF(@nullable = 'NO',
               'ALTER TABLE t_ai_log MODIFY COLUMN note_id BIGINT UNSIGNED NULL COMMENT ''笔记 id（客服对话为空）''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_ai_log'
                      AND COLUMN_NAME = 'session_id');
SET @ddl := IF(@col_exists = 0,
               'ALTER TABLE t_ai_log ADD COLUMN session_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''客服会话 id（笔记 AI 为空）''',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (SELECT COUNT(*)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = 'note_workshop'
                      AND TABLE_NAME = 't_ai_log'
                      AND INDEX_NAME = 'idx_session_id');
SET @ddl := IF(@idx_exists = 0,
               'ALTER TABLE t_ai_log ADD INDEX idx_session_id (session_id)',
               'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
