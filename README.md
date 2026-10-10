# 效能评估系统

> 基于 B/S 架构的效能评估系统。前端 Vue 3 + TypeScript + Element Plus + ECharts，
> 后端 Java 21 + Spring Boot 3.5 + MySQL 8，支持 AHP、模糊综合评判、加权平均法三种算法，
> 提供文本 / 表格 / 图形三类结果展示与八类图形，面向私有化与内网离线部署。

---

## 一、目录结构

```
efficiency-evaluation-system/
├── backend/                    后端服务（Spring Boot 3.5 + JPA + MySQL）
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/ees/
│       │   ├── algorithm/      算法引擎（AHP / 模糊综合评判 / 加权平均 / 组合流程 / C++ 适配位）
│       │   ├── common/         统一响应、错误码、异常处理、JSON 与编号工具
│       │   ├── config/         安全、异步、审计、算法装配、存储、Web 与数据初始化
│       │   ├── controller/     REST 接口（认证、项目、指标、数据、任务、结果、报告、系统）
│       │   ├── domain/         24 张表的 JPA 实体
│       │   ├── dto/            请求/响应对象
│       │   ├── repository/     Spring Data JPA 仓储
│       │   ├── security/       JWT 认证与权限上下文
│       │   ├── service/        业务服务
│       │   └── util/           表格解析、Excel 模板、加密
│       └── resources/
│           ├── application*.yml
│           └── db/schema.sql, db/data.sql   （启动时自动执行，幂等）
├── frontend/                   前端工程（Vue 3 + Vite + Element Plus + ECharts）
│   └── src/
│       ├── api/                接口封装与 Axios 拦截器
│       ├── components/         EesChart / ChartCard / StatCard / LevelTag / PageHeader
│       ├── layouts/            主框架（深色侧栏 + 浅色工作台）
│       ├── router/             路由与菜单级授权
│       ├── stores/             Pinia 状态（用户、项目上下文）
│       ├── styles/             设计令牌与通用样式
│       ├── utils/              ECharts option 构造、格式化与字典
│       └── views/              18 个业务页面
├── db/                         MySQL 初始化脚本（供 Navicat 手工执行）
├── docs/                       API.md / DEPLOY.md / USER_GUIDE.md / ACCEPTANCE.md
├── samples/                    指标体系、试验数据、算法参数样例
└── scripts/                    一键启动 / 停止脚本
```

---

## 二、技术基线

| 层次 | 选型 | 版本 |
|---|---|---|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + Apache ECharts | 3.5 / 5.7 / 6.4 / 2.9 / 5.6 |
| 后端 | Java LTS + Spring Boot + Spring Data JPA + Spring Security | 21 / 3.5.16 |
| 系统数据库 | MySQL Community（官方 MSI 安装） | 8.0.45 |
| 认证 | JWT（HS256）+ BCrypt 密码哈希 + 方法级权限 | jjwt 0.12.6 |
| 报表 | Apache POI（Excel/Word）+ OpenPDF（PDF，内置 CJK 字体） | 5.4.1 / 2.2.5 |
| 表格解析 | Apache POI + commons-csv（自动识别 UTF-8 / GBK） | 1.12.0 |
| 算法组件 | 内置 Java 实现 + C++ 独立算法服务适配位 | java-*-1.0.0 |

> 全部依赖在离线包内闭环，运行时不访问任何互联网服务；前端资源、字体、图标与图表库全部本地打包。

---

## 三、环境要求

| 组件 | 要求 | 本项目已安装位置 |
|---|---|---|
| JDK | 21（LTS） | `D:\DevEnv\jdk-21`（Microsoft Build of OpenJDK 21.0.12.1） |
| Maven | 3.9+ | `D:\DevEnv\maven`（3.9.16） |
| MySQL | 8.0+ | **官方 MSI 安装版 MySQL Server 8.0.45**：程序目录 `C:\Program Files\MySQL\MySQL Server 8.0`，配置 `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini`，数据目录 `D:\MySQLData\Data`，Windows 服务名 **`MySQL80`**（自动启动） |
| 图形化数据库客户端 | 任意 | MySQL Workbench 8.0.45（官方，`C:\Program Files\MySQL\MySQL Workbench 8.0`）；另装 Navicat Premium Lite 17 `D:\DevEnv\Navicat` |
| 命令行数据库客户端 | 任意 | `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`（桌面「MySQL 客户端」快捷方式，双击即连库） |
| Node.js | 18+（推荐 20/22/24） | `C:\Program Files\nodejs`（24.19.0） |
| IDE | 任意 | IntelliJ IDEA 2025.3 `D:\DevEnv\IDEA` |

环境变量（用户级，已配置）：`JAVA_HOME=D:\DevEnv\jdk-21`、`MAVEN_HOME=D:\DevEnv\maven`，
PATH 已包含 JDK、Maven、MySQL、IDEA 的 bin。

> 数据库账号与存储目录**不通过环境变量设置**，一律以 `backend/src/main/resources/application-*.yml`
> 为唯一出处，避免出现「改了配置文件却不生效」的问题；企业环境如需集中下发口令，
> 再按 `docs/DEPLOY.md` 3.2 节用 `EES_DB_*` 注入。

> MySQL 由官方 MSI 安装，注册为 Windows 服务并设为自动启动，**开机即运行、无控制台窗口**，不需要手工启动。

---

## 四、快速启动

### 1. MySQL（服务，自动运行）

数据库已注册为 Windows 服务 `MySQL80`（启动类型：自动），开机自动运行，无需任何操作。
如需检查或手工控制：

```bat
sc query MySQL80            :: 查看状态（RUNNING 即正常）
net start MySQL80           :: 手工启动（需管理员）
net stop  MySQL80           :: 手工停止（需管理员）
```

本项目自带的 `scripts\start-all.bat` 与 `scripts\stop-all.bat` 会自动探测本机的 MySQL 服务名
（依次尝试 `MySQL80`/`MySQL84`/`MySQL57`/`MySQL56`/`MySQL`）并启停，因此换到企业环境时
不需要修改脚本里的任何路径；数据库若由甲方 DBA 维护，脚本只提示、不会强行操作。

数据与日志位置：

| 内容 | 路径 |
|---|---|
| 数据目录 | `D:\MySQLData\Data` |
| 错误日志 | `D:\MySQLData\mysql-error.log` |
| 慢查询日志 | `D:\MySQLData\mysql-slow.log` |
| 服务配置文件 | `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` |

数据库连接信息：

| 用途 | 账号 | 密码 | 数据库 |
|---|---|---|---|
| 应用账号 | `ees` | `Ees@2026` | `efficiency_evaluation` |
| 只读账号（演示数据库接入） | `ees_reader` | `EesRead@2026` | `efficiency_evaluation` |
| 管理员 | `root` | `Root@2026` | — |

### 2. 启动后端

```bat
cd /d D:\Git仓库\efficiency-evaluation-system\backend
mvn spring-boot:run
```

首次启动会自动执行 `db/schema.sql`（建表，幂等）与 `db/data.sql`（角色、系统参数、质量规则、
算法基准样例），并创建两个默认账号。服务地址 <http://127.0.0.1:8080>，
健康检查 <http://127.0.0.1:8080/api/health>。

### 3. 启动前端

```bat
cd /d D:\Git仓库\efficiency-evaluation-system\frontend
npm install      :: 首次执行
npm run dev
```

浏览器访问 <http://127.0.0.1:5173>。

### 4. 一键脚本

```bat
scripts\start-all.bat      检查 MySQL 服务 + 启动后端 + 前端
scripts\stop-all.bat       停止全部
scripts\build-all.bat      构建后端 jar 与前端静态资源
```

### 5. 运行期数据目录

上传原始文件、数据快照、导出报告与应用日志统一落在 `ees.storage.root`（默认 `./data`，
即相对进程工作目录）。以 `mvn spring-boot:run` 从 `backend` 目录启动时为 `backend/data`：

```
backend/data/
├── upload/     原始导入文件留档（按日期分目录）
├── snapshot/   数据快照留存
├── report/     导出的 Excel / Word / PDF（按日期分目录）
├── temp/       临时文件
└── logs/       evaluation-backend.log（50 MB 滚动，保留 30 份）
```

可用环境变量 `EES_STORAGE_ROOT` 指定到独立数据盘；备份时应把该目录与数据库一并纳入。

---

## 五、默认账号

| 用户名 | 密码 | 角色 | 主要权限 |
|---|---|---|---|
| `admin` | `admin123` | 系统管理员 | 全部功能 |
| `evaluator` | `eval123` | 评估人员 | 项目、指标体系、数据接入（批次、快照）、任务配置、算法执行、结果复核 / 发布 / 横向对比、结果分析（结果分析页导出 Excel / Word / PDF 仍可用；**不含报告导出**，报告导出仅系统管理员） |

> 首次登录后请立即修改密码；连续登录失败 5 次将锁定 15 分钟。

---

## 六、五分钟体验路径

1. 用 `admin / admin123` 登录。
2. **指标体系 → 导入指标体系**，上传 `samples\指标体系样例.csv`，选择项目后提交；
   查看校验结果（指标数、叶子数、层级数、告警），然后 **发布** 该版本。
3. **数据接入 → 导入数据文件**，上传 `samples\试验数据样例.csv`，选择项目与刚发布的指标体系版本；
   查看质量报告与错误明细。
4. **数据快照 → 生成快照**，选择该批次与指标体系版本，生成快照。
5. **评估工作台**，依次选择项目 → 指标体系 → 数据快照 → 算法（加权平均法）→ 参数 → 提交；
   在步骤五观察任务状态与进度。
6. **评估结果 → 结果分析**，切换「文本 / 表格 / 图形」三个标签，
   查看八类图形、追溯信息，并导出 Excel / Word / PDF。
7. **算法基准**，点击「全部运行」，确认四个标准样例全部通过（PASS）。

---

## 七、需求符合性对照

| 原始要求 | 落地实现 | 验证入口 |
|---|---|---|
| 结果展示 ≥ 3 种（文本、表格、图形） | 结果分析页三个标签页，与导出文件一致 | 结果分析 → 文本 / 表格 / 图形 |
| 图形 ≥ 6 种 | 实际交付 **8 类**：柱状、折线、饼/环形、雷达、散点、热力、箱线、排名条形 | 结果分析 → 图形标签 |
| 算法 ≥ 3 种（AHP、加权平均、模糊综合评判） | **4 种**：AHP、加权平均法、模糊综合评判、AHP 组合流程 | 评估工作台；算法基准页 |
| 数据接入 ≥ 2 种（数据库、文件导入） | 只读 JDBC 接入（连接测试 / 表清单 / 字段预览 / 受限 SELECT 抽取）+ XLSX/CSV/JSON/ZIP 文件导入 | 数据源管理 / 数据接入 |
| B/S 架构、主流浏览器访问 | 前后端分离，Vue 3 单页应用 + REST API；前端 `base: './'` 可在任意子路径部署 | 浏览器访问任意页面 |
| 私有化 / 内网离线部署 | 依赖、字体、图标与图表库全部本地打包，无 CDN、无外网调用 | 断网后完整走通评估闭环 |

---

## 八、文档索引

| 文档 | 内容 |
|---|---|
| `docs/API.md` | 全部接口契约、算法参数约定、八类图形数据结构、前端路由规划 |
| `docs/DEPLOY.md` | 环境安装、数据库初始化、离线部署、备份恢复、故障处理 |
| `docs/USER_GUIDE.md` | 按角色划分的操作手册 |
| `docs/ACCEPTANCE.md` | 可执行的验收检查表与验证命令 |
| **`docs/CODE_GUIDE/`** | **代码详解（14 个分册 + 总索引）**：覆盖仓库 176 个源文件、约 32,500 行代码，逐文件逐语句说明「这行代码干什么、为什么这么写」。入口见 `docs/CODE_GUIDE/README.md` |
| `samples/` | 指标体系、试验数据、AHP 判断矩阵、算法参数标准样例 |

---

## 九、已确认技术决策

| 决策项 | 当前基线 | 调整条件 |
|---|---|---|
| 算法集成 | 内置 Java 实现，同时保留 `ees.algorithm.engine=CPP_ENGINE` 的 C++ 独立算法服务适配位（统一 REST 协议，遵循附录 B 输入输出结构） | 甲方提供可运行 C++ 组件并通过基准测试后，仅需切换配置项 |
| 系统数据库 | MySQL 8.0（`db/schema.sql` 使用标准 SQL，仅少量 MySQL 方言） | 甲方指定其他数据库并完成兼容性评估 |
| 甲方数据库 | 外部只读数据源，通过可插拔 JDBC 连接器接入，内置 MySQL / PostgreSQL / Oracle / SQL Server / 达梦 / 人大金仓驱动位 | — |
| UI 方向 | 深色侧栏 + 可视化卡片的驾驶舱，与浅色工作台混合方案 | 品牌规范确认后调整色彩与组件 |
| 首期范围 | 核心评估闭环、八类图形、权限审计与报告 | 敏感性分析与情景推演放二期 |
