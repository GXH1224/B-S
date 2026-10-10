# 效能评估系统 —— 后端接口契约（前端开发依据）

- 基础地址：`http://127.0.0.1:8080`（开发环境由 Vite 代理 `/api` → 8080）
- 所有接口返回统一结构：

```json
{ "code": 0, "message": "success", "data": {}, "timestamp": 1790000000000 }
```

`code === 0` 表示成功，其余为业务失败，`message` 为可直接展示的中文提示。
分页接口的 `data` 为 `{ records: [], total: 0, page: 1, size: 20, pages: 0, hasNext: false }`。

- 认证：登录后所有请求带 `Authorization: Bearer <token>`；下载类接口也支持 `?token=<token>`。
- 错误码：1001 参数校验、2001 未登录、2003 无权限、3007 数值格式非法、4003 一致性检验未通过、
  5002 任务超时、6001 数据源连接失败、6002 SQL 含禁止的写操作关键字。

---

## 1. 认证 `/api/auth`

| 方法 | 路径 | 说明 | 关键字段 |
|---|---|---|---|
| POST | `/api/auth/login` | 登录 | 请求 `{username, password}`；响应 `{token, tokenType, expiresIn, user:{id, username, realName, roleCode, roleName, orgName, dataScope, permissions[], menus[], lastLoginAt}}` |
| POST | `/api/auth/logout` | 登出 | — |
| GET | `/api/auth/me` | 当前用户 | `{id, username, realName, roleCode, roleName, dataScope, permissions[], superAdmin}` |
| POST | `/api/auth/change-password` | 修改密码 | `{oldPassword, newPassword}` |

默认账号：`admin/admin123`（系统管理员，权限 `["*"]`）、`evaluator/eval123`（评估人员，29 项业务权限）。

> **角色与接口可访问性**：系统只有 `SYSTEM_ADMIN`（系统管理员）与 `EVALUATOR`（评估人员）两类角色。
> `result:review`（结果复核）、`result:publish`（结果发布，归档 `/archive` 复用该码）与 `result:compare`（结果横向对比）
> 均已授予评估人员，因此 §7 的 `/review`、`/publish`、`/archive`、`/compare` **评估人员与系统管理员都能调用**；
> `result:export`（结果导出）同样已授予评估人员，评估人员可用它生成并取回结果报告（见 §7、§8）。
> `data:source:list` / `create` / `update` / `test` / `extract` 五个数据源权限码**均已授予评估人员**，
> 因此 §5.1 的数据源接口对评估人员可用（菜单「数据源管理」也对其可见），`GET /api/data-sources` 返回 `code=0`，
> 不再是 403；`GET /api/users`、审计日志与系统参数接口同样只对系统管理员开放（`GET /api/users/options`、`GET /api/roles` 等免权限码接口不在此列）。
> **报告模块已收紧为仅系统管理员**：评估人员不再持有 `report:list` / `report:create` / `report:download`，
> 因此 `GET /api/reports`（列表）与 `POST /api/reports`（生成）对其返回 HTTP **403** `{"code":2003,"message":"无权访问该资源"}`；
> 仅 `GET /api/reports/{id}/download` 额外允许 `result:export`（详见 §8）。

## 2. 系统 `/api`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 健康检查（免登录）：`{status, components:{database,algorithm,storage}}` |
| GET | `/api/info` | 系统信息（免登录）：`{name, version, architecture, offline, algorithms, algorithmMode}` |
| GET | `/api/dashboard/overview?projectId=` | 驾驶舱总览（见 §10） |
| GET | `/api/users` | 用户分页 `keyword, roleCode, status, page, size` |
| GET | `/api/users/options` | 用户下拉 |
| POST/PUT | `/api/users`、`/api/users/{id}` | 新增/修改用户，体见 `UserSaveRequest` |
| POST | `/api/users/{id}/reset-password` | `{newPassword}` |
| PUT | `/api/users/{id}/status?status=ENABLED` | 启用/停用 |
| DELETE | `/api/users/{id}` | 逻辑删除 |
| GET | `/api/roles` | 角色列表（含 userCount） |
| GET | `/api/permissions` | 权限字典 `[{code,label}]` |
| GET | `/api/audit-logs` | 审计分页 `username, module, success, from, to, page, size` |
| GET | `/api/audit-logs/{id}` | 审计详情 `{log, detailMap}` |
| POST | `/api/audit-logs/purge` | `{retentionDays}` |
| GET | `/api/configs` | 系统参数（按分组）：`{SYSTEM:{key:{id,value,description,editable}}, ...}` |
| PUT | `/api/configs/{key}` | `{value}` |
| GET | `/api/quality-rules` | 数据质量规则 |
| PUT | `/api/quality-rules/{id}` | `{severity, enabled, description}` |
| GET | `/api/baselines` | 算法基准样例列表 |
| POST | `/api/baselines/{id}/run` | 运行单个基准 → `{passed, result, comparisons[], actualRaw, expected}` |
| POST | `/api/baselines/run-all` | 运行全部基准 |

## 3. 项目 `/api/projects`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/projects?keyword=&status=&page=&size=` | 分页（自动按数据范围过滤） |
| GET | `/api/projects/options` | `[{id,code,name,status}]` |
| GET | `/api/projects/all` | 全部可见项目 |
| GET | `/api/projects/{id}` | 详情 |
| GET | `/api/projects/{id}/statistics` | `{taskCount,resultCount,batchCount,publishedCount,avgScore}` |
| POST/PUT | `/api/projects`、`/api/projects/{id}` | 保存（字段：`projectCode, projectName, projectType, description, ownerId, ownerName, orgName, status, startDate, endDate, remark`） |
| POST | `/api/projects/{id}/archive` / `/activate` | 归档 / 启用 |
| DELETE | `/api/projects/{id}` | 删除（已产生结果时拒绝） |
| GET/POST | `/api/projects/{id}/members` | 成员列表 / 覆盖保存 `[{userId, memberRole}]` |

## 4. 指标体系 `/api/indicator-versions`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/indicator-versions?keyword=&status=&projectId=&page=&size=` | 版本分页 |
| GET | `/api/indicator-versions/options?projectId=` | 版本下拉 |
| GET | `/api/indicator-versions/{id}` | 版本详情（含 `validateResult` JSON 字符串） |
| GET | `/api/indicator-versions/{id}/indicators` | 指标平铺列表 |
| GET | `/api/indicator-versions/{id}/tree` | 指标树 `[{code,name,level,unit,direction,weight,leaf,minValue,maxValue,children[]}]` |
| POST | `/api/indicator-versions/import` | `multipart/form-data`：`file, projectId?, versionCode?, versionName?, description?` → `{versionId, versionCode, totalRows, successRows, errorRows, indicatorCount, leafCount, maxLevel, errors[], warnings[], success}` |
| POST | `/api/indicator-versions/{id}/revalidate` | 重新校验 |
| POST | `/api/indicator-versions/{id}/publish` / `/archive` | 发布 / 归档 |
| DELETE | `/api/indicator-versions/{id}` | 删除（已发布拒绝） |
| GET | `/api/indicator-versions/import-batches?versionId=` | 导入批次 |
| GET | `/api/indicator-versions/template` | 下载导入模板 xlsx |

指标体系模板列：`indicator_code, indicator_name, parent_code, level, unit, direction(正向/负向/区间型 或 POSITIVE/NEGATIVE/INTERVAL), weight, min_value, max_value, interval_lower, interval_upper, optimal_value, score_rule, sort_order, remark`

## 5. 数据接入

### 5.1 数据源 `/api/data-sources`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/data-sources?keyword=&dbType=&page=&size=` | 分页（密码不回显，返回 `******`） |
| GET | `/api/data-sources/{id}` | 详情 |
| GET | `/api/data-sources/db-types` | `[{dbType,label,driverClass,urlExample}]` |
| POST/PUT | `/api/data-sources`、`/{id}` | 保存（`sourceCode, sourceName, dbType, jdbcUrl, driverClass, dbUsername, dbPassword, projectId, maxRows, remark`） |
| DELETE | `/api/data-sources/{id}` | 删除 |
| POST | `/api/data-sources/{id}/test` | 连接测试 → `{success, productName, productVersion, driverName, url, costMs, message}` |
| GET | `/api/data-sources/{id}/tables?schema=` | 表/视图清单 |
| GET | `/api/data-sources/{id}/fields?table=` | 字段预览 `[{columnName,dataType,columnComment,nullable,sampleValue}]` |
| POST | `/api/data-sources/{id}/extract` | `{querySql, projectId, targetVersionId}` → 抽取并生成数据批次 |
| POST | `/api/data-sources/validate-sql` | `{querySql}` → `{valid, normalizedSql, message}` |

> **权限码与角色归属**：列表 / 详情 / 表清单 / 字段预览需要 `data:source:list`；新建需要 `data:source:create`；
> 编辑与删除都需要 `data:source:update`（`DELETE /api/data-sources/{id}` 复用的就是这一个码，字典里没有
> `data:source:delete`）；连接测试需要 `data:source:test`；抽取需要 `data:source:extract`。
> **这五个码均已授予评估人员**，因此本组接口对**评估人员与系统管理员都可用**（菜单「数据源管理」对其可见），
> 评估人员可以自行完成「配置只读账号 → 连接测试 → 表清单 / 字段预览 → 单条 SELECT 抽取生成数据批次」，
> 也可以编辑甚至**删除**数据源（删除与编辑共用 `data:source:update`）。
> `/api/data-sources/db-types` 与 `/api/data-sources/validate-sql` 未加权限码，登录即可调用。
> **数据源密码不回显**：列表与详情返回的 `dbPassword` 为掩码 `******`（详情走 `getMasked`），明文只加密落库。
> 评估人员接入数据的另一种方式是 §5.2 的文件导入（XLSX / CSV），两种方式都会生成统一的数据批次。

### 5.2 数据批次 `/api/data-batches`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/data-batches?projectId=&sourceType=&status=&keyword=&page=&size=` | 分页 |
| GET | `/api/data-batches/{id}` | 详情（含 `qualityReport`、`errorDetail` JSON 字符串） |
| POST | `/api/data-batches/import` | `multipart/form-data`：`file, projectId?, targetVersionId?` → `{batchId,batchCode,totalRows,validRows,errorRows,objectCount,indicatorCount,status,qualityReport,errors[],blocking}` |
| GET | `/api/data-batches/{id}/records?limit=` | 原始记录 |
| GET | `/api/data-batches/{id}/errors` | 错误行 `[{rowNo,objectCode,dataTime,indicatorCode,indicatorValue,unit,errorMsg}]` |
| GET | `/api/data-batches/{id}/quality-report` | 质量报告 |
| POST | `/api/data-batches/{id}/revalidate` | 重新校验 |
| DELETE | `/api/data-batches/{id}` | 删除 |
| GET | `/api/data-batches/template` | 下载数据导入模板 xlsx |
| GET | `/api/data-batches/{id}/errors/export` | 下载错误明细 xlsx |

质量报告字段：`totalRows, validRows, errorRows, objectCount, distinctIndicatorCount, missingValueCount, outOfRangeCount, unitMismatchCount, formatErrorCount, requiredFieldMissingCount, duplicateCount, blocking, blockingReasons[], ruleSeverities{}`

数据模板列：`project_code, object_code, object_name, data_time, indicator_code, indicator_value, unit, source_batch, remark`

### 5.3 快照 `/api/snapshots`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/snapshots?projectId=&status=&keyword=&page=&size=` | 分页 |
| GET | `/api/snapshots/{id}` | 详情 |
| GET | `/api/snapshots/{id}/overview?maxObjects=` | `{snapshot, objects[], indicators[], missingCount, outlierCount, recordCount, values[]}` |
| GET | `/api/snapshots/{id}/values` | 全部快照值 |
| POST | `/api/snapshots` | `{batchId, indicatorVersionId, remark}` → `{snapshotId, snapshotCode, recordCount, objectCount, indicatorCount, missingCount, outlierCount, dataRange, qualityReport}` |
| DELETE | `/api/snapshots/{id}` | 删除（已被任务使用则拒绝） |

## 6. 评估任务 `/api/evaluation-tasks`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/evaluation-tasks?projectId=&algorithm=&status=&keyword=&from=&to=&page=&size=` | 分页 |
| GET | `/api/evaluation-tasks/recent?limit=` | 最近任务 |
| POST | `/api/evaluation-tasks` | 创建并异步执行 |
| GET | `/api/evaluation-tasks/{id}` | `{task, parameters, inputSummary, history[], resultCount, consistency[]}` |
| GET | `/api/evaluation-tasks/{id}/status` | `{status, progress, currentStep, errorCode, errorMsg, resultCount, durationMs, startedAt, finishedAt}` |
| GET | `/api/evaluation-tasks/{id}/history` | 状态历史 |
| POST | `/api/evaluation-tasks/{id}/cancel` / `/retry` | 取消 / 重试（重试返回新任务号） |

创建任务请求体：

```json
{
  "projectId": 1,
  "snapshotId": 1,
  "indicatorVersionId": 1,
  "algorithm": "WEIGHTED",
  "taskName": "方案对比评估",
  "parameters": { }
}
```

**算法与 parameters 约定**

- `WEIGHTED` 加权平均法：`{"gradeNames":["优","良","中","差"],"gradeThresholds":[90,80,60,0]}`（可省略，用默认值）
- `FUZZY` 模糊综合评判：
  `{"gradeNames":["优","良","中","差"],"gradeScores":[95,85,70,55],"operator":"M_PLUS"}`
  operator 取值：`M_PLUS`(·,+) 加权平均型、`M_MIN_MAX`(∧,∨)、`M_PLUS_MAX`(·,∨)、`M_MIN_PLUS`(∧,⊕)
- `AHP` 层次分析法：必须提供判断矩阵
  - 单层：`{"criteria":["A11","A12","A21"],"matrix":[[1,2,6],[0.5,1,3],[0.1667,0.3333,1]],"gradeThresholds":[90,80,60,0]}`
  - 多层：`{"nodes":[{"nodeCode":"ROOT","nodeName":"总目标","criteria":["A1","A2"],"matrix":[[1,3],[0.3333,1]]},{"nodeCode":"A1",...}]}`
  - 可选 `"allowForceOnCrFail": true` 允许 CR 不通过时强制计算
- `COMBO` 组合流程：`{"comboFlow":["AHP","WEIGHTED"], "criteria":[...], "matrix":[...]}`

任务状态：`DRAFT / VALIDATING / QUEUED / RUNNING / SUCCESS / FAILED / CANCELED / TIMEOUT / PENDING_REVIEW / PUBLISHED / ARCHIVED`

## 7. 评估结果 `/api/evaluation-results`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/evaluation-results?projectId=&taskId=&algorithm=&level=&publishStatus=&objectCode=&page=&size=` | 分页 |
| GET | `/api/evaluation-results/by-task/{taskId}` | 任务下全部对象结果（按得分降序，含 `ranking`） |
| GET | `/api/evaluation-results/{id}` | 完整视图：`{result, details[], parsedVector[], weights{}, contributions{}, warnings[], extra{}, text{}, traceability{}, charts{}}` |
| GET | `/api/evaluation-results/{id}/text` | 文本展示：`{conclusion:{summary, weakIndicators[], strongIndicators[], level, score}, warnings[], improvements[]}` |
| GET | `/api/evaluation-results/{id}/details?keyword=&sortBy=&order=&page=&size=` | 表格展示（sortBy ∈ indicatorCode/weight/score/contribution/normalizedValue/rawValue） |
| GET | `/api/evaluation-results/charts?taskId=&resultId=&projectId=&chartType=all` | 图形数据总集 |
| GET | `/api/evaluation-results/charts/{chartType}?taskId=&resultId=&projectId=` | 单个图形 |
| GET | `/api/evaluation-results/compare?projectId=&taskId=` | 横向对比 `{indicatorCodes[], rows[]}` |
| POST | `/api/evaluation-results/{id}/review` | `{action:"SUBMIT"\|"APPROVE"\|"REJECT", comment}` |
| POST | `/api/evaluation-results/{id}/publish` / `/archive` | 发布 / 归档 |
| POST | `/api/evaluation-results/{id}/exports` | `{type:"EXCEL"\|"WORD"\|"PDF"}` → `{reportId, reportCode, fileName, downloadUrl}` |

> 复核 `/review`、发布 `/publish` 与归档 `/archive` 分别需要 `result:review` / `result:publish`，
> 横向对比 `/compare` 需要 `result:compare` 或 `result:list` 之一，导出 `/exports` 需要 `result:export`——
> 这些权限码**均已授予评估人员**，即「执行评估 → 复核 → 发布」闭环与结果横向对比、导出
> **评估人员与系统管理员都能调用**，不需要管理员介入。
> 其中 `/exports`（以及结果列表页的导出）只是**第一步**：它返回 `{reportId, downloadUrl}`，
> 前端随后调用 §8 的 `GET /api/reports/{id}/download` 取回文件，该下载接口同样对 `result:export` 放行。

### 7.1 三类展示形式
- **文本**：`/text` 的 `conclusion.summary`、`weakIndicators`、`strongIndicators`、`improvements`
- **表格**：`/details`（排序、筛选、分页）；字段 `indicatorCode, indicatorName, unit, direction, rawValue, normalizedValue, weight, contribution, score, grade`
- **图形**：`/charts`，共 8 类

### 7.2 八类图形数据结构（`/charts` 返回对象的键）

所有图形都带 `chartType, title, unit, description, dataSource, generatedAt`；
当数据不足时返回 `emptyReason`（**必须展示原因而不是空图**）。

| 键 | 图形 | 数据结构 |
|---|---|---|
| `bar` | 柱状图 | `{categories[], series:[{name,type:'bar',data[]}]}`；series 为 得分/贡献值/权重 |
| `line` | 折线图 | `{categories[], series:[{name,type:'line',data[]}], objectCode}` |
| `pie` | 饼图 | `{weightPie:[{name,value,percent}], levelPie:[{name,value}], series, showPercent}` |
| `radar` | 雷达图 | `{indicatorNames[], indicatorCodes[], max:100, series:[{name,type:'radar',value[]}]}` |
| `scatter` | 散点图 | `{xIndicatorCode, yIndicatorCode, xIndicatorName, yIndicatorName, availableIndicators[{code,name}], points:[[x,y,对象]]}` |
| `heatmap` | 热力图 | `{xLabels[], yLabels[], data:[{value:[x,y,得分],indicatorCode,objectCode,indicatorName}], min, max}` |
| `boxplot` | 箱线图 | `{categories[], boxData:[[min,q1,median,q3,max]], outliers:[[...]]}` |
| `rankBar` | 排名条形图 | `{categories[], rankings[], levels[], series:[{name,data[]}]}` |

## 8. 报告 `/api/reports`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/reports?projectId=&reportType=&taskId=&page=&size=` | 分页 |
| POST | `/api/reports` | `{taskId, resultId?, type:"EXCEL"\|"WORD"\|"PDF"}` |
| GET | `/api/reports/{id}` | 详情 |
| GET | `/api/reports/{id}/download` | 下载（浏览器可用 `?token=` 方式） |

> **权限码**：列表需 `report:list`，生成需 `report:create`，下载需 `report:download`
> （三者均为 `hasAuthority(...) or hasRole('SYSTEM_ADMIN')`，因此系统管理员始终可用）。
>
> **本模块仅系统管理员可用**：评估人员已不再持有任何 `report:*` 权限，因此
> - `GET /api/reports`（列表）、`GET /api/reports/{id}`（详情，与列表同用 `report:list`）与 `POST /api/reports`（生成）
>   对评估人员返回 HTTP **403** `{"code":2003,"message":"无权访问该资源"}`；
> - 前端「任务详情」页原「导出报告」按钮走的就是 `POST /api/reports`（需 `report:create`），故评估人员看不到该按钮；
> - `GET /api/reports/{id}/download` 除外——其鉴权为
>   `hasAuthority('report:download') or hasAuthority('result:export') or hasRole('SYSTEM_ADMIN')`。
>
> 下载接口放行 `result:export` 的原因：**「评估结果」页（§7）的导出会调用本接口下载**。
> 该导出是两步——先 `POST /api/evaluation-results/{id}/exports`（需 `result:export`）生成报告，
> 再调本接口取回刚生成的文件（响应里的 `downloadUrl` 即指向这里）；若下载只认 `report:download`，
> 评估人员的结果导出会以 403 中断。放行只保证「自己生成的那份报告」能取回，
> 不会让评估人员获得浏览或生成报告的能力（列表与生成仍只认 `report:*`）。

## 9. 驾驶舱 `/api/dashboard/overview`

```json
{
  "projectCount": 1, "activeProjectCount": 1, "archivedProjectCount": 0,
  "indicatorVersionCount": 1, "userCount": 5, "enabledUserCount": 5,
  "algorithmCount": 4, "algorithms": {"AHP":"java-ahp-1.0.0","FUZZY":"...","WEIGHTED":"...","COMBO":"..."},
  "taskTotal": 4, "taskRunning": 0, "taskSuccess": 4, "taskFailed": 0,
  "taskPendingReview": 0, "taskToday": 4,
  "batchCount": 1, "snapshotCount": 1, "resultCount": 12, "publishedResultCount": 0,
  "avgScore": 80.21,
  "levelDistribution": [{"level":"良","count":6}],
  "algorithmUsage": [{"algorithm":"WEIGHTED","algorithmLabel":"加权平均法","algorithmVersion":"...","count":3}],
  "latestResults": [{"id","taskId","taskCode","projectCode","objectCode","objectName","totalScore","level","ranking","algorithm","algorithmLabel","createdAt"}],
  "alerts": [{"taskId","taskCode","taskName","status","errorCode","errorMsg","createdAt"}],
  "taskTrend": [{"date":"2026-09-22","taskCount":4}],
  "projects": [{"id","projectCode","projectName","status","taskCount","resultCount","avgScore"}],
  "generatedAt": "..."
}
```

## 10. 前端路由与页面域（方案 7.4 混合式 UI）

| 路由 | 页面 | 风格 |
|---|---|---|
| `/login` | 登录 | 深色品牌页 |
| `/dashboard` | 综合驾驶舱 | 深色侧栏 + 可视化卡片 |
| `/projects` | 项目管理 | 浅色工作台 |
| `/indicators` | 指标体系（导入、树、校验结果、发布） | 浅色工作台 |
| `/data-sources` | 数据源（连接测试、表清单、字段预览、抽取；评估人员与系统管理员均可见，`menu: 'dataSource'`） | 浅色工作台 |
| `/data-batches` | 数据接入（上传、质量报告、错误明细） | 浅色工作台 |
| `/snapshots` | 数据快照 | 浅色工作台 |
| `/tasks` | 评估任务列表 | 浅色工作台 |
| `/workbench` | 评估工作台（分步：选项目→选指标版本→选快照→选算法→配参数→提交） | 分步引导 |
| `/tasks/:id` | 任务详情（状态机、日志、失败原因、重试） | 浅色工作台 |
| `/results` | 结果列表 | 浅色工作台 |
| `/results/:id` | 结果分析（文本 / 表格 / 图形 三标签 + 追溯信息 + 导出） | 混合 |
| `/reports` | 报告导出记录（仅系统管理员可见，`menu: 'report'`） | 浅色工作台 |
| `/baselines` | 算法基准测试 | 浅色工作台 |
| `/system/users` | 用户管理 | 浅色工作台 |
| `/system/roles` | 角色权限 | 浅色工作台 |
| `/system/audit` | 审计日志 | 浅色工作台 |
| `/system/config` | 系统参数 | 浅色工作台 |
