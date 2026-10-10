-- ============================================================================
--  效能评估系统 (Efficiency Evaluation System) - 数据库结构
--  Database : MySQL 8.0 / 8.4
--  Charset  : utf8mb4
--  说明     : 脚本幂等，可重复执行；由 Spring Boot 启动时自动执行，
--             也可在 Navicat / mysql 客户端中手工执行。
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1. 角色
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code`     VARCHAR(50)  NOT NULL COMMENT '角色编码',
  `role_name`     VARCHAR(100) NOT NULL COMMENT '角色名称',
  `description`   VARCHAR(500)          DEFAULT NULL COMMENT '角色说明',
  `permissions`   TEXT                  DEFAULT NULL COMMENT '权限编码 JSON 数组',
  `data_scope`    VARCHAR(30)           DEFAULT 'PROJECT' COMMENT '数据范围 ALL/PROJECT/SELF',
  `built_in`      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否内置角色',
  `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at`    DATETIME              DEFAULT NULL,
  `updated_at`    DATETIME              DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

-- ---------------------------------------------------------------------------
-- 2. 用户
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `username`         VARCHAR(64)  NOT NULL COMMENT '登录名',
  `password`         VARCHAR(120) NOT NULL COMMENT 'BCrypt 密码哈希',
  `real_name`        VARCHAR(64)           DEFAULT NULL COMMENT '姓名',
  `role_code`        VARCHAR(50)  NOT NULL COMMENT '角色编码',
  `org_name`         VARCHAR(128)          DEFAULT NULL COMMENT '所属单位（预留多组织）',
  `email`            VARCHAR(128)          DEFAULT NULL,
  `phone`            VARCHAR(32)           DEFAULT NULL,
  `status`           VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED/LOCKED',
  `login_fail_count` INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
  `locked_until`     DATETIME              DEFAULT NULL COMMENT '锁定截止时间',
  `last_login_at`    DATETIME              DEFAULT NULL,
  `last_login_ip`    VARCHAR(64)           DEFAULT NULL,
  `remark`           VARCHAR(500)          DEFAULT NULL,
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`       DATETIME              DEFAULT NULL,
  `updated_at`       DATETIME              DEFAULT NULL,
  `created_by`       VARCHAR(64)           DEFAULT NULL,
  `updated_by`       VARCHAR(64)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_role` (`role_code`),
  KEY `idx_user_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- ---------------------------------------------------------------------------
-- 3. 审计日志
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_audit_log` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`      BIGINT                DEFAULT NULL,
  `username`     VARCHAR(64)           DEFAULT NULL,
  `module`       VARCHAR(50)           DEFAULT NULL COMMENT '模块：AUTH/DATA/TASK/CONFIG/EXPORT/SYSTEM',
  `action`       VARCHAR(64)           DEFAULT NULL COMMENT '操作',
  `http_method`  VARCHAR(10)           DEFAULT NULL,
  `request_uri`  VARCHAR(500)          DEFAULT NULL,
  `target_type`  VARCHAR(50)           DEFAULT NULL,
  `target_id`    VARCHAR(64)           DEFAULT NULL,
  `detail`       TEXT                  DEFAULT NULL COMMENT '请求摘要 JSON',
  `ip`           VARCHAR(64)           DEFAULT NULL,
  `user_agent`   VARCHAR(500)          DEFAULT NULL,
  `success`      TINYINT(1)   NOT NULL DEFAULT 1,
  `error_msg`    VARCHAR(2000)         DEFAULT NULL,
  `cost_ms`      BIGINT                DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_user` (`username`),
  KEY `idx_audit_module` (`module`),
  KEY `idx_audit_time` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审计日志表';

-- ---------------------------------------------------------------------------
-- 4. 系统配置
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `config_key`    VARCHAR(128) NOT NULL,
  `config_value`  TEXT                  DEFAULT NULL,
  `config_group`  VARCHAR(50)           DEFAULT 'SYSTEM',
  `description`   VARCHAR(500)          DEFAULT NULL,
  `editable`      TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`    DATETIME              DEFAULT NULL,
  `updated_at`    DATETIME              DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统参数表';

-- ---------------------------------------------------------------------------
-- 5. 项目
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `project` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `project_code`   VARCHAR(64)  NOT NULL COMMENT '项目编码',
  `project_name`   VARCHAR(200) NOT NULL COMMENT '项目名称',
  `project_type`   VARCHAR(50)           DEFAULT NULL COMMENT '项目类型',
  `description`    TEXT                  DEFAULT NULL,
  `owner_id`       BIGINT                DEFAULT NULL COMMENT '负责人ID',
  `owner_name`     VARCHAR(64)           DEFAULT NULL,
  `org_name`       VARCHAR(128)          DEFAULT NULL COMMENT '所属单位（组织预留）',
  `status`         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/ARCHIVED',
  `start_date`     DATE                  DEFAULT NULL,
  `end_date`       DATE                  DEFAULT NULL,
  `archived_at`    DATETIME              DEFAULT NULL,
  `remark`         VARCHAR(500)          DEFAULT NULL,
  `deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`     DATETIME              DEFAULT NULL,
  `updated_at`     DATETIME              DEFAULT NULL,
  `created_by`     VARCHAR(64)           DEFAULT NULL,
  `updated_by`     VARCHAR(64)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_code` (`project_code`),
  KEY `idx_project_status` (`status`),
  KEY `idx_project_owner` (`owner_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评估项目表';

-- ---------------------------------------------------------------------------
-- 6. 项目成员（项目级数据授权）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `project_member` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT,
  `project_id`   BIGINT      NOT NULL,
  `user_id`      BIGINT      NOT NULL,
  `username`     VARCHAR(64)          DEFAULT NULL,
  `member_role`  VARCHAR(50)          DEFAULT 'VIEWER' COMMENT 'MANAGER/DATA/EVALUATOR/REVIEWER/VIEWER',
  `created_at`   DATETIME             DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_member` (`project_id`, `user_id`),
  KEY `idx_pm_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '项目成员表';

-- ---------------------------------------------------------------------------
-- 7. 指标体系版本
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `indicator_version` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `version_code`   VARCHAR(64)  NOT NULL COMMENT '版本编号，如 IND-2026-001',
  `version_name`   VARCHAR(200) NOT NULL COMMENT '版本名称',
  `project_id`     BIGINT                DEFAULT NULL COMMENT '归属项目，NULL 表示全局版本',
  `source_type`    VARCHAR(20)  NOT NULL DEFAULT 'FILE' COMMENT 'FILE/MANUAL/API',
  `source_file`    VARCHAR(500)          DEFAULT NULL COMMENT '原始导入文件',
  `source_hash`    VARCHAR(128)          DEFAULT NULL COMMENT '原始文件 SHA256',
  `import_batch_id` BIGINT               DEFAULT NULL,
  `indicator_count` INT         NOT NULL DEFAULT 0,
  `leaf_count`     INT          NOT NULL DEFAULT 0,
  `max_level`      INT          NOT NULL DEFAULT 0,
  `status`         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/VALIDATED/PUBLISHED/INVALID/ARCHIVED',
  `validate_result` TEXT                 DEFAULT NULL COMMENT '校验结果 JSON',
  `description`    VARCHAR(1000)         DEFAULT NULL,
  `published_at`   DATETIME              DEFAULT NULL,
  `published_by`   VARCHAR(64)           DEFAULT NULL,
  `deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at`     DATETIME              DEFAULT NULL,
  `updated_at`     DATETIME              DEFAULT NULL,
  `created_by`     VARCHAR(64)           DEFAULT NULL,
  `updated_by`     VARCHAR(64)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_indicator_version_code` (`version_code`),
  KEY `idx_iv_project` (`project_id`),
  KEY `idx_iv_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '指标体系版本表';

-- ---------------------------------------------------------------------------
-- 8. 指标
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `indicator` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `version_id`       BIGINT        NOT NULL,
  `indicator_code`   VARCHAR(64)   NOT NULL COMMENT '指标编码（主关联键）',
  `indicator_name`   VARCHAR(200)  NOT NULL COMMENT '指标名称',
  `parent_code`      VARCHAR(64)            DEFAULT NULL COMMENT '上级指标编码',
  `level`            INT           NOT NULL DEFAULT 1 COMMENT '层级，1 为顶层',
  `level_path`       VARCHAR(1000)          DEFAULT NULL COMMENT '层级路径 /A/B/C',
  `is_leaf`          TINYINT(1)    NOT NULL DEFAULT 1,
  `unit`             VARCHAR(32)            DEFAULT NULL,
  `direction`        VARCHAR(20)   NOT NULL DEFAULT 'POSITIVE' COMMENT 'POSITIVE/NEGATIVE/INTERVAL',
  `weight`           DECIMAL(18,8)          DEFAULT NULL COMMENT '本地权重',
  `data_type`        VARCHAR(20)   NOT NULL DEFAULT 'NUMBER',
  `min_value`        DECIMAL(24,8)          DEFAULT NULL,
  `max_value`        DECIMAL(24,8)          DEFAULT NULL,
  `interval_lower`   DECIMAL(24,8)          DEFAULT NULL COMMENT '区间型指标下界',
  `interval_upper`   DECIMAL(24,8)          DEFAULT NULL COMMENT '区间型指标上界',
  `optimal_value`    DECIMAL(24,8)          DEFAULT NULL,
  `score_rule`       TEXT                    DEFAULT NULL COMMENT '评分规则 JSON',
  `sort_order`       INT           NOT NULL DEFAULT 0,
  `remark`           VARCHAR(500)           DEFAULT NULL,
  `created_at`       DATETIME               DEFAULT NULL,
  `updated_at`       DATETIME               DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ind_version` (`version_id`),
  KEY `idx_ind_code` (`version_id`, `indicator_code`),
  KEY `idx_ind_parent` (`version_id`, `parent_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '指标表';

-- ---------------------------------------------------------------------------
-- 9. 指标体系导入批次
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `indicator_import_batch` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `version_id`   BIGINT                DEFAULT NULL,
  `file_name`    VARCHAR(500)          DEFAULT NULL,
  `file_size`    BIGINT                DEFAULT NULL,
  `file_hash`    VARCHAR(128)          DEFAULT NULL,
  `stored_path`  VARCHAR(1000)         DEFAULT NULL,
  `total_rows`   INT          NOT NULL DEFAULT 0,
  `success_rows` INT          NOT NULL DEFAULT 0,
  `error_rows`   INT          NOT NULL DEFAULT 0,
  `status`       VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/PARTIAL/FAILED',
  `error_detail` TEXT                  DEFAULT NULL COMMENT '错误明细 JSON',
  `created_by`   VARCHAR(64)           DEFAULT NULL,
  `created_at`   DATETIME              DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_iib_version` (`version_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '指标体系导入批次表';

-- ---------------------------------------------------------------------------
-- 10. 外部数据源（甲方业务库，只读接入）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_source` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `source_code`      VARCHAR(64)   NOT NULL COMMENT '数据源编码',
  `source_name`      VARCHAR(200)  NOT NULL,
  `db_type`          VARCHAR(30)   NOT NULL DEFAULT 'MYSQL' COMMENT 'MYSQL/POSTGRESQL/ORACLE/SQLSERVER/DM/KINGBASE/JDBC',
  `jdbc_url`         VARCHAR(1000) NOT NULL,
  `driver_class`     VARCHAR(255)           DEFAULT NULL,
  `db_username`      VARCHAR(128)  NOT NULL,
  `db_password`      VARCHAR(1000) NOT NULL COMMENT 'AES 加密保存',
  `project_id`       BIGINT                 DEFAULT NULL,
  `read_only`        TINYINT(1)    NOT NULL DEFAULT 1,
  `max_rows`         INT           NOT NULL DEFAULT 100000,
  `status`           VARCHAR(20)   NOT NULL DEFAULT 'UNKNOWN' COMMENT 'UNKNOWN/OK/FAIL',
  `last_test_at`     DATETIME               DEFAULT NULL,
  `last_test_result` VARCHAR(2000)          DEFAULT NULL,
  `remark`           VARCHAR(500)           DEFAULT NULL,
  `deleted`          TINYINT(1)    NOT NULL DEFAULT 0,
  `created_at`       DATETIME               DEFAULT NULL,
  `updated_at`       DATETIME               DEFAULT NULL,
  `created_by`       VARCHAR(64)            DEFAULT NULL,
  `updated_by`       VARCHAR(64)            DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ds_code` (`source_code`),
  KEY `idx_ds_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '外部数据源表';

-- ---------------------------------------------------------------------------
-- 11. 数据源字段缓存（字段预览）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_source_field` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `data_source_id`    BIGINT       NOT NULL,
  `table_name`        VARCHAR(200)          DEFAULT NULL,
  `column_name`       VARCHAR(200) NOT NULL,
  `data_type`         VARCHAR(100)          DEFAULT NULL,
  `column_comment`    VARCHAR(500)          DEFAULT NULL,
  `nullable`          TINYINT(1)            DEFAULT 1,
  `ordinal_position`  INT                   DEFAULT 0,
  `sample_value`      VARCHAR(500)          DEFAULT NULL,
  `cached_at`         DATETIME              DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_dsf_source` (`data_source_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据源字段缓存表';

-- ---------------------------------------------------------------------------
-- 12. 数据批次
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_batch` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT,
  `batch_code`     VARCHAR(64)   NOT NULL COMMENT '批次编号',
  `project_id`     BIGINT                 DEFAULT NULL,
  `project_code`   VARCHAR(64)            DEFAULT NULL,
  `source_type`    VARCHAR(20)   NOT NULL DEFAULT 'FILE' COMMENT 'FILE/DATABASE/API/SFTP',
  `source_ref`     VARCHAR(1000)          DEFAULT NULL COMMENT '文件名或查询说明',
  `data_source_id` BIGINT                 DEFAULT NULL,
  `query_sql`      TEXT                   DEFAULT NULL,
  `file_name`      VARCHAR(500)           DEFAULT NULL,
  `file_size`      BIGINT                 DEFAULT NULL,
  `file_hash`      VARCHAR(128)           DEFAULT NULL,
  `stored_path`    VARCHAR(1000)          DEFAULT NULL,
  `target_version_id` BIGINT              DEFAULT NULL COMMENT '校验依据的指标体系版本',
  `total_rows`     INT           NOT NULL DEFAULT 0,
  `valid_rows`     INT           NOT NULL DEFAULT 0,
  `error_rows`     INT           NOT NULL DEFAULT 0,
  `object_count`   INT           NOT NULL DEFAULT 0,
  `indicator_count` INT          NOT NULL DEFAULT 0,
  `status`         VARCHAR(20)   NOT NULL DEFAULT 'IMPORTED' COMMENT 'IMPORTED/VALIDATED/FAILED/SNAPSHOTTED',
  `quality_report` TEXT                   DEFAULT NULL COMMENT '质量报告 JSON',
  `error_detail`   TEXT                   DEFAULT NULL COMMENT '错误行 JSON',
  `remark`         VARCHAR(500)           DEFAULT NULL,
  `deleted`        TINYINT(1)    NOT NULL DEFAULT 0,
  `created_at`     DATETIME               DEFAULT NULL,
  `created_by`     VARCHAR(64)            DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_batch_code` (`batch_code`),
  KEY `idx_batch_project` (`project_id`),
  KEY `idx_batch_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据批次表';

-- ---------------------------------------------------------------------------
-- 13. 原始数据记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `raw_data_record` (
  `id`                   BIGINT        NOT NULL AUTO_INCREMENT,
  `batch_id`             BIGINT        NOT NULL,
  `row_no`               INT                    DEFAULT NULL,
  `project_code`         VARCHAR(64)            DEFAULT NULL,
  `object_code`          VARCHAR(64)            DEFAULT NULL,
  `object_name`          VARCHAR(200)           DEFAULT NULL,
  `data_time`            DATETIME               DEFAULT NULL,
  `indicator_code`       VARCHAR(64)            DEFAULT NULL,
  `indicator_name`       VARCHAR(200)           DEFAULT NULL,
  `indicator_value`      DECIMAL(24,8)          DEFAULT NULL,
  `indicator_value_text` VARCHAR(200)           DEFAULT NULL,
  `unit`                 VARCHAR(32)            DEFAULT NULL,
  `source_batch`         VARCHAR(64)            DEFAULT NULL,
  `remark`               VARCHAR(500)           DEFAULT NULL,
  `valid`                TINYINT(1)    NOT NULL DEFAULT 1,
  `error_msg`            VARCHAR(500)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rdr_batch` (`batch_id`),
  KEY `idx_rdr_key` (`batch_id`, `object_code`, `indicator_code`),
  KEY `idx_rdr_indicator` (`batch_id`, `indicator_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '原始数据记录表';

-- ---------------------------------------------------------------------------
-- 14. 字段映射
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `field_mapping` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`     BIGINT                DEFAULT NULL,
  `batch_id`       BIGINT                DEFAULT NULL,
  `source_field`   VARCHAR(200) NOT NULL COMMENT '来源字段/列名',
  `target_field`   VARCHAR(200) NOT NULL COMMENT '标准字段',
  `transform_rule` VARCHAR(200)          DEFAULT 'NONE' COMMENT 'NONE/TRIM/UPPER/LOWER/NUMBER/DATE',
  `required`       TINYINT(1)   NOT NULL DEFAULT 0,
  `remark`         VARCHAR(500)          DEFAULT NULL,
  `created_at`     DATETIME              DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_fm_project` (`project_id`),
  KEY `idx_fm_batch` (`batch_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字段映射表';

-- ---------------------------------------------------------------------------
-- 15. 评估数据快照
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_snapshot` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
  `snapshot_code`        VARCHAR(64)  NOT NULL COMMENT '快照编号，如 SNAP-20260922-0003',
  `project_id`           BIGINT                DEFAULT NULL,
  `project_code`         VARCHAR(64)           DEFAULT NULL,
  `indicator_version_id` BIGINT                DEFAULT NULL,
  `indicator_version_code` VARCHAR(64)         DEFAULT NULL,
  `batch_id`             BIGINT       NOT NULL,
  `batch_code`           VARCHAR(64)           DEFAULT NULL,
  `record_count`         INT          NOT NULL DEFAULT 0,
  `object_count`         INT          NOT NULL DEFAULT 0,
  `indicator_count`      INT          NOT NULL DEFAULT 0,
  `missing_count`        INT          NOT NULL DEFAULT 0,
  `outlier_count`        INT          NOT NULL DEFAULT 0,
  `checksum`             VARCHAR(128)          DEFAULT NULL COMMENT '内容校验值',
  `status`               VARCHAR(20)  NOT NULL DEFAULT 'GENERATED' COMMENT 'GENERATED/USED/INVALID',
  `storage_path`         VARCHAR(1000)         DEFAULT NULL,
  `quality_report`       TEXT                  DEFAULT NULL,
  `data_range`           VARCHAR(200)          DEFAULT NULL COMMENT '数据时间范围',
  `generated_at`         DATETIME              DEFAULT NULL,
  `used_count`           INT          NOT NULL DEFAULT 0,
  `created_by`           VARCHAR(64)           DEFAULT NULL,
  `remark`               VARCHAR(500)          DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_code` (`snapshot_code`),
  KEY `idx_snap_project` (`project_id`),
  KEY `idx_snap_batch` (`batch_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评估数据快照表';

-- ---------------------------------------------------------------------------
-- 16. 快照指标值（标准化后，快照生成后不可变）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `snapshot_value` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `snapshot_id`      BIGINT        NOT NULL,
  `object_code`      VARCHAR(64)   NOT NULL,
  `object_name`      VARCHAR(200)           DEFAULT NULL,
  `data_time`        DATETIME               DEFAULT NULL,
  `indicator_code`   VARCHAR(64)   NOT NULL,
  `indicator_name`   VARCHAR(200)           DEFAULT NULL,
  `raw_value`        DECIMAL(24,8)          DEFAULT NULL,
  `normalized_value` DECIMAL(18,8)          DEFAULT NULL,
  `normalized`       TINYINT(1)    NOT NULL DEFAULT 0,
  `valid`            TINYINT(1)    NOT NULL DEFAULT 1,
  `outlier`          TINYINT(1)    NOT NULL DEFAULT 0,
  `missing`          TINYINT(1)    NOT NULL DEFAULT 0,
  `remark`           VARCHAR(500)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sv_snapshot` (`snapshot_id`),
  KEY `idx_sv_object` (`snapshot_id`, `object_code`),
  KEY `idx_sv_indicator` (`snapshot_id`, `indicator_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '快照指标值表';

-- ---------------------------------------------------------------------------
-- 17. 评估任务
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `evaluation_task` (
  `id`                     BIGINT        NOT NULL AUTO_INCREMENT,
  `task_code`              VARCHAR(64)   NOT NULL COMMENT '任务编号，如 TASK-20260922-0001',
  `task_name`              VARCHAR(200)  NOT NULL,
  `project_id`             BIGINT        NOT NULL,
  `project_code`           VARCHAR(64)            DEFAULT NULL,
  `project_name`           VARCHAR(200)           DEFAULT NULL,
  `indicator_version_id`   BIGINT        NOT NULL,
  `indicator_version_code` VARCHAR(64)            DEFAULT NULL,
  `snapshot_id`            BIGINT        NOT NULL,
  `snapshot_code`          VARCHAR(64)            DEFAULT NULL,
  `algorithm`              VARCHAR(30)   NOT NULL COMMENT 'AHP/FUZZY/WEIGHTED/COMBO',
  `algorithm_version`      VARCHAR(50)            DEFAULT NULL,
  `parameters`             TEXT                   DEFAULT NULL COMMENT '算法参数 JSON',
  `combo_flow`             TEXT                   DEFAULT NULL COMMENT '组合流程 JSON',
  `input_summary`          TEXT                   DEFAULT NULL COMMENT '输入摘要 JSON',
  `status`                 VARCHAR(30)   NOT NULL DEFAULT 'DRAFT'
      COMMENT 'DRAFT/VALIDATING/QUEUED/RUNNING/SUCCESS/FAILED/CANCELED/TIMEOUT/PENDING_REVIEW/PUBLISHED/ARCHIVED',
  `progress`               INT           NOT NULL DEFAULT 0,
  `current_step`           VARCHAR(200)           DEFAULT NULL,
  `error_code`             VARCHAR(64)            DEFAULT NULL,
  `error_msg`              VARCHAR(2000)          DEFAULT NULL,
  `stack_trace`            TEXT                   DEFAULT NULL,
  `retry_of`               BIGINT                 DEFAULT NULL COMMENT '重试来源任务ID',
  `retry_count`            INT           NOT NULL DEFAULT 0,
  `submit_by`              VARCHAR(64)            DEFAULT NULL,
  `submit_by_id`           BIGINT                 DEFAULT NULL,
  `queued_at`              DATETIME               DEFAULT NULL,
  `started_at`             DATETIME               DEFAULT NULL,
  `finished_at`            DATETIME               DEFAULT NULL,
  `duration_ms`            BIGINT                 DEFAULT NULL,
  `object_count`           INT           NOT NULL DEFAULT 0,
  `result_count`           INT           NOT NULL DEFAULT 0,
  `deleted`                TINYINT(1)    NOT NULL DEFAULT 0,
  `created_at`             DATETIME               DEFAULT NULL,
  `updated_at`             DATETIME               DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_task_project` (`project_id`),
  KEY `idx_task_status` (`status`),
  KEY `idx_task_snapshot` (`snapshot_id`),
  KEY `idx_task_algorithm` (`algorithm`),
  KEY `idx_task_time` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评估任务表';

-- ---------------------------------------------------------------------------
-- 18. 任务状态历史
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `task_status_history` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `task_id`     BIGINT       NOT NULL,
  `from_status` VARCHAR(30)           DEFAULT NULL,
  `to_status`   VARCHAR(30)  NOT NULL,
  `reason`      VARCHAR(1000)         DEFAULT NULL,
  `operator`    VARCHAR(64)           DEFAULT NULL,
  `operator_id` BIGINT                DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_tsh_task` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '任务状态历史表';

-- ---------------------------------------------------------------------------
-- 19. 评估结果（每个被评估对象一条）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `evaluation_result` (
  `id`                     BIGINT        NOT NULL AUTO_INCREMENT,
  `task_id`                BIGINT        NOT NULL,
  `task_code`              VARCHAR(64)            DEFAULT NULL,
  `project_id`             BIGINT                 DEFAULT NULL,
  `project_code`           VARCHAR(64)            DEFAULT NULL,
  `project_name`           VARCHAR(200)           DEFAULT NULL,
  `object_code`            VARCHAR(64)   NOT NULL,
  `object_name`            VARCHAR(200)           DEFAULT NULL,
  `indicator_version_code` VARCHAR(64)            DEFAULT NULL,
  `snapshot_code`          VARCHAR(64)            DEFAULT NULL,
  `algorithm`              VARCHAR(30)            DEFAULT NULL,
  `algorithm_version`      VARCHAR(50)            DEFAULT NULL,
  `total_score`            DECIMAL(18,6)          DEFAULT NULL COMMENT '综合得分',
  `level`                  VARCHAR(50)            DEFAULT NULL COMMENT '评判等级',
  `vector`                 TEXT                   DEFAULT NULL COMMENT '综合评判向量 JSON',
  `weights`                TEXT                   DEFAULT NULL COMMENT '权重 JSON',
  `contributions`          TEXT                   DEFAULT NULL COMMENT '指标贡献 JSON',
  `details`                TEXT                   DEFAULT NULL COMMENT '指标明细 JSON',
  `warnings`               TEXT                   DEFAULT NULL COMMENT '告警 JSON',
  `extra`                  TEXT                   DEFAULT NULL COMMENT '算法中间结果 JSON',
  `ranking`                INT                    DEFAULT NULL,
  `reviewed`               VARCHAR(20)   NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING/APPROVED/REJECTED',
  `review_comment`         VARCHAR(1000)          DEFAULT NULL,
  `reviewed_by`            VARCHAR(64)            DEFAULT NULL,
  `reviewed_at`            DATETIME               DEFAULT NULL,
  `publish_status`         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
  `published_at`           DATETIME               DEFAULT NULL,
  `published_by`           VARCHAR(64)            DEFAULT NULL,
  `created_at`             DATETIME               DEFAULT NULL,
  `updated_at`             DATETIME               DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_er_task` (`task_id`),
  KEY `idx_er_project` (`project_id`),
  KEY `idx_er_object` (`object_code`),
  KEY `idx_er_score` (`total_score`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评估结果表';

-- ---------------------------------------------------------------------------
-- 20. 结果指标明细（行式存储，支撑表格分页/排序与导出）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `result_detail` (
  `id`               BIGINT        NOT NULL AUTO_INCREMENT,
  `result_id`        BIGINT        NOT NULL,
  `task_id`          BIGINT        NOT NULL,
  `object_code`      VARCHAR(64)            DEFAULT NULL,
  `indicator_code`   VARCHAR(64)   NOT NULL,
  `indicator_name`   VARCHAR(200)           DEFAULT NULL,
  `indicator_level`  INT                    DEFAULT 1,
  `parent_code`      VARCHAR(64)            DEFAULT NULL,
  `unit`             VARCHAR(32)            DEFAULT NULL,
  `direction`        VARCHAR(20)            DEFAULT NULL,
  `raw_value`        DECIMAL(24,8)          DEFAULT NULL,
  `normalized_value` DECIMAL(18,8)          DEFAULT NULL,
  `weight`           DECIMAL(18,8)          DEFAULT NULL,
  `contribution`     DECIMAL(18,8)          DEFAULT NULL,
  `score`            DECIMAL(18,8)          DEFAULT NULL,
  `grade`            VARCHAR(50)            DEFAULT NULL COMMENT '单指标评价等级',
  `rank_no`          INT                    DEFAULT NULL,
  `remark`           VARCHAR(500)           DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rd_result` (`result_id`),
  KEY `idx_rd_task` (`task_id`),
  KEY `idx_rd_indicator` (`indicator_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '结果指标明细表';

-- ---------------------------------------------------------------------------
-- 21. AHP 一致性检验记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ahp_consistency` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `task_id`      BIGINT        NOT NULL,
  `result_id`    BIGINT                 DEFAULT NULL,
  `object_code`  VARCHAR(64)            DEFAULT NULL,
  `node_code`    VARCHAR(64)            DEFAULT NULL COMMENT '判断矩阵对应节点',
  `node_name`    VARCHAR(200)           DEFAULT NULL,
  `matrix_size`  INT                    DEFAULT NULL,
  `matrix_data`  TEXT                   DEFAULT NULL COMMENT '判断矩阵 JSON',
  `eigen_vector` TEXT                   DEFAULT NULL COMMENT '权重向量 JSON',
  `lambda_max`   DECIMAL(18,8)          DEFAULT NULL,
  `ci`           DECIMAL(18,8)          DEFAULT NULL,
  `ri`           DECIMAL(18,8)          DEFAULT NULL,
  `cr`           DECIMAL(18,8)          DEFAULT NULL,
  `passed`       TINYINT(1)    NOT NULL DEFAULT 1,
  `warning`      VARCHAR(500)           DEFAULT NULL,
  `created_at`   DATETIME               DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ahp_task` (`task_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AHP一致性检验记录表';

-- ---------------------------------------------------------------------------
-- 22. 报告 / 导出记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `report_record` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `report_code`    VARCHAR(64)  NOT NULL,
  `report_name`    VARCHAR(300)          DEFAULT NULL,
  `task_id`        BIGINT                DEFAULT NULL,
  `result_id`      BIGINT                DEFAULT NULL,
  `project_id`     BIGINT                DEFAULT NULL,
  `report_type`    VARCHAR(20)  NOT NULL COMMENT 'EXCEL/WORD/PDF/JSON',
  `file_name`      VARCHAR(300)          DEFAULT NULL,
  `file_path`      VARCHAR(1000)         DEFAULT NULL,
  `file_size`      BIGINT                DEFAULT NULL,
  `status`         VARCHAR(20)  NOT NULL DEFAULT 'GENERATING' COMMENT 'GENERATING/READY/FAILED',
  `error_msg`      VARCHAR(1000)         DEFAULT NULL,
  `download_count` INT          NOT NULL DEFAULT 0,
  `created_by`     VARCHAR(64)           DEFAULT NULL,
  `created_at`     DATETIME              DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_code` (`report_code`),
  KEY `idx_rr_task` (`task_id`),
  KEY `idx_rr_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报告导出记录表';

-- ---------------------------------------------------------------------------
-- 23. 数据质量规则
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_quality_rule` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `project_id`     BIGINT                DEFAULT NULL,
  `rule_code`      VARCHAR(64)  NOT NULL,
  `rule_name`      VARCHAR(200) NOT NULL,
  `rule_type`      VARCHAR(50)  NOT NULL COMMENT 'REQUIRED/RANGE/FORMAT/DIRECTION/DUPLICATE/MISSING/UNIT',
  `target_field`   VARCHAR(200)          DEFAULT NULL,
  `indicator_code` VARCHAR(64)           DEFAULT NULL,
  `expression`     VARCHAR(500)          DEFAULT NULL,
  `severity`       VARCHAR(20)  NOT NULL DEFAULT 'WARN' COMMENT 'BLOCK/WARN/INFO',
  `enabled`        TINYINT(1)   NOT NULL DEFAULT 1,
  `description`    VARCHAR(500)          DEFAULT NULL,
  `created_at`     DATETIME              DEFAULT NULL,
  `updated_at`     DATETIME              DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dqr_code` (`rule_code`),
  KEY `idx_dqr_project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据质量规则表';

-- ---------------------------------------------------------------------------
-- 24. 算法基准样例（用于回归验证）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `algorithm_baseline` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `baseline_code`    VARCHAR(64)  NOT NULL,
  `baseline_name`    VARCHAR(200) NOT NULL,
  `algorithm`        VARCHAR(30)  NOT NULL,
  `input_payload`    TEXT                  DEFAULT NULL COMMENT '输入 JSON',
  `expected_payload` TEXT                  DEFAULT NULL COMMENT '预期输出 JSON',
  `abs_tolerance`    DECIMAL(18,8)         DEFAULT NULL,
  `rel_tolerance`    DECIMAL(18,8)         DEFAULT NULL,
  `description`      VARCHAR(1000)         DEFAULT NULL,
  `enabled`          TINYINT(1)   NOT NULL DEFAULT 1,
  `last_run_at`      DATETIME              DEFAULT NULL,
  `last_run_result`  VARCHAR(2000)         DEFAULT NULL,
  `created_at`       DATETIME              DEFAULT NULL,
  `updated_at`       DATETIME              DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_baseline_code` (`baseline_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '算法基准样例表';

SET FOREIGN_KEY_CHECKS = 1;
