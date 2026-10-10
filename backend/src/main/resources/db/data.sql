-- ============================================================================
--  效能评估系统 - 初始化基础数据 (幂等，INSERT IGNORE 不会覆盖已有数据)
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 角色（系统只保留两类：系统管理员、评估人员）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_role` (`role_code`, `role_name`, `description`, `permissions`, `data_scope`, `built_in`, `sort_order`, `created_at`, `updated_at`) VALUES
('SYSTEM_ADMIN', '系统管理员', '用户、角色、系统配置、日志、备份和部署检查', '["*"]', 'ALL', 1, 1, NOW(), NOW()),
('EVALUATOR', '评估人员', '项目、指标方案、数据库与文件两种数据接入、任务配置、算法执行、结果复核发布与结果分析（报告导出仅系统管理员）', '["dashboard:view","project:list","project:create","project:update","project:member","indicator:list","indicator:import","data:source:list","data:source:create","data:source:update","data:source:test","data:source:extract","data:batch:list","data:batch:import","data:batch:validate","data:snapshot:list","data:snapshot:create","task:list","task:create","task:execute","task:cancel","task:retry","result:list","result:view","result:review","result:publish","result:compare","result:export","baseline:list"]', 'PROJECT', 1, 2, NOW(), NOW());

-- ---------------------------------------------------------------------------
-- 系统参数
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `sys_config` (`config_key`, `config_value`, `config_group`, `description`, `editable`, `created_at`, `updated_at`) VALUES
('system.name',            '效能评估系统',      'SYSTEM',    '系统名称',                                 1, NOW(), NOW()),
('system.version',         '1.0.0',            'SYSTEM',    '系统版本号',                               0, NOW(), NOW()),
('system.offline',         'true',             'SYSTEM',    '离线部署模式（禁用一切外网资源）',           0, NOW(), NOW()),
('algorithm.default',      'WEIGHTED',         'ALGORITHM', '默认评估算法 AHP/FUZZY/WEIGHTED',           1, NOW(), NOW()),
('algorithm.cr.threshold', '0.1',              'ALGORITHM', 'AHP 一致性比率阈值 CR',                     1, NOW(), NOW()),
('algorithm.score.scale',  '4',                'ALGORITHM', '计算中间量保留小数位',                      1, NOW(), NOW()),
('algorithm.display.scale','2',                'ALGORITHM', '结果展示保留小数位',                        1, NOW(), NOW()),
('algorithm.allow.force',  'false',            'ALGORITHM', 'CR 不通过时是否允许强制计算',               1, NOW(), NOW()),
('data.missing.policy',    'BLOCK',            'DATA',      '缺失值策略 BLOCK/IMPUTE/DROP',              1, NOW(), NOW()),
('data.duplicate.policy',  'CONFIRM',          'DATA',      '重复记录策略 CONFIRM/OVERWRITE/KEEP',       1, NOW(), NOW()),
('data.max.rows',          '200000',           'DATA',      '单批次最大记录数',                          1, NOW(), NOW()),
('export.max.rows',        '100000',           'EXPORT',    '单次导出最大行数',                          1, NOW(), NOW()),
('report.company',         '效能评估系统',      'REPORT',    '报告落款单位',                              1, NOW(), NOW()),
('report.pdf.font',        'STSong-Light',     'REPORT',    'PDF 中文字体名',                            1, NOW(), NOW()),
('security.login.maxfail', '5',                'SECURITY',  '登录失败锁定阈值',                          1, NOW(), NOW()),
('security.login.lockmin', '15',               'SECURITY',  '登录锁定时长（分钟）',                      1, NOW(), NOW()),
('audit.retention.days',   '365',              'SECURITY',  '审计日志保留天数',                          1, NOW(), NOW());

-- ---------------------------------------------------------------------------
-- 数据质量规则（对应方案 5.3）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `data_quality_rule` (`rule_code`, `rule_name`, `rule_type`, `target_field`, `severity`, `enabled`, `description`, `created_at`, `updated_at`) VALUES
('QR-REQUIRED',  '必填字段缺失',   'REQUIRED',  'indicator_value', 'BLOCK', 1, '必填字段缺失时阻止任务提交，展示缺失字段和记录位置', NOW(), NOW()),
('QR-FORMAT',    '数值格式非法',   'FORMAT',    'indicator_value', 'BLOCK', 1, '非数值内容标记为错误行，提供错误行号和修正建议',     NOW(), NOW()),
('QR-RANGE',     '指标值越界',     'RANGE',     'indicator_value', 'WARN',  1, '超出指标体系定义的上下界时提示',                     NOW(), NOW()),
('QR-DIRECTION', '正负方向缺失',   'DIRECTION', 'direction',       'WARN',  1, '方向未确认时不得执行需要标准化的算法',               NOW(), NOW()),
('QR-DUPLICATE', '重复记录',       'DUPLICATE', 'indicator_code',  'WARN',  1, '按批次、对象、时间和指标编码识别重复记录',           NOW(), NOW()),
('QR-MISSING',   '缺失值',         'MISSING',   'indicator_value', 'BLOCK', 1, '按算法和业务规则选择剔除、补值或阻止计算，不允许静默处理', NOW(), NOW()),
('QR-UNIT',      '单位不一致',     'UNIT',      'unit',            'INFO',  1, '数据单位与指标定义不一致时提示',                     NOW(), NOW()),
('QR-COVERAGE',  '指标未覆盖',     'REQUIRED',  'indicator_code',  'BLOCK', 1, '数据未覆盖指标体系必需指标时阻止提交',               NOW(), NOW());

-- ---------------------------------------------------------------------------
-- 算法标准样例（对应方案 6.6 算法基准测试）
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `algorithm_baseline` (`baseline_code`, `baseline_name`, `algorithm`, `input_payload`, `expected_payload`, `abs_tolerance`, `rel_tolerance`, `description`, `enabled`, `created_at`, `updated_at`) VALUES
('BASE-AHP-001', 'AHP 完全一致矩阵（3阶）', 'AHP',
 '{"criteria":["A","B","C"],"matrix":[[1,2,6],[0.5,1,3],[0.16666667,0.33333333,1]]}',
 '{"weights":[0.6,0.3,0.1],"lambdaMax":3.0,"ci":0.0,"cr":0.0,"ri":0.58,"passed":true}',
 0.0001, 0.0001, '完全一致判断矩阵，权重应为 0.6 / 0.3 / 0.1，CR = 0', 1, NOW(), NOW()),
('BASE-AHP-002', 'AHP Saaty 经典 5 阶矩阵', 'AHP',
 '{"criteria":["C1","C2","C3","C4","C5"],"matrix":[[1,0.5,4,3,3],[2,1,7,5,5],[0.25,0.14285714,1,0.5,0.33333333],[0.33333333,0.2,2,1,1],[0.33333333,0.2,3,1,1]]}',
 '{"weights":[0.2636,0.4758,0.0538,0.0981,0.1087],"lambdaMax":5.0727,"ci":0.0182,"cr":0.0163,"ri":1.12,"passed":true}',
 0.002, 0.002, 'Saaty(1980) 经典算例，CR ≈ 0.016 < 0.1 通过一致性检验', 1, NOW(), NOW()),
('BASE-FUZZY-001', '模糊综合评判 一级模型', 'FUZZY',
 '{"gradeNames":["优","良","中","差"],"gradeScores":[95,85,70,55],"weights":[0.5,0.3,0.2],"membership":[[0.7,0.2,0.1,0.0],[0.3,0.5,0.2,0.0],[0.1,0.3,0.4,0.2]],"operator":"M_PLUS"}',
 '{"vector":[0.46,0.31,0.19,0.04],"level":"优","score":85.55}',
 0.0001, 0.0001, '加权平均型合成算子 M(·,+) 标准算例，综合评判向量应为 [0.46,0.31,0.19,0.04]', 1, NOW(), NOW()),
('BASE-WEIGHTED-001', '加权平均法 正向指标归一', 'WEIGHTED',
 '{"values":[90,80,70],"weights":[0.5,0.3,0.2],"directions":["POSITIVE","POSITIVE","POSITIVE"],"minValues":[0,0,0],"maxValues":[100,100,100],"gradeNames":["优","良","中","差"],"gradeThresholds":[90,80,60,0]}',
 '{"normalized":[0.9,0.8,0.7],"score":83.0,"level":"良"}',
 0.0001, 0.0001, '三项正向指标 90/80/70，权重 0.5/0.3/0.2，综合得分 83.0', 1, NOW(), NOW());
