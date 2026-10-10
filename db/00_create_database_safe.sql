-- ============================================================================
--  效能评估系统 - 建库与业务账号（安全版，不会修改 root 密码）
-- ============================================================================
--
--  为什么需要这个文件：
--    交付包自带的 db\00_create_database.sql 第 15 行会执行
--      ALTER USER 'root'@'localhost' IDENTIFIED ... BY 'Root@2026';
--    那是给「全新 MySQL、root 无密码」的演示机准备的。
--    本机 MySQL 9.6 的 root 已有自己的密码，直接跑那个脚本会把 root 密码
--    改成 Root@2026，影响本机其它库（agri_analysis 等）。所以用本文件替代。
--
--  用法：以 root 身份执行本文件（Navicat 里右键「运行 SQL 文件」，或命令行）。
--        mysql.exe -uroot -p --default-character-set=utf8mb4 < 本文件
--
--  执行完之后：
--    · 表结构和初始数据【不用手工建】——后端 jar 启动时会自动执行
--      内置的 db/schema.sql（24 张表，CREATE TABLE IF NOT EXISTS）与
--      db/data.sql（INSERT IGNORE 灌初始数据），两个脚本都是幂等的。
--    · 也就是说：Navicat 里那三个脚本，你其实只需要「建库 + 建账号」这一步。
-- ============================================================================

-- 1. 建库（幂等）
CREATE DATABASE IF NOT EXISTS `efficiency_evaluation`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

-- 2. 业务账号 ees（后端用这个账号连库，最小权限）
--    口令与后端默认值 EES_DB_PASSWORD=Ees@2026 保持一致；
--    若要改口令，请同时设置环境变量 EES_DB_PASSWORD。
CREATE USER IF NOT EXISTS 'ees'@'localhost'  IDENTIFIED BY 'Ees@2026';
CREATE USER IF NOT EXISTS 'ees'@'127.0.0.1'  IDENTIFIED BY 'Ees@2026';
CREATE USER IF NOT EXISTS 'ees'@'%'          IDENTIFIED BY 'Ees@2026';

ALTER USER 'ees'@'localhost' IDENTIFIED BY 'Ees@2026';
ALTER USER 'ees'@'127.0.0.1' IDENTIFIED BY 'Ees@2026';
ALTER USER 'ees'@'%'         IDENTIFIED BY 'Ees@2026';

GRANT ALL PRIVILEGES ON `efficiency_evaluation`.* TO 'ees'@'localhost';
GRANT ALL PRIVILEGES ON `efficiency_evaluation`.* TO 'ees'@'127.0.0.1';
GRANT ALL PRIVILEGES ON `efficiency_evaluation`.* TO 'ees'@'%';

-- 3. 只读账号示例（演示「外部数据源只读接入」时才需要，可不要）
CREATE USER IF NOT EXISTS 'ees_reader'@'%' IDENTIFIED BY 'EesRead@2026';
GRANT SELECT, SHOW VIEW ON `efficiency_evaluation`.* TO 'ees_reader'@'%';

FLUSH PRIVILEGES;

-- 4. 校验
SELECT VERSION() AS mysql_version, @@character_set_server AS charset, @@time_zone AS tz;
SHOW DATABASES LIKE 'efficiency_evaluation';
SELECT user, host, plugin FROM mysql.user WHERE user IN ('ees', 'ees_reader');
