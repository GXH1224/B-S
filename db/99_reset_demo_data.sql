-- ============================================================================
--  效能评估系统 - 重置演示数据（保留用户、角色、系统参数、质量规则与算法基准）
--  用途：反复演示「导入指标体系 → 导入数据 → 生成快照 → 执行评估 → 查看结果」闭环
--  注意：该脚本会清空全部业务数据，仅用于测试环境
-- ============================================================================

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `result_detail`;
TRUNCATE TABLE `evaluation_result`;
TRUNCATE TABLE `ahp_consistency`;
TRUNCATE TABLE `task_status_history`;
TRUNCATE TABLE `evaluation_task`;
TRUNCATE TABLE `snapshot_value`;
TRUNCATE TABLE `data_snapshot`;
TRUNCATE TABLE `raw_data_record`;
TRUNCATE TABLE `data_batch`;
TRUNCATE TABLE `field_mapping`;
TRUNCATE TABLE `indicator`;
TRUNCATE TABLE `indicator_import_batch`;
TRUNCATE TABLE `indicator_version`;
TRUNCATE TABLE `report_record`;
TRUNCATE TABLE `project_member`;
TRUNCATE TABLE `project`;
TRUNCATE TABLE `data_source_field`;
TRUNCATE TABLE `data_source`;
TRUNCATE TABLE `sys_audit_log`;

SET FOREIGN_KEY_CHECKS = 1;

SELECT '演示业务数据已重置（用户/角色/参数/质量规则/算法基准保留）' AS status;
