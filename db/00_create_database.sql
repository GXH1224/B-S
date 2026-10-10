-- ============================================================================
--  效能评估系统 - MySQL 环境初始化（以 root 身份执行一次）
-- ============================================================================
--
--  【交付注意】本脚本中的口令为演示用初始口令，企业环境请在执行前替换：
--    root      : Root@2026
--    ees       : Ees@2026        （应用账号，后端通过 EES_DB_PASSWORD 注入）
--    ees_reader: EesRead@2026    （只读账号示例，用于外部数据源接入演示）
--  替换后必须同步设置后端环境变量 EES_DB_USER / EES_DB_PASSWORD，
--  否则后端会因「Access denied for user 'ees'」启动失败（见 docs/DEPLOY.md 2.5 与 6）。
--  另外，应用内默认业务账号（admin 等）的初始口令也应通过界面首次登录后立即修改。
-- ============================================================================

-- 1. 设置 root 密码（初始为空）
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY 'Root@2026';

-- 2. 创建系统数据库
CREATE DATABASE IF NOT EXISTS `efficiency_evaluation`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

-- 3. 创建业务账号（应用使用该账号，最小权限）
CREATE USER IF NOT EXISTS 'ees'@'localhost' IDENTIFIED WITH caching_sha2_password BY 'Ees@2026';
CREATE USER IF NOT EXISTS 'ees'@'127.0.0.1' IDENTIFIED WITH caching_sha2_password BY 'Ees@2026';
CREATE USER IF NOT EXISTS 'ees'@'%' IDENTIFIED WITH caching_sha2_password BY 'Ees@2026';

GRANT ALL PRIVILEGES ON `efficiency_evaluation`.* TO 'ees'@'localhost';
GRANT ALL PRIVILEGES ON `efficiency_evaluation`.* TO 'ees'@'127.0.0.1';
GRANT ALL PRIVILEGES ON `efficiency_evaluation`.* TO 'ees'@'%';

-- 4. 只读账号示例（演示“只读账号接入”，可用于外部数据源接入验证）
CREATE USER IF NOT EXISTS 'ees_reader'@'%' IDENTIFIED WITH caching_sha2_password BY 'EesRead@2026';
GRANT SELECT, SHOW VIEW ON `efficiency_evaluation`.* TO 'ees_reader'@'%';

FLUSH PRIVILEGES;

-- 5. 校验
SELECT VERSION() AS mysql_version, @@character_set_server AS charset, @@time_zone AS tz;
SHOW DATABASES LIKE 'efficiency_evaluation';
SELECT user, host, plugin FROM mysql.user WHERE user IN ('root','ees','ees_reader');
