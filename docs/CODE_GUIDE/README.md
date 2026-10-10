# 效能评估系统 · 代码详解（总索引）

> 面向：需要读懂、维护、答辩、交接这套代码的开发者与学生。
> 覆盖：仓库内 **178 个源文件 / 34,315 行代码**（行数口径见第五节），按 **14 个分册 + 1 份写作规范** 全部覆盖，一个文件都不漏。

---

## 一、先说清楚：这份文档的粒度

你要求的是"每个文件里的每句代码是干什么的"。真实情况是——这个仓库有 **178 个文件、34,315 行代码**（行数口径：**逻辑行数**，以换行符计、文件末尾的换行不另计一行；含空行与注释，与各分册一致，统计方法与注意事项见第五节开头的口径说明）。
如果严格一行一条，会产出约 3 万条说明、上百 MB 文本，既写不完，也没有人能读。

因此本套文档采用工程上真正可用的粒度，并且**明确告诉你哪里合并了**：

| 内容类型 | 粒度 |
|---|---|
| `package` / `import` 连续段 | **合并为一行**说明「导入了什么、为什么需要」 |
| 纯符号行（`}`、`)`、空行） | **并入上一块**的结束行 |
| 一组连续的字段声明 / 一组 getter-setter | **合并为一行**说明这组字段的作用 |
| **任何有业务含义的语句**（判断、赋值、调用、异常处理、SQL、模板指令、关键样式） | **单独一行**，给真实行号 + 代码片段 + 说明 |
| `package-lock.json`（2618 行） | **不做逐行说明** —— 它是 npm 自动生成的锁文件，不是人写的代码。这一点如实告知，不假装覆盖 |

另外，文档不只翻译代码，还解释**为什么这么写**，并把能对应上技术方案的地方标出来（如「对应方案 5.4 可追溯要求」）。

---

## 二、分册清单

| 分册 | 标题 | 覆盖文件数 | 主要读者 |
|---|---|---|---|
| [`_写作规范.md`](_写作规范.md) | 写作规范（分册必须遵守） | — | 文档维护者 |
| [`01_后端_工程与启动配置.md`](01_后端_工程与启动配置.md) | 后端 · 工程结构与启动配置 | 15 | 全员必读 |
| [`02_后端_公共_安全_DTO_工具.md`](02_后端_公共_安全_DTO_工具.md) | 后端 · 公共基础、安全认证、DTO 与工具类 | 18 | 全体后端 |
| [`03_后端_领域模型与仓储.md`](03_后端_领域模型与仓储.md) | 后端 · 领域模型（JPA 实体）与数据访问 | 49 | 后端 / 数据库 |
| [`04_后端_算法引擎.md`](04_后端_算法引擎.md) | 后端 · 评估算法引擎（AHP / 模糊 / 加权 / 组合） | 12 | 算法负责人 |
| [`05_后端_业务服务_上.md`](05_后端_业务服务_上.md) | 后端 · 业务服务（一）：认证、审计、编号、参数、指标、项目 | 6 | 后端 |
| [`06_后端_业务服务_下.md`](06_后端_业务服务_下.md) | 后端 · 业务服务（二）：数据、快照、任务、结果、报告、驾驶舱、基准 | 8 | 后端核心 |
| [`07_后端_接口层.md`](07_后端_接口层.md) | 后端 · REST 接口层（Controller） | 12 | 前后端对接 |
| [`08_前端_框架与核心.md`](08_前端_框架与核心.md) | 前端 · 工程配置、骨架、路由、状态、接口、工具、公共组件 | 23 | 全体前端 |
| [`09_前端_页面_登录驾驶舱与项目.md`](09_前端_页面_登录驾驶舱与项目.md) | 前端 · 页面（一）：登录、404、驾驶舱、项目管理 | 4 | 前端 |
| [`10_前端_页面_指标体系与数据接入.md`](10_前端_页面_指标体系与数据接入.md) | 前端 · 页面（二）：指标体系、数据源、数据批次、快照 | 5 | 前端 |
| [`11_前端_页面_评估任务.md`](11_前端_页面_评估任务.md) | 前端 · 页面（三）：评估工作台、任务列表、任务详情 | 3 | 前端 |
| [`12_前端_页面_结果分析与报告.md`](12_前端_页面_结果分析与报告.md) | 前端 · 页面（四）：结果列表、结果分析、报告、算法基准 | 4 | 前端核心 |
| [`13_前端_页面_系统管理.md`](13_前端_页面_系统管理.md) | 前端 · 页面（五）：用户、角色权限、审计、系统参数 | 4 | 前端 / 运维 |
| [`14_数据库脚本与运维脚本.md`](14_数据库脚本与运维脚本.md) | 数据库脚本、运维脚本与样例数据 | 15 | 部署 / DBA |

**合计 178 个文件 / 34,315 行** —— 与仓库实际文件数、行数一致（上表 14 个分册的文件数相加 = 178；第五节的 178 条行数相加 = 34,315）。`package-lock.json`、`README.md`、`docs/*.md`、`.gitignore` 等**不计入**这 178 个（见第五节末尾的如实说明）。

---

## 三、按角色选阅读路径

| 你想干什么 | 建议顺序 |
|---|---|
| **第一次接手，想快速上手** | `01` 工程与启动 → `03` 领域模型（先看分组表，理解有哪些表）→ `06` 任务与结果服务（核心链路）→ `12` 结果分析页 |
| **要改/调试后端接口** | `02` 公共与安全（统一响应/异常/权限）→ `07` 接口层 → 对应 `05`/`06` 服务 |
| **要改算法** | `04` 算法引擎（含全部数学公式）→ `03` 中 `Indicator`/`SnapshotValue`/`EvaluationResult` 三张表的字段语义 → `samples/` 里的基准样例 |
| **要改前端页面** | `08` 框架与核心（先看 `api/request.ts`、`utils/format.ts`、`utils/chartOptions.ts`、公共组件）→ 对应的 `09`~`13` 分册 |
| **要答辩/讲清设计** | 本索引第四节「关键设计决策速查」→ `03` 分组说明 → `06` 的可追溯设计 → `14` 的数据库设计 |
| **要做部署/运维** | `01` 配置（`application*.yml` 逐项）→ `14` 运维脚本（含踩过的坑）→ 另见 `docs/DEPLOY.md` |

---

## 四、关键设计决策速查（最容易被问到的地方）

| # | 问题 | 结论 | 详见 |
|---|---|---|---|
| 1 | 为什么实体之间**不用 JPA 关联注解**，全用 `Long` 外键 id？ | 避免懒加载与 JSON 序列化互相纠缠、便于分页与审计；代价是关联要自己查 | `03` |
| 2 | 为什么 JSON 存成 `TEXT` 而不是 `JSON` 列？ | 各数据库对 JSON 列支持不一致（要兼容甲方库），且这些字段只整体读写、不按 JSON 查询 | `03`、`14` |
| 3 | 为什么 `SnapshotValue` 同时存原始值和标准化值？ | 方案 5.4 要求"原始数据与标准化数据分别保存"，历史结果不因源数据变化而改变 | `03`、`06` |
| 4 | 快照为什么不随源数据变化？ | 评估只读快照；快照生成后不可变，`USED` 后连删除都禁止 | `06` |
| 5 | 一行数据格式非法，为什么不会阻断整批？ | 只有「必填缺失 / 缺失值(BLOCK) / 指标未覆盖 / 方向未确认」才阻断；格式非法只标记该行（这是修过的真实缺陷） | `06`、`14` |
| 6 | 审计日志为什么要在发起线程采集 `RequestInfo`？ | 异步线程拿不到 SecurityContext，且 `HttpServletRequest` 在响应结束后会被回收（也是修过的真实缺陷） | `02`、`01` |
| 7 | 业务编号为什么不用进程内自增？ | 服务重启后计数器归零会生成重复编号 → 唯一键冲突；改为按数据库当天最大值推导（修过的真实缺陷） | `05` |
| 8 | 图形数据为什么全在后端算？ | 方案 7.3 不允许前端重算导致与表格数值不一致；八类图形的数据都来自同一结果数据集 | `06`、`12` |
| 9 | 模糊综合评判为什么存整个向量？ | 方案 6.3 明确要求"输出完整综合评判向量，不能只保存最大隶属度对应的等级" | `04`、`03` |
| 10 | `AlgorithmRegistry` 为什么不用 `@Component`？ | 需要按 `ees.algorithm.engine` 配置在 JAVA_NATIVE / CPP_ENGINE 之间切换，由 `AlgorithmConfig` 显式装配 | `01`、`04` |
| 11 | 后端为什么能单独出页面？ | dev 环境用 `spring.web.resources.static-locations` 托管 `frontend/dist`，首页由 Spring Boot 自动映射，一个进程即可 | `01` |
| 12 | 前端为什么用 hash 路由？ | 离线部署到任意子路径时不需要 Nginx rewrite；也正因如此不需要 history fallback；单 jar 部署（前端已打进 jar 的 `classpath:/static/`）时更不需要 rewrite | `08` |
| 13 | 为什么交付包里没有任何内置 JWT 密钥？ | 整改前 `application.yml` 与 `EesProperties` 各内置了一个默认密钥，随源码交付等同公开值，实测可据此伪造 `SYSTEM_ADMIN` 令牌越权读写全部接口，属真实漏洞；现由 `com.ees.security.JwtSecretProvider` 按「环境变量 `EES_JWT_SECRET` → 本地密钥文件 `<存储根>/.jwt-secret`（首次启动自动生成并持久化）→ 进程内随机」三级解析，启动横幅只打印密钥来源、不打印密钥本身 | `02`（配置项见 `01`） |
| 14 | 生产环境怎么部署？为什么只需要一个 jar？ | `backend/pom.xml` 在 `prepare-package` 阶段用 `maven-resources-plugin` 把 `frontend/dist` 复制进 jar 的 `classpath:/static/`，因此 prod 生产档单 jar 就能同时提供页面与接口，目标机器只需 JDK 21 + MySQL，不需要 Node、也不需要 Nginx；也可用 `scripts/start-offline.bat` 双击启动 | `14`、`01` |

---

## 五、完整文件清单（按分册归属）

> **行数口径**：本索引（以及本套分册）所有行数均按**逻辑行数**统计 —— **以换行符计，文件末尾的换行不另计一行**，含空行与注释。
> **实测方法（务必沿用）**：UTF-8 文件用 `[IO.File]::ReadAllLines($p).Count`（等价于数 LF 字节）；`.bat` 脚本是 GBK(936) 编码，用 `[IO.File]::ReadAllLines($p, GBK).Count`。
> **不要用不带 `-Encoding` 的 `Get-Content`** —— 它按 ANSI(GBK) 误解码 UTF-8 中文，会吞掉行（例如 `security/JwtService.java` 从 123 少算成 117、`security/JwtSecretProvider.java` 从 192 少算成 175、`security/LoginUser.java` 从 125 少算成 124），这正是本索引旧数字普遍偏小的原因。本轮 178 个文件已用 `ReadAllLines` 与 LF 字节两法实测对照，**结果逐条一致**。
> 带 ⚠️ 的是项目里最长、最需要重点读的文件。
> 14 个分册的文件数相加 = **178**，各行数相加 = **34,315**，与开头「178 个源文件 / 34,315 行代码」一致（本节 178 条行数均为 `[IO.File]::ReadAllLines` 磁盘实测）。各分册行数与对应分册末尾的「本册共覆盖」自检一致（`01` 1480、`02` 1786、`03` **2595**、`05` 1557、`06` **4326**、`07` **1574**、`08` **2622**、`09` **2136**、`11` 2773、`14` 2051；其中 `02` 的 `security` 5 个文件 = 548 行）。**提交 `5f8ba79`（驾驶舱图表支持自定义统计区间）使 `03` +18、`06` +38、`07` +5、`08` +12、`09` +112，合计 34,130 → `34,315`**（`03` 的两个 Repository 与 `06` 的 `DashboardService` 归同事负责，本索引只更新数字）。

### 分册 01 —— 工程结构与启动配置（15 个文件 / 1480 行）

| 文件 | 行数 |
|---|---|
| `pom.xml`（聚合工程） | 33 |
| `backend/pom.xml` | 210 |
| `backend/src/main/java/com/ees/EesApplication.java` | 39 |
| `backend/src/main/java/com/ees/config/EesProperties.java` | 440 |
| `backend/src/main/java/com/ees/config/SecurityConfig.java` | 99 |
| `backend/src/main/java/com/ees/config/WebConfig.java` | 55 |
| `backend/src/main/java/com/ees/config/AsyncConfig.java` | 45 |
| `backend/src/main/java/com/ees/config/AlgorithmConfig.java` | 49 |
| `backend/src/main/java/com/ees/config/AuditFilter.java` | 95 |
| `backend/src/main/java/com/ees/config/StorageConfig.java` | 51 |
| `backend/src/main/java/com/ees/config/DataInitializer.java` | 36 |
| `backend/src/main/java/com/ees/config/StartupBanner.java` | 121 |
| `backend/src/main/resources/application.yml` | 147 |
| `backend/src/main/resources/application-dev.yml` | 40 |
| `backend/src/main/resources/application-prod.yml` | 20 |

### 分册 02 —— 公共基础、安全、DTO、工具（18 个文件 / 1786 行）

| 文件 | 行数 |
|---|---|
| `common/ApiResponse.java` | 103 |
| `common/BizException.java` | 54 |
| `common/ErrorCode.java` | 70 |
| `common/GlobalExceptionHandler.java` | 145 |
| `common/JsonUtils.java` | 123 |
| `common/PageResult.java` | 86 |
| `security/JwtSecretProvider.java`（新增：JWT 密钥三级解析） | 192 |
| `security/JwtService.java` | 123 |
| `security/JwtAuthenticationFilter.java` | 61 |
| `security/LoginUser.java` | 125 |
| `security/SecurityUtils.java` | 47 |
| `dto/LoginRequest.java` | 23 |
| `dto/LoginResponse.java` | 32 |
| `dto/UserSaveRequest.java` | 41 |
| `dto/ChangePasswordRequest.java` | 18 |
| `util/TabularReader.java` | 276 |
| `util/ExcelTemplateWriter.java` | 183 |
| `util/CryptoUtils.java` | 84 |

### 分册 03 —— 领域模型与仓储（49 个文件 / 2595 行）

**domain（25）**：`BaseEntity`(45)、`SysRole`(111)、`SysUser`(215)、`SysAuditLog`(67)、`SysConfig`(32)、`Project`(68)、`ProjectMember`(37)、`IndicatorVersion`(77)、`Indicator`(77)、`IndicatorImportBatch`(58)、`DataSourceEntity`(80)、`DataSourceField`(48)、`DataBatch`(95)、`RawDataRecord`(65)、`FieldMapping`(46)、`DataSnapshot`(85)、`SnapshotValue`(59)、`EvaluationTask`(117)、`TaskStatusHistory`(42)、`EvaluationResult`(102)、`ResultDetail`(70)、`AhpConsistency`(67)、`ReportRecord`(65)、`DataQualityRule`(49)、`AlgorithmBaseline`(56)

**repository（24）**：`SysUserRepository`(37)、`SysRoleRepository`(16)、`SysAuditLogRepository`(30)、`SysConfigRepository`(16)、`ProjectRepository`(39)、`ProjectMemberRepository`(20)、`IndicatorVersionRepository`(54)、`IndicatorRepository`(19)、`IndicatorImportBatchRepository`(13)、`DataSourceRepository`(31)、`DataSourceFieldRepository`(15)、`DataBatchRepository`(41)、`RawDataRecordRepository`(30)、`FieldMappingRepository`(15)、`DataSnapshotRepository`(41)、`SnapshotValueRepository`(32)、`EvaluationTaskRepository`(**76**)、`TaskStatusHistoryRepository`(11)、`EvaluationResultRepository`(**113**)、`ResultDetailRepository`(40)、`AhpConsistencyRepository`(13)、`ReportRecordRepository`(29)、`DataQualityRuleRepository`(15)、`AlgorithmBaselineRepository`(16)

### 分册 04 —— 算法引擎（12 个文件 / 2126 行）

| 文件 | 行数 |
|---|---|
| `algorithm/AlgorithmType.java` | 47 |
| `algorithm/AlgorithmEngine.java` | 20 |
| `algorithm/AlgorithmRegistry.java` | 44 |
| `algorithm/AlgorithmRequest.java` | 136 |
| `algorithm/AlgorithmResult.java` | 94 |
| `algorithm/MathSupport.java` | 223 |
| `algorithm/ScoringSupport.java` | 157 |
| `algorithm/ahp/AhpEngine.java` ⚠️ | 445 |
| `algorithm/fuzzy/FuzzyEngine.java` ⚠️ | 391 |
| `algorithm/weighted/WeightedAverageEngine.java` | 280 |
| `algorithm/combo/ComboEngine.java` | 187 |
| `algorithm/cpp/CppEngineAdapter.java` | 102 |

### 分册 05 —— 业务服务（一）（6 个文件 / 1557 行）

| 文件 | 行数 |
|---|---|
| `service/AuthService.java` | 280 |
| `service/AuditService.java` | 169 |
| `service/CodeService.java` | 92 |
| `service/ConfigService.java` | 111 |
| `service/IndicatorService.java` ⚠️ | 613 |
| `service/ProjectService.java` | 292 |

### 分册 06 —— 业务服务（二）（8 个文件 / 4326 行）

| 文件 | 行数 |
|---|---|
| `service/DataBatchService.java` ⚠️ | 691 |
| `service/DataSourceService.java` ⚠️ | 424 |
| `service/SnapshotService.java` ⚠️ | 392 |
| `service/TaskService.java` ⚠️ | 738 |
| `service/ResultService.java` ⚠️ | 893 |
| `service/ReportService.java` ⚠️ | 672 |
| `service/DashboardService.java` | **305** |
| `service/BaselineService.java` | 211 |

### 分册 07 —— 接口层（12 个文件 / 1574 行）

`AuthController`(79)、`HealthController`(91)、`UserController`(218)、`ProjectController`(104)、`IndicatorController`(146)、`DataSourceController`(159)、`DataBatchController`(143)、`SnapshotController`(88)、`TaskController`(102)、`ResultController`(166)、`ReportController`(106)、`SystemController`(**172**)

### 分册 08 —— 前端框架与核心（23 个文件 / 2622 行）

| 文件 | 行数 |
|---|---|
| `frontend/package.json` | 32 |
| `frontend/vite.config.ts` | 50 |
| `frontend/tsconfig.json` | 7 |
| `frontend/tsconfig.app.json` | 27 |
| `frontend/tsconfig.node.json` | 13 |
| `frontend/env.d.ts` | 16 |
| `frontend/index.html` | 52 |
| `frontend/src/main.ts` | 31 |
| `frontend/src/App.vue` | 25 |
| `frontend/src/router/index.ts` | 235 |
| `frontend/src/stores/user.ts` | 90 |
| `frontend/src/stores/app.ts` | 30 |
| `frontend/src/api/request.ts` | 190 |
| `frontend/src/api/index.ts` | **211** |
| `frontend/src/utils/format.ts` | 187 |
| `frontend/src/utils/chartOptions.ts` | 415 |
| `frontend/src/styles/index.scss` | **367** |
| `frontend/src/components/EesChart.vue` | 75 |
| `frontend/src/components/ChartCard.vue` | 116 |
| `frontend/src/components/StatCard.vue` | 23 |
| `frontend/src/components/LevelTag.vue` | 9 |
| `frontend/src/components/PageHeader.vue` | 15 |
| `frontend/src/layouts/MainLayout.vue` | 406 |

### 分册 09 —— 页面（一）（4 个文件 / 2136 行）

`views/Login.vue`(443)、`views/NotFound.vue`(95)、`views/Dashboard.vue`(**760**)、`views/project/ProjectList.vue`(838)

### 分册 10 —— 页面（二）（5 个文件 / 4365 行）

`views/indicator/IndicatorVersionList.vue`(753)、`views/indicator/IndicatorVersionDetail.vue`(734)、`views/data/DataSourceList.vue`(931)、`views/data/DataBatchList.vue`(1136)、`views/data/SnapshotList.vue`(811)

### 分册 11 —— 页面（三）（3 个文件 / 2773 行）

`views/task/EvaluationWorkbench.vue`(1618) ⚠️ 全项目最大、`views/task/TaskList.vue`(453)、`views/task/TaskDetail.vue`(702)

### 分册 12 —— 页面（四）（4 个文件 / 2980 行）

`views/result/ResultList.vue`(621)、`views/result/ResultDetail.vue`(1370) ⚠️、`views/report/ReportList.vue`(525)、`views/baseline/BaselineList.vue`(464)

### 分册 13 —— 页面（五）（4 个文件 / 1944 行）

`views/system/UserList.vue`(565)、`views/system/RoleList.vue`(472)、`views/system/AuditLogList.vue`(418)、`views/system/SystemConfig.vue`(489)

### 分册 14 —— 数据库脚本、运维脚本与样例（15 个文件 / 2051 行）

| 文件 | 行数 |
|---|---|
| `backend/src/main/resources/db/schema.sql` ⚠️ | 636 |
| `backend/src/main/resources/db/data.sql` | 68 |
| `db/00_create_database.sql` | 40 |
| `db/01_schema.sql` | 636 |
| `db/02_seed.sql` | 68 |
| `db/99_reset_demo_data.sql` | 31 |
| `scripts/start-all.bat` | 165 |
| `scripts/stop-all.bat` | 75 |
| `scripts/build-all.bat` | 90 |
| `scripts/start-offline.bat`（新增：离线单 jar 启动，prod 档） | 112 |
| `samples/指标体系样例.csv` | 7 |
| `samples/试验数据样例.csv` | 16 |
| `samples/试验数据（含错误行样例）.csv` | 5 |
| `samples/AHP判断矩阵样例.json` | 62 |
| `samples/算法参数样例.json` | 40 |

**未做逐行说明的文件（如实列出）**：`frontend/package-lock.json`（2618 行，npm 自动生成的依赖锁文件）、`README.md`、`docs/*.md`（文档本身）、`.gitignore`。这些不是业务代码，**也不计入上面的 178 个文件**（178 = 14 个分册的文件数之和）。

---

## 六、代码分层与主调用链（先建立骨架，再读分册）

```
浏览器
  │  (hash 路由 单页应用)
  ▼
frontend/src/views/*.vue ──调用──▶ frontend/src/api/index.ts ──▶ api/request.ts (axios + JWT)
                                                                      │  HTTP /api/**
                                                                      ▼
                                        ┌──────────── controller（分册 07）────────────┐
                                        │  只做参数接收、权限注解、装配响应              │
                                        └───────────────────┬─────────────────────────┘
                                                            ▼
                                        ┌──────────── service（分册 05 / 06）──────────┐
                                        │  业务规则、事务、状态机、八类图形数据          │
                                        └───┬───────────────┬───────────────┬─────────┘
                                            ▼               ▼               ▼
                                 repository(03)      algorithm(04)     util(02)
                                  JPA 24 张表        AHP/模糊/加权     表格解析/加密/报表
                                            │               │
                                            ▼               ▼
                                       MySQL 24 张表   算法引擎（内置 Java 或 C++ 适配位）
```

**读代码的推荐顺序（跟着一次评估走）**：

1. `views/task/EvaluationWorkbench.vue` 提交任务 → `api/index.ts` 的 `taskApi.create`
2. `TaskController.create` → `TaskService.create`（校验 + 落库 + 状态机）
3. `TaskService.buildRequest` 从 **快照** 装配算法输入 → `AlgorithmRegistry.get(算法).evaluate`
4. `AhpEngine` / `FuzzyEngine` / `WeightedAverageEngine` 计算 → 返回统一结构
5. `TaskService.saveResults` 写 `evaluation_result` + `result_detail`
6. `ResultController` → `ResultService.buildAllCharts` 产出八类图形数据
7. `views/result/ResultDetail.vue` + `utils/chartOptions.ts` 渲染

---

## 七、这份文档没做什么（避免误解）

1. **不是 API 文档**：接口契约在 `docs/API.md`；本套文档讲的是"代码怎么实现"。
2. **不是需求文档**：技术方案与需求在 `docs/` 其他文件与原始方案文档里；本套文档只在能对应上时标注"对应方案 X.Y"。
3. **`package-lock.json` 未逐行说明**：npm 自动生成，无人工逻辑。
4. **不含前端构建产物**：`frontend/dist/`、`frontend/node_modules/`、`backend/target/` 均为生成物，不在清单内。
