# 变更记录（CHANGELOG）

> **用途**：记录本系统的每一次修改，便于追溯与回退。
> **当前分支**：`main`　**记录方式**：一次修改对应一个提交（提交总数以 `git rev-list --count HEAD` 为准）

---

## 一、记录约定

1. **每一次修改都对应一个 git 提交**，一个提交只做一件事，便于单独回退。
2. 提交信息用中文写清三件事：**改了什么 / 为什么改 / 怎么验证的**。
3. 涉及数据库结构或数据的改动，会在提交信息里写明迁移语句与**备份文件位置**（数据库不在 git 里）。
4. 本文件按时间倒序记录每次修改；新增改动时在「三、变更记录」表格顶部追加一行。
5. 表格的「#」列是**提交的先后次序**（第 N 次提交），可用 `git rev-list --count <提交号>` 复核。
   **只更新本记录文件、不改动系统的提交不单列**，因此该列会有跳号（例如缺 17、20、23、26），这是正常的。

---

## 二、如何查看与回退

### 查看改动

```bat
cd /d D:\Git仓库\efficiency-evaluation-system

git log --oneline                  :: 一行一条，看全部提交
git log --stat                     :: 附带每个提交改了哪些文件
git show <提交号>                   :: 看某次提交的完整改动（如 git show 22ef380）
git diff <提交号1> <提交号2>         :: 比较两次提交之间的差异
```

### 回退某一次修改

```bat
:: 方式一（推荐）：撤销某次提交的改动，并生成一个反向提交 —— 历史完整保留，可再次前滚
git revert <提交号>

:: 方式二：只把某个文件恢复到某次提交时的样子
git checkout <提交号> -- <文件路径>

:: 方式三：整体退回到某次提交（会丢弃该提交之后的全部本地改动，慎用）
git reset --hard <提交号>
```

> **只回退某一次修改、不影响后面的修改**时，用方式一（`git revert`）；
> 若某次提交与后续提交改了同一个文件，`revert` 可能出现冲突，需手动解决后再提交。

### 构建与运行（改动后重新生效）

```bat
:: 前端改了 → 重新构建前端；后端改了 → 重新打包 jar（会把前端一起打进 jar）
scripts\build-all.bat

:: 运行（单 jar，浏览器打开 http://127.0.0.1:8080/）
scripts\start-offline.bat
```

### 数据库改动的回退

数据库内容不在 git 中，回退方式写在对应提交的说明里。目前只有一次涉及数据删除，备份位置：

| 时间 | 改动 | 备份文件 |
|---|---|---|
| 2026-09-30 | 角色精简（删除 3 个角色与 3 个账号） | `D:\Git仓库\_db_backup_before_role_reduction.sql`（含改动前的 5 个角色 + 5 个账号，用 `mysqldump` 导出，可直接用 `mysql < 文件` 恢复） |

此外，`db/02_seed.sql`（以及后端 `resources/db/data.sql`）始终是「一套完整可重建的基础数据」，
需要重建基础数据时执行 `db/00_create_database.sql` → `db/01_schema.sql` → `db/02_seed.sql` 即可。

---

## 三、变更记录

| # | 时间 | 提交 | 变更内容 | 主要文件 |
|---|---|---|---|---|
| 48 | 2026-09-30 20:21 | `5f8ba79` | **功能**：驾驶舱两张图支持自定义统计区间 —— 「任务趋势」默认仍近 7 天，新增**起止日期选择**与**快捷区间下拉（近一周 / 近一个月 / 近一年 / 自定义）**；右侧「评判等级分布」改为**统计同一区间并联动**（原先它统计的是全部结果，口径与趋势图不一致）。接口 `/api/dashboard/overview` 新增 `from`/`to`；区间最长 366 天、前端超限自动截断；指标卡仍是"当前总量"不随区间变化。顺带修掉趋势图原先只在「最近 20 条任务」里数数、区间一长就漏计的缺陷 | `SystemController.java`、`DashboardService.java`、2 个 `repository/*.java`、`api/index.ts`、`styles/index.scss`、`Dashboard.vue` |
| 46 | 2026-09-30 20:00 | `da6af44` | **界面修复**：驾驶舱「任务趋势（近 7 天）」折线图的 y 轴名称「任务数」上半截被裁（看起来像显示不全）。原因是 ECharts 把轴名称画在**网格上沿之上**再叠加 `nameGap`（默认 15），而该图 `grid.top` 只留了 26。已把 `top` 改为 46 并设 `nameGap: 10`（`Dashboard.vue` 644 → 648 行） | `frontend/src/views/Dashboard.vue` |
| 44 | 2026-09-30 19:49 | `2a3d1d9` | **权限开放**：给评估人员补上「数据库接入」—— 数据源管理菜单 + 五个权限码。此前评估人员只有文件导入，进不到「数据源管理」，无法用只读账号连甲方业务库抽取数据。现在菜单 8 → **9** 个（补 `dataSource`，侧栏「数据接入」下三个子项齐全），权限 24 → **29** 项（补 `data:source:list/create/update/test/extract`）。另补齐权限字典缺失的 `data:source:create/update` 两条标签（33 → 35 项，原先新授权限在「角色权限」页不显示） | `AuthService.java`、`db/02_seed.sql`、`db/data.sql`、`UserController.java` |
| 42 | 2026-09-30 19:13 | `e295085` | **权限修复（续）**：上一轮只修了驾驶舱统计，**列表接口完全没做数据权限** —— 实测可见项目为 0 的评估人员仍能列出全部 15 个任务、48 条结果、6 个批次、6 个快照、4 个指标版本，并能按 id 直读别人的详情（任务/结果/批次记录均 200），写入路径也不校验目标项目。现在：5 个仓储的 search 查询加可见集合条件；5 个服务补「列表取交集 + 按 id 直读 403 + 写入校验项目可见」，结果服务的 8 个图表/对比/导出等 13 处按 taskId/resultId 取数全部加了守卫。实测评估人员六个列表全为 0 条、按 id 直读全部 403，管理员不受影响 | 5 个 `repository/*.java`、`TaskService.java`、`ResultService.java`、`DataBatchService.java`、`SnapshotService.java`、`IndicatorService.java` |
| 40 | 2026-09-30 18:53 | `747fec4` | **权限修复（重要）**：驾驶舱统计改为按数据权限过滤，**管理员仍看全部项目**。原先只有「项目数」真的过滤了，其余全是全库统计 —— 实测一个可见项目为 0 的评估人员账号竟能看到 15 个任务、48 条结果、平均分 80.23；且带 `?projectId=` 时不校验可见性，传别人的项目 id 即可读到该项目统计。现在统一按 `visibleProjectIds()` 取数（null=管理员不限制），不可见项目直接 403；空可见集合返回 0 并跳过查询。顺带修正「选中项目时项目数显示全部可见项目数」「算法使用恒为全库」两处。前端配套：登录/登出清空项目上下文、驾驶舱对失效项目 id 回退到「全部项目」，避免收紧后 403 | 6 个 `repository/*.java`、`DashboardService.java`、`stores/user.ts`、`views/Dashboard.vue` |
| 38 | 2026-09-30 18:41 | `c05676e` | **修复驾驶舱「平均得分」恒显示 “-”**：`DashboardService.overview()` 的「全部项目」分支（`scope == null`）当初只写了计数、没写聚合，`avgScore` 被写死为 `null`、`publishedResultCount` 被写死为 `0`。已补上对称的全局查询 `avgScoreAll()` 与 `countByPublishStatus(...)`。修复后「全部项目」显示平均分 **80.23**（库中直算 80.23）；「已发布」原先恒为 0 属潜在缺陷（当前结果全是 DRAFT 所以恰好一致），一并修正 | `EvaluationResultRepository.java`、`DashboardService.java` |
| 36 | 2026-09-30 18:29 | `1f8f374` | **界面修复**：「评估结果」列表的操作列里，「导出」下拉比前面的 `查看分析 / 复核 / 发布` 低一截。原因是 `el-dropdown` 自带 `line-height:1` 且为 inline-flex，与 `el-button` 平铺时基线不一致；该列此前没有任何布局约束。已用项目既有的 `.op-cell`（flex + `align-items:center` + gap）包裹四个操作项，与数据接入三个页面的写法一致（`ResultList.vue` 597 → 621 行） | `frontend/src/views/result/ResultList.vue` |
| 34 | 2026-09-30 18:19 | `0a82886` | **登录态改为会话级**：令牌由 `localStorage` 改存 `sessionStorage`，**关闭浏览器后再打开需重新登录**（原先会直接以上次那个账号进入系统，共用电脑上有身份串用风险）。同一标签页刷新不受影响；代价是新开标签页需重新登录。启动时额外清理历史遗留在 localStorage 的旧令牌。令牌有效期不变（仍 480 分钟） | `frontend/src/api/request.ts`、`frontend/src/main.ts` |
| 32 | 2026-09-30 17:07 | `fd12bf0` | **文档同步**：把报告导出收紧写入 3 份使用者文档 + 6 个代码详解分册与总索引。验收清单新增 3 条接口级权限条目，检查项数按实测重算（功能验收 76 → 79、合计 94 → 97）；索引合计 33,745 → **33,755**（07 册 1568、11 册 2773）；顺带改掉 08 分册里已过期的 `has([...])` 举例 | `README.md`、`docs/USER_GUIDE.md`、`docs/ACCEPTANCE.md`、`docs/API.md`、`docs/CODE_GUIDE/` 的 05/07/08/11/13/14 与总索引 |
| 31 | 2026-09-30 17:07 | `2e078e9` | **文档同步**：把「数据批次空状态说明」写入分册 10（`DataBatchList.vue` 1117 → 1136，本册小计 4346 → 4365）。该文档改动上一轮已由代理完成但**漏做提交**，本次补交 | `docs/CODE_GUIDE/10_前端_页面_指标体系与数据接入.md` |
| 30 | 2026-09-30 16:57 | `ce6c264` | **权限调整**：报告导出改为**仅系统管理员**，评估人员不再显示 —— 评估人员菜单 9 → **8** 个（去掉 `report`），权限 27 → **24** 项（去掉 `report:list`/`report:create`/`report:download`），任务详情页的「导出报告」按钮随之消失。为不打断「评估结果」页的导出（两步：先生成报告再下载），`GET /api/reports/{id}/download` 放宽为也认 `result:export`；报告列表与生成接口仍仅 `report:*`（评估人员 403）。另修正 `TaskDetail.vue` 一处判权写法（`has([...])` 是"任一满足"，与真实依赖不符） | `AuthService.java`、`db/02_seed.sql`、`db/data.sql`、`ReportController.java`、`TaskDetail.vue` |
| 28 | 2026-09-30 16:04 | `f8956fe` | **数据批次空状态说明**：为「校验依据的指标体系版本」下拉（导入数据文件对话框与生成快照对话框）补上"为空时的原因与办法"——指标体系版本在导入时绑定项目，选了未绑定任何版本的项目时下拉就是空的（`DataBatchList.vue` 1117 → 1136 行） | `frontend/src/views/data/DataBatchList.vue` |
| 27 | 2026-09-30 16:04 | `bae68cd` | **修复 .gitignore 误伤（重要）**：`.gitignore` 里不带斜杠的 `data/` 会匹配**任意层级**的同名目录，导致 `frontend/src/views/data/` 下 3 个源码文件（`DataSourceList.vue` / `DataBatchList.vue` / `SnapshotList.vue`）**从初始提交起就一直没进版本库** —— 克隆到别处或按提交回退后前端将无法构建。已把运行期目录规则锚定为 `/data/`、`/logs/`（`backend/data/` 不变），并补入这 3 个文件 | `.gitignore`、`frontend/src/views/data/` 下三个 .vue |
| 25 | 2026-09-30 12:20 | `8e8a8f0` | 文档与注释同步：把侧栏底部移除写入分册 07/08/09 与总索引（合计 33,760 → 33,726）；修正 4 处 `application.yml` 行号引用（`permit-paths` 76、`forbidden-keywords` 117、`audit.methods` 134、`ahp-cr-threshold` 103）；改掉 13 分册里把两处版本数据源混为一谈的表述 | `docs/CODE_GUIDE/`、`HealthController.java` |
| 24 | 2026-09-30 12:14 | `546ef92` | **主布局改版**：删除左侧栏底部的「离线内网部署」与版本号 `v1.0.0`。连带清掉只为它服务的 `version`、`onMounted` 里取版本号的 `/api/info` 调用、`.aside-foot*` 三条样式（`MainLayout.vue` 440 → 406 行）。**连带结果**：前端已无任何地方调用 `/api/info`，该接口只剩「运维只读探针」用途，仍免登录放行（原免登录理由已不成立，是否收紧待定） | `frontend/src/layouts/MainLayout.vue` |
| 22 | 2026-09-30 12:08 | `4339e18` | 文档与注释同步：把登录页页脚移除写入分册 07/09/13 与总索引（合计 33,806 → 33,760），并修正 `HealthController` 注释里「前端登录页展示」的过时表述 | `docs/CODE_GUIDE/`、`HealthController.java` |
| 21 | 2026-09-30 12:03 | `59aa33b` | **登录页改版**：删除底部「效能评估系统 v1.0.0 · 前后端分离 B/S 架构 · 可用算法 N 种」页脚。同时清掉只为它服务的死代码：`info` / `algorithmCount` / `algorithmText`、`onMounted` 里调用 `/api/info` 的整段、`systemApi` 与 `computed` 的导入、以及 `.login-foot` 相关样式（`Login.vue` 489 → 443 行）。`/api/info` 接口保留，改由主布局侧栏取版本号 | `frontend/src/views/Login.vue` |
| 19 | 2026-09-30 11:53 | `19508da` | **文档同步**：把权限调整写入 3 份使用者文档、4 个代码详解分册与总索引；顺带修正验收结论的统计（原写「功能验收 41 / 合计 59」与实际条目数不符，实为 76 / 10 / 8，已更正为合计 **94** 并逐条点算核对）；根 README 的评估人员权限说明同步 | `docs/USER_GUIDE.md`、`docs/ACCEPTANCE.md`、`docs/API.md`、`README.md`、`docs/CODE_GUIDE/` 的 05/07/13/14 与总索引 |
| 18 | 2026-09-30 11:48 | `d0a9773` | **权限调整**：评估人员补上 `result:review` / `result:publish` / `result:compare`（可独立走完「执行评估 → 复核 → 发布」闭环，此前只有系统管理员能做），并移除用不上的 `data:source:list`（无对应菜单，且该页展示外部库连接串）；权限字典补 `result:compare`；两份种子脚本与运行库同步（权限 25 → **27** 项） | `db/02_seed.sql`、`backend/.../db/data.sql`、`UserController.java` |
| 16 | 2026-09-30 11:47 | `290b10d` | **代码详解分册同步**：10 个分册 + 总索引与源码对齐（角色精简的直接同步，以及补齐此前几次提交「改了源码却漏更文档」造成的漂移）。索引合计由 33,703 行改为 **33,805 行**，178 条明细逐条与磁盘实测相符 | `docs/CODE_GUIDE/` 下 10 个分册 + `README.md` |
| 15 | 2026-09-30 11:40 | `93ac537` | 补齐 `SysRole` 类注释（原写「方案 4.1 五类角色」，已同步为两类角色）；验收条款 1.1.4 措辞精确化（实测为 HTTP **403** + 响应体 `code=2003`，不只写 code） | `SysRole.java`、`docs/ACCEPTANCE.md` |
| 14 | 2026-09-30 11:22 | `d4f31b1` | 面向使用者的四份文档同步为两类角色（README、USER_GUIDE、ACCEPTANCE、API；验收 1.1.2~1.1.4 由 viewer 改用 evaluator）；**新增本文件 CHANGELOG.md** 建立「每次修改都记录」机制 | `README.md`、`docs/USER_GUIDE.md`、`docs/ACCEPTANCE.md`、`docs/API.md`、`CHANGELOG.md` |
| 13 | 2026-09-30 11:20 | `22ef380` | **角色精简**：系统角色由 5 个减为 2 个（保留系统管理员、评估人员；删除数据管理员、审核人员、领导/查看人员），默认账号由 5 个减为 2 个；同步删除数据库中的 3 个角色与 3 个账号。项目成员角色（项目内分工）未改动 | `AuthService.java`、`db/data.sql`、`db/02_seed.sql`、`Login.vue`、`RoleList.vue` |
| 12 | 2026-09-30 11:20 | `47dce43` | **登录页改版**：移除左侧品牌区的四条系统介绍卡片（B/S 架构、三种评估算法、三类结果与八类图形、内网离线部署）及其样式；左上角标题与简介保持不变 | `Login.vue` |
| 11 | 2026-09-30 11:03 | `5ca07b4` | **修复白屏（根因）**：`/api/auth/me` 未返回 `menus`，导致非管理员账号刷新页面后菜单集合为空、路由守卫把驾驶舱重定向到自身形成无限重定向 → 整页白屏。接口补齐 `menus`，并给守卫加了「禁止重定向到自身」的保护 | `AuthController.java`、`AuthService.java`、`router/index.ts` |
| 10 | 2026-09-29 21:20 | `4e41d8b` | **白屏兜底**：静态资源缺失由 500 改为 404（且不再打 ERROR 堆栈）；路由懒加载失败自动刷新一次；入口资源失效超时后显示「页面加载失败 + 重新加载」而非白屏 | `GlobalExceptionHandler.java`、`index.html`、`router/index.ts` |
| 9 | 2026-09-29 21:09 | `77d54b3` | **修复项目可见性**：新建项目时「负责人」留空会导致创建者自己看不到该项目。后端在创建时兜底负责人、并让创建者与负责人自动成为项目成员；前端新建时预填负责人、列表为空时按数据范围给出原因说明 | `ProjectService.java`、`ProjectList.vue`、`EvaluationWorkbench.vue` |
| 8 | 2026-09-29 20:38 | `50cf18a` | 同步代码详解分册：反映安全整改、单 jar 部署与新脚本；索引计数校正为 178 个文件 / 33,703 行 | `docs/CODE_GUIDE/*`、`db/00_create_database.sql` |
| 7 | 2026-09-29 20:32 | `3dd7551` | 修复脚本 `:probe` 探活缺陷（`%ERRORLEVEL%` 写在括号块内被解析期展开，恒返回失败）；4 个 `.bat` 统一为 GBK + CRLF；补 `.jwt-secret` 忽略规则 | `scripts/*.bat`、`.gitignore`、`JwtService.java`、`README.md`、`docs/ACCEPTANCE.md` |
| 6 | 2026-09-29 20:25 | `082e5d6` | **合规整改**：生产档日志不再打印初始口令（启动横幅与建号日志按档位区分，prod 档只输出账号名） | `StartupBanner.java`、`AuthService.java`、`application-dev.yml` |
| 5 | 2026-09-29 20:23 | `e7116b3` | **支持单 jar 离线部署**：构建时把 `frontend/dist` 打进 jar，prod 档单进程即可出页面；构建脚本调整为先前端后后端；新增离线启动脚本；脚本去掉写死的本机绝对路径 | `backend/pom.xml`、`scripts/*.bat`、`docs/DEPLOY.md` |
| 4 | 2026-09-29 20:18 | `9c93bf9` | **安全整改（重要）**：移除仓库内置的 JWT 默认密钥。该密钥随源码交付、等同公开值，实测可据此伪造系统管理员令牌越权读写全部接口。改为「环境变量 → 本地密钥文件（首次启动自动生成并持久化）→ 进程内随机」三级解析 | `JwtSecretProvider.java`（新增）、`JwtService.java`、`application.yml`、`EesProperties.java`、`scripts/*.bat` |
| 3 | 2026-09-29 20:11 | `f284066` | 排除 Spring Security 自动配置，消除启动日志里随机生成的明文密码 | `EesApplication.java` |
| 2 | 2026-09-29 19:55 | `a48c1ff` | 修正 README 中的绝对路径（项目已迁移至 `D:\Git仓库\efficiency-evaluation-system`） | `README.md` |
| 1 | 2026-09-29 19:49 | `0f85e59` | **初始提交**：效能评估系统（B/S 架构）完整源码、数据库脚本、样例数据、开发与运行脚本、全套文档（含 14 册代码详解） | 全部源文件（199 个） |

---

## 四、待办与已知事项

- **角色精简带来的权限分布变化（已在 `d0a9773` 处理完毕）**：
  角色精简后 `EVALUATOR` 权限数组里没有 `result:review` / `result:publish` / `result:compare`，
  结果复核、发布、归档与横向对比一度只有系统管理员可执行。现已把这三个权限码补给评估人员
  （权限由 25 项增至 **27 项**），使其能独立走完「执行评估 → 复核 → 发布」闭环；
  同时移除了评估人员那条用不上的 `data:source:list` —— `menusOf()` 不给它 `dataSource` 菜单，
  界面上本就进不到「数据源管理」，而该页会展示外部（甲方业务库）的连接串，无需对评估人员开放。
  评估人员的数据接入能力仍在「数据接入 → 数据批次 / 数据快照」两个页面。
- `db/01_schema.sql` 与 `backend/src/main/resources/db/schema.sql` 是同一份建表脚本的两份副本；
  `db/02_seed.sql` 与 `backend/src/main/resources/db/data.sql` 同理。**改动其中一份时必须同步另一份**，
  否则会出现「手工执行生效、程序启动不生效」或反之的问题（角色精简时就踩过这一点）。
