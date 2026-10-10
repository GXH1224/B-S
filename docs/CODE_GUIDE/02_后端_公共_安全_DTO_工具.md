# 02 后端 · 公共基础、安全认证、DTO 与工具类

> 覆盖范围：`backend/src/main/java/com/ees/common/`（6 个文件）、`backend/src/main/java/com/ees/security/`（5 个文件）、
> `backend/src/main/java/com/ees/dto/`（4 个文件）、`backend/src/main/java/com/ees/util/`（3 个文件），共 **18 个文件、1786 行**
> 对应分册：总索引第 **02** 册
> 阅读前提：先读 01 分册了解工程总体结构与启动流程。本册讲的是"横切层"——它被 `controller` / `service` / `algorithm` 三层共同依赖，
> 但自身基本不反向依赖业务层（唯一例外是 `ExcelTemplateWriter` 引用 `IndicatorService` / `DataBatchService` 的模板列常量，见 2.18）。
> 涉及算法引擎、数据批次等业务逻辑时，本册只说明调用关系，不展开其实现。
> **安全整改提示**：交付前做过一次针对 JWT 内置默认密钥的整改，新增 `security/JwtSecretProvider`（见 2.8），
> 并连带修改了 `JwtService` 的构造签名（见 2.7）。凡涉及 `ees.security.jwt-secret` 的旧描述（含 01 分册与 `DEPLOY.md`）都应以本册 2.8 的实测结论为准。

## 目录
- [1. 文件清单](#1-文件清单)
- [2. 逐文件详解](#2-逐文件详解)
- [3. 本册小结](#3-本册小结)

## 1. 文件清单

| 序号 | 文件路径（相对仓库根） | 行数 | 一句话作用 |
|---|---|---|---|
| 1 | `backend/src/main/java/com/ees/common/ApiResponse.java` | 103 | 统一响应体，前端以 `code == 0` 判定成功 |
| 2 | `backend/src/main/java/com/ees/common/PageResult.java` | 86 | 统一分页结果，含 `total/pages/hasNext` 计算 |
| 3 | `backend/src/main/java/com/ees/common/BizException.java` | 54 | 可预期业务失败的统一异常类型，携带错误码与可选 payload |
| 4 | `backend/src/main/java/com/ees/common/ErrorCode.java` | 70 | 统一错误码枚举（1xxx~6xxx 分段），对应方案 8.3 错误类别 |
| 5 | `backend/src/main/java/com/ees/common/GlobalExceptionHandler.java` | 145 | `@RestControllerAdvice`，把各类异常映射为统一响应与 HTTP 状态；含静态资源缺失 404 分支 |
| 6 | `backend/src/main/java/com/ees/common/JsonUtils.java` | 123 | 实体 TEXT 字段与 JSON 之间的序列化/反序列化工具 |
| 7 | `backend/src/main/java/com/ees/security/JwtService.java` | 123 | JWT 生成与解析（HS256），密钥由 `JwtSecretProvider` 提供，含空密钥守卫与密钥长度补齐逻辑 |
| 8 | `backend/src/main/java/com/ees/security/JwtSecretProvider.java` | 192 | **安全整改新增**：JWT 签名密钥的三级解析（环境变量 → 本地密钥文件 → 进程内随机）与持久化 |
| 9 | `backend/src/main/java/com/ees/security/JwtAuthenticationFilter.java` | 61 | 每请求一次的认证过滤器，从三处取令牌并填充 SecurityContext |
| 10 | `backend/src/main/java/com/ees/security/LoginUser.java` | 125 | 登录用户上下文（`UserDetails`），产出角色与细粒度权限 |
| 11 | `backend/src/main/java/com/ees/security/SecurityUtils.java` | 47 | 静态获取当前登录用户、校验权限的工具 |
| 12 | `backend/src/main/java/com/ees/dto/LoginRequest.java` | 23 | 登录请求入参（用户名/密码 + 预留验证码字段） |
| 13 | `backend/src/main/java/com/ees/dto/LoginResponse.java` | 32 | 登录响应（令牌 + 到期秒数 + 用户信息内部类） |
| 14 | `backend/src/main/java/com/ees/dto/UserSaveRequest.java` | 41 | 用户新增/修改入参，同一对象服务两种语义 |
| 15 | `backend/src/main/java/com/ees/dto/ChangePasswordRequest.java` | 18 | 修改密码入参（原密码 + 新密码） |
| 16 | `backend/src/main/java/com/ees/util/TabularReader.java` | 276 | 表格文件读取（XLSX / XLS / CSV），含表头探测、编码回退、数值与日期兜底 |
| 17 | `backend/src/main/java/com/ees/util/ExcelTemplateWriter.java` | 183 | 生成指标体系模板、标准试验数据模板（附录 A）与错误明细 xlsx |
| 18 | `backend/src/main/java/com/ees/util/CryptoUtils.java` | 84 | 数据源密码的 AES/GCM 加解密（方案 9.1），AES 密钥由最终 JWT 密钥派生 |

> 说明：`com.ees.common.CodeGenerator` 在当前代码树中**不存在**，编号生成职责已由 `com.ees.service.CodeService` 承担
> （被 `TaskService`、`DataBatchService`、`DataSourceService`、`SnapshotService`、`ReportService`、`ProjectService` 注入）。
> `README.md` 第 18 行仍写着 `common/` 含"编号工具"，属描述滞后，本册按实际代码编写，不臆造 `CodeGenerator`。

## 2. 逐文件详解

### 2.1 `backend/src/main/java/com/ees/common/ApiResponse.java`

**作用**：定义全系统唯一的响应外壳 `{code, message, data, timestamp, traceId}`，并提供一组静态工厂方法，让所有 controller 用一行代码产出响应。
**被谁使用**：全部 15 个 controller、`GlobalExceptionHandler`、`config/SecurityConfig`（认证/鉴权失败的 JSON 出口）直接构造或返回它。
**关键设计点**：
1. 以 `code == 0` 作为成功判据（第 6 行注释 + `docs/API.md` 第 10 行），HTTP 状态码在本系统中不承载业务语义，业务成败只看 `code`。
2. 所有工厂方法都汇聚到唯一的三参构造（第 19-23 行），保证不会漏赋值字段；`fail(ErrorCode)` 系列直接复用枚举自带的中文文案，避免同一个错误在不同层写出不同提示。
3. `withData` 是"失败也带结构化数据"的关键：`GlobalExceptionHandler` 用它把字段级错误 Map 挂到失败响应上，前端可按字段高亮（对应方案 8.3 的"输入错误返回字段级错误"）。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.common;` | 归入公共基础包，被 controller / security / service 全层引用 |
| 3 | `import java.io.Serializable;` | 实现序列化接口，为将来放入缓存或跨进程传输留出条件（当前为进程内直接返回） |
| 5-7 | `/** 统一响应结构 ... */` | 类注释即前后端契约说明：前端所有接口按 `code == 0` 判定成功 |
| 8 | `public class ApiResponse<T> implements Serializable {` | 泛型 `T` 承载任意业务数据，controller 可直接声明 `ApiResponse<PageResult<Project>>` 而无需强转 |
| 10-12 | `code` / `message` / `data` | 三要素：状态码、可直接展示给用户的中文提示、业务载荷 |
| 13 | `timestamp = System.currentTimeMillis()` | 字段初始化即取当前毫秒时间，响应生成时间自动带上，调用方无需设置 |
| 14 | `traceId` | 预留的链路追踪标识（见易错点 1） |
| 16-17 | `public ApiResponse() { }` | 无参构造，Jackson 反序列化与框架反射需要 |
| 19-23 | `ApiResponse(int code, String message, T data)` | 唯一赋值入口，`ok*` / `fail*` 全部走它 |
| 25-27 | `ok()` | 无数据的成功（如删除、登出），`data` 为 null |
| 29-31 | `ok(T data)` | 最常用：单对象、列表、分页结果 |
| 33-35 | `ok(T data, String message)` | 需要给用户成功提示时使用，例如"校验完成" |
| 37-39 | `fail(ErrorCode errorCode)` | 用枚举自带文案，保证错误码与提示一一对应 |
| 41-43 | `fail(ErrorCode, String)` | 保留错误码语义、替换为更具体的提示，如 `ACCOUNT_LOCKED` + "账号已锁定至 2026-09-22 10:30" |
| 45-47 | `fail(int code, String message)` | 无对应枚举时的兜底入口，用于算法引擎回传的原始错误码字符串场景 |
| 49-51 | `fail(String message)` | 未分类业务失败统一落到 `BUSINESS_ERROR(1000)`，防止调用方随手编造错误码 |
| 53-57 | `withData(T data)` | 链式补充 data 并返回自身，用于"失败 + 字段级明细"响应 |
| 59-62 | `withTraceId(String traceId)` | 链式补充 traceId（当前无调用点） |
| 64-102 | 5 组 `getXxx` / `setXxx` | 序列化与反序列化依赖的访问器；`getTimestamp` 让前端能判断响应新鲜度 |

**易错点/注意**：
1. `traceId` 字段、`withTraceId`、`getTraceId`/`setTraceId` 在整个后端**没有填充点**（已用全仓库检索确认，仅本文件出现），属预留能力；答辩时不要把它说成已实现的链路追踪。
2. `timestamp` 是毫秒时间戳而非格式化字符串，前端需自行转换为本地时间展示。

### 2.2 `backend/src/main/java/com/ees/common/PageResult.java`

**作用**：定义统一分页结构 `{records, total, page, size, pages, hasNext}`，并在构造时算好总页数与"是否有下一页"。
**被谁使用**：全部 10 个分页 controller 的返回类型，以及 9 个 service（`AuditService`、`DataBatchService`、`DataSourceService`、`IndicatorService`、`ProjectService`、`ReportService`、`ResultService`、`SnapshotService`、`TaskService`）的返回类型。
**关键设计点**：
1. `pages` 与 `hasNext` 由后端一次算好，前端分页组件不再自己算边界，避免 `total % size` 这类边界写错导致最后一页漏数据（对应 `docs/API.md` 第 11 行的分页契约）。
2. `size <= 0` 时强制 `pages = 0`（第 27 行），防止前端传入 `size=0` 造成除零。
3. `records` 在构造与字段初始化两处都做了空值兜底（第 12、23 行），保证前端 `records.map()` 永不因 null 崩溃。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.common;` | 公共基础包 |
| 3-5 | `import java.io.Serializable; import java.util.ArrayList; import java.util.List;` | 序列化接口 + 两个集合类型；因为第 12 行默认值用了 `ArrayList`，不能只导 `List` |
| 7-9 | `/** 分页结果。 */` | 类注释 |
| 10 | `public class PageResult<T> implements Serializable {` | 泛型记录类型 |
| 12 | `private List<T> records = new ArrayList<>();` | 初始为空列表而非 null，前端表格直接遍历 |
| 13 | `private long total;` | 总数用 `long`：原始数据记录（`raw_data_record`）量级可能超过 `int` |
| 14 | `private int page = 1;` | 默认第 1 页，与 API 契约一致 |
| 15 | `private int size = 20;` | 默认每页 20 条，与 API 契约一致 |
| 16 | `private int pages;` | 总页数，由构造计算 |
| 17 | `private boolean hasNext;` | 是否存在下一页，由构造计算 |
| 19-20 | `public PageResult() { }` | 无参构造，供 Jackson 反序列化 |
| 22-29 | `PageResult(List<T> records, long total, int page, int size)` | 关键构造：第 23 行 null 转空列表；第 27 行 `Math.ceil((double) total / size)` 向上取整算总页数并防除零；第 28 行 `hasNext = page < pages` 由数据推导，不由前端猜测 |
| 31-33 | `public static <T> PageResult<T> of(...)` | 语义化工厂方法，service 返回处写起来更短 |
| 35-37 | `public static <T> PageResult<T> empty(int page, int size)` | 空结果但保留页码与页长，前端表格不会因空数据跳回第 1 页 |
| 39-85 | 6 组 `getXxx` / `setXxx` | 字段访问器；`isHasNext()` 的命名让 JSON 序列化为 `hasNext`，与前端类型定义一致 |

**易错点/注意**：无数据时 `total=0 → pages=0`，若前端直接用 `pages` 渲染页码会显示 0 页，应回退为 1（前端分页组件通常自带该兜底）。

### 2.3 `backend/src/main/java/com/ees/common/BizException.java`

**作用**：定义"可预期业务失败"的统一异常，携带 `ErrorCode`（决定提示与 HTTP 状态）与可选 `payload`（结构化明细）。
**被谁使用**：18 个类抛出——`algorithm/AlgorithmRegistry`、`common/JsonUtils`、`security/SecurityUtils`、`util/CryptoUtils`、`util/TabularReader`，以及 `AuthService`、`BaselineService`、`ConfigService`、`DataBatchService`、`DataSourceService`、`IndicatorService`、`ProjectService`、`ReportService`、`ResultService`、`SnapshotService`、`TaskService` 等业务服务。
**关键设计点**：
1. 继承 `RuntimeException`（第 6 行）而非受检异常：service 方法签名不必声明 `throws`，业务校验代码可以就地在任意深度抛出，异常最终由 `GlobalExceptionHandler` 一处转换（第 5 行注释规定的约定）。
2. `errorCode` 声明为 `final`（第 8 行）：异常一旦构造，错误码不可被后续代码改写，保证日志、响应体、任务表落库三者一致。
3. `payload` 声明为 `transient`（第 9 行）：异常可能随任务失败信息被持久化/序列化，而 payload 往往是不可序列化的临时对象，标 `transient` 可避免序列化失败。
4. 三个静态工厂（`of`/`param`/`notFound`）把最常用的三种语义固定下来，减少手写错误码时选错段位。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.common;` | 公共基础包 |
| 3-5 | `/** 业务异常 ... */` | 类注释规定全局约定：所有可预期的失败都抛本异常，由 `GlobalExceptionHandler` 统一转换 |
| 6 | `public class BizException extends RuntimeException {` | 非受检异常，不污染调用链签名 |
| 8 | `private final ErrorCode errorCode;` | 错误码不可变，避免 handler 与日志读到不同值 |
| 9 | `private final transient Object payload;` | 结构化明细，不参与序列化 |
| 11-15 | `BizException(ErrorCode errorCode)` | 只给错误码，message 取枚举默认文案（如 `ACCOUNT_DISABLED` → "账号已停用"） |
| 17-21 | `BizException(ErrorCode, String message)` | 最常用重载：保留错误码语义 + 补充具体上下文 |
| 23-27 | `BizException(ErrorCode, String, Object payload)` | 附带结构化数据，供 `GlobalExceptionHandler` 第 38-40 行回填到 `data` |
| 29-33 | `BizException(ErrorCode, String, Throwable cause)` | 包装底层异常，`rootMessage` 与日志可追溯到根因 |
| 35-37 | `public ErrorCode getErrorCode()` | 供 handler 取错误码做 HTTP 状态映射 |
| 39-41 | `public Object getPayload()` | 供 handler 取结构化明细 |
| 43-45 | `of(String)` → `BUSINESS_ERROR(1000)` | 泛业务失败 |
| 47-49 | `param(String)` → `PARAM_INVALID(1001)` | 入参非法 |
| 51-53 | `notFound(String)` → `NOT_FOUND(1004)` | 资源不存在，`AuthService.saveUser` 用它返回"用户不存在" |

**易错点/注意**：`super(message)` 只设置 `getMessage()`，`getErrorCode()` 需另行读取；若直接 `catch (Exception)` 后重新抛出 `new BizException(...)` 而不传 `cause`，根因链会断掉（`rootMessage` 也就取不到底层数据库错误）。

### 2.4 `backend/src/main/java/com/ees/common/ErrorCode.java`

**作用**：全系统唯一的错误码字典，用 `code + 默认中文文案` 的枚举形式固定每一类失败的语义。
**被谁使用**：30 个类引用（含 5 个算法引擎、11 个 service、`SecurityConfig`、`controller/TaskController`、`domain/EvaluationTask`）。枚举名还被算法引擎写成字符串落库，例如 `AhpEngine` 返回 `ErrorCode.AHP_CR_NOT_PASSED.name()`，`TaskService` 第 356 行写入 `ErrorCode.SYSTEM_ERROR.name()`。
**关键设计点**：
1. 分段编号：段位表达"责任归属与系统行为"，而不是随意编数字。方案 8.3 要求把失败按类别区分处理，1xxx~6xxx 就是这套分类的落地编码。
2. 3xxx 段与 4xxx 段的行为差异是硬约束：3xxx 属"输入错误"，要求在提交阶段阻止并返回**字段级**错误；4xxx 属"算法错误"，要求**任务失败并保存算法原始错误**。前者由 `GlobalExceptionHandler.handleValidation` 与 `DataBatchService.validate` 实现，后者由算法引擎返回 `AlgorithmResult.failure(错误码名, 原始信息)`、由任务表留存实现。
3. 错误码与 HTTP 状态的映射只覆盖"协议语义明确"的少数几个（见 2.5），其余业务失败统一 HTTP 200，前端只看 `code`。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.common;` | 公共基础包 |
| 3-5 | `/** 统一错误码。对应方案 8.3 错误类别。 */` | 明确与方案 8.3 的对应关系，这是本文件存在的理由 |
| 6 | `public enum ErrorCode {` | 用枚举而非常量类：可用 `switch` 做状态映射（2.5 第 41-46 行），且编译期即可检查拼写 |
| 8-14 | `// 通用 1xxx` 段：`SUCCESS(0)`、`BUSINESS_ERROR(1000)`、`PARAM_INVALID(1001)`、`NOT_FOUND(1004)`、`DATA_CONFLICT(1009)`、`SYSTEM_ERROR(1500)` | 与 HTTP 语义天然对齐的一段：0 成功、1000 未分类业务失败、1001 参数非法、1004 资源不存在、1009 唯一约束/状态冲突、1500 服务端内部错误。`SUCCESS(0)` 单独放在枚举首位，是为了让"错误码表"同时充当"状态码表"，`ApiResponse.ok()` 与之呼应 |
| 16-22 | `// 认证授权 2xxx` 段：`UNAUTHORIZED(2001)`、`FORBIDDEN(2003)`、`LOGIN_FAILED(2004)`、`ACCOUNT_DISABLED(2005)`、`ACCOUNT_LOCKED(2006)`、`TOKEN_INVALID(2007)` | 登录/令牌相关的全部失败形态。`AuthService.login` 分别抛 80 行的 `LOGIN_FAILED`、85 行的 `ACCOUNT_DISABLED`、90 行的 `ACCOUNT_LOCKED`；不区分"用户名不存在"和"密码错误"（都用 2004）是刻意的：避免账号枚举 |
| 24-33 | `// 输入数据 3xxx` 段：`INPUT_VALIDATE_FAILED(3001)`、`FILE_TYPE_NOT_ALLOWED(3002)`、`FILE_TOO_LARGE(3003)`、`FILE_PARSE_FAILED(3004)`、`INDICATOR_NOT_COVERED(3005)`、`DATA_FIELD_MISSING(3006)`、`DATA_FORMAT_INVALID(3007)`、`DATA_VALUE_OUT_OF_RANGE(3008)`、`DIRECTION_UNKNOWN(3009)` | 方案 8.3 的"输入错误"整段。注释明确要求"阻止提交，返回字段级错误"：3006/3007/3008 分别对应必填缺失、数值格式非法、指标值越界，`DataBatchService.validate` 逐行生成 `{行号, 字段, 原因, 严重级}` 明细；3009 对应方案要求"指标正负方向未确认"时不允许进入标准化计算 |
| 35-41 | `// 算法 4xxx` 段：`ALGORITHM_ERROR(4001)`、`AHP_MATRIX_INVALID(4002)`、`AHP_CR_NOT_PASSED(4003)`、`ALGORITHM_PARAM_INVALID(4004)`、`ALGORITHM_NOT_SUPPORTED(4005)`、`MISSING_VALUE_BLOCKED(4006)` | 方案 8.3 的"算法错误"整段，注释要求"任务失败并保存算法原始错误"。4003 是方案 6.2 一致性检验要求的直接编码——`AhpEngine` 第 120 行在 CR 超限时返回该码并附带矩阵与 CR 值；4006 对应"缺失值不接受静默处理"，`TaskService` 第 178 行据此拒绝启动任务 |
| 43-47 | `// 运行错误 5xxx` 段：`ALGORITHM_ENGINE_UNAVAILABLE(5001)`、`TASK_TIMEOUT(5002)`、`TASK_CANCELED(5003)`、`TASK_STATUS_ILLEGAL(5004)` | 方案 8.3 的"运行错误"：不是数据错也不是算法错，而是环境与编排状态问题。5001 由 `algorithm/cpp/CppEngineAdapter` 在外部算法服务不通时返回；5004 由 `TaskService` 第 567、580 行在状态机不允许时抛出（如对已完成任务再次执行） |
| 49-53 | `// 数据源 6xxx` 段：`DATASOURCE_CONNECT_FAILED(6001)`、`DATASOURCE_SQL_FORBIDDEN(6002)`、`DATASOURCE_QUERY_FAILED(6003)`、`DATASOURCE_NOT_FOUND(6004)` | 数据库抽取这条链路的专属段。6002 是安全要求而非普通错误：`DataSourceService` 第 345-360 行用它拦截多语句、非 SELECT、含注释的 SQL，实现只读数据源白名单 |
| 55-56 | `private final int code;` / `private final String message;` | 两个不可变字段 |
| 58-61 | `ErrorCode(int code, String message)` | 枚举构造，逐项写入 |
| 63-69 | `getCode()` / `getMessage()` | 供 `ApiResponse.fail(ErrorCode)` 与 `GlobalExceptionHandler` 读取 |

**错误码分层与方案 8.3 错误分类的对应关系**（这是本节最需要记住的一张表）：

| 方案 8.3 错误类别 | 本枚举段位 | 代表错误码 | 系统做出的行为（代码依据） |
|---|---|---|---|
| 通用 / 未分类 | 1xxx | `BUSINESS_ERROR` 1000、`PARAM_INVALID` 1001、`NOT_FOUND` 1004、`DATA_CONFLICT` 1009、`SYSTEM_ERROR` 1500 | 参数校验失败返回字段级明细（`handleValidation`）；数据冲突提示唯一约束（`handleIntegrity`）；其余落 1000；未捕获异常落 1500 |
| 认证 / 授权错误 | 2xxx | `UNAUTHORIZED` 2001、`FORBIDDEN` 2003、`LOGIN_FAILED` 2004、`ACCOUNT_DISABLED` 2005、`ACCOUNT_LOCKED` 2006、`TOKEN_INVALID` 2007 | 2001/2007 → HTTP 401；2003 → HTTP 403；登录类错误 → HTTP 200 + 精确中文提示，便于登录页原位展示 |
| 输入错误 | 3xxx | `INPUT_VALIDATE_FAILED` 3001、`FILE_TYPE_NOT_ALLOWED` 3002、`FILE_TOO_LARGE` 3003、`FILE_PARSE_FAILED` 3004、`INDICATOR_NOT_COVERED` 3005、`DATA_FIELD_MISSING` 3006、`DATA_FORMAT_INVALID` 3007、`DATA_VALUE_OUT_OF_RANGE` 3008、`DIRECTION_UNKNOWN` 3009 | **阻止提交**，并返回字段级错误（`GlobalExceptionHandler` 第 51-63 行、`DataBatchService.validate` 的 `{行号, 字段, 原因}` 明细） |
| 算法错误 | 4xxx | `ALGORITHM_ERROR` 4001、`AHP_MATRIX_INVALID` 4002、`AHP_CR_NOT_PASSED` 4003、`ALGORITHM_PARAM_INVALID` 4004、`ALGORITHM_NOT_SUPPORTED` 4005、`MISSING_VALUE_BLOCKED` 4006 | **任务失败**，并把算法原始错误保存到任务记录（算法引擎写入 `errorCode` / `errorMessage`，`TaskService` 落库） |
| 运行错误 | 5xxx | `ALGORITHM_ENGINE_UNAVAILABLE` 5001、`TASK_TIMEOUT` 5002、`TASK_CANCELED` 5003、`TASK_STATUS_ILLEGAL` 5004 | 任务状态置为失败/取消，前端任务列表可按错误码给出处置建议（如"算法服务不可用，请检查 C++ 引擎适配位配置"） |
| 数据源错误 | 6xxx | `DATASOURCE_CONNECT_FAILED` 6001、`DATASOURCE_SQL_FORBIDDEN` 6002、`DATASOURCE_QUERY_FAILED` 6003、`DATASOURCE_NOT_FOUND` 6004 | 6001/6003 保留根因消息（`rootMessage`）便于排错；6002 直接拒绝执行；6004 在 `handleBiz` 中额外映射为 HTTP 404 |

**易错点/注意**：
1. 段位是"给人和前端看的分类"，不是"给程序解析的区间"。除 `handleBiz` 里显式列出的几个码之外，**没有任何代码按 3xxx/4xxx 前缀做判断**，新增错误码时必须同时检查是否需要补进 `handleBiz` 的 `switch`。
2. 枚举名（如 `AHP_CR_NOT_PASSED`）会被写进数据库任务记录，因此**重命名枚举项等于改数据**，属破坏性变更。

### 2.5 `backend/src/main/java/com/ees/common/GlobalExceptionHandler.java`

**作用**：全局异常总出口。把 controller / service / 序列化 / 安全 / 数据库各层抛出的异常，统一转换为 `ApiResponse` + 指定 HTTP 状态。
**被谁使用**：Spring MVC 自动装配（`@RestControllerAdvice`），业务代码不显式调用；它自身依赖 `ApiResponse`、`ErrorCode`、`BizException`。
**关键设计点**：
1. **同一个"失败"要区分"给用户看的信息"和"给排错用的信息"**：业务异常（`BizException`）原样透传提示；未预期异常只在服务端 `log.error` 打完整堆栈（**静态资源缺失已被下面第 3 条单独摘出这条路径**），响应体只给截断后的根因消息，`README`/类注释要求的"生产环境不返回详细堆栈"由此落实。
2. **HTTP 状态码只用于承载协议语义**：401/403/404/500 有明确含义，因此真实使用；其余业务失败（含 3xxx 输入错误、4xxx 算法错误、409 类数据冲突）一律返回 HTTP 200，让前端 Axios 只在响应拦截器里按 `code` 分支，避免把"业务失败"误判成"网络/服务器故障"弹出通用错误。
3. **静态资源缺失单独拦截为 404，不再落进兜底分支**（`NoResourceFoundException`，第 97-119 行）：Spring 6.1 起请求不到的静态文件会抛该异常；若不单独映射，它就会落到 `handleOther`——记录一整串 ERROR 堆栈并返回 **500**，有两处害处：① **误导排障**：前端重新构建后 chunk 文件名（带内容 hash）会变化，浏览器若仍持有旧页面就会去取已经不存在的旧文件，那是"文件不存在"、应当 404，返回 500 会让人误判成"后端故障"；② **污染日志**：浏览器自动请求 `favicon.ico` 这类日常事件每次都会写一整串 ERROR 堆栈，与方案 9.1「生产环境关闭详细异常堆栈、日志不得打印密码和敏感配置」的要求相悖。因此该分支只按 `log.debug` 打一行"静态资源不存在"并返回 404。**这条分支的存在同时意味着 `handleOther` 不再是"兜底一切异常"**（见下表第 127-132 行）。
4. **参数校验失败必须带字段级明细**：`message` 里放人可读的拼接串，`data` 里放 `Map<字段名, 提示>`，前端表单可逐字段标红（对应方案 8.3"输入错误"的要求）。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.common;` | 公共基础包 |
| 3-4 | `import org.slf4j.Logger; import org.slf4j.LoggerFactory;` | 日志门面，用于区分 warn（业务/校验）、debug（静态资源缺失，见第 115 行）与 error（系统异常） |
| 5-20 | `import org.springframework.dao... / http... / validation... / web.bind.annotation... / multipart... / servlet... / servlet.resource...` | 导入 11 个异常类型 + `FieldError` + `HttpStatus`、`ResponseEntity`、`@ExceptionHandler`、`@RestControllerAdvice`：**每个 `@ExceptionHandler` 方法都要匹配一种具体异常类型，这是本文件行数的主要来源**（同包的 `BizException` 与 `java.lang.Exception` 两个分支无需导入）。第 20 行的 `org.springframework.web.servlet.resource.NoResourceFoundException` 为本次整改新增（见第 97-119 行） |
| 22-24 | `import java.util.LinkedHashMap; import java.util.Map; import java.util.stream.Collectors;` | 字段错误用 `LinkedHashMap` 保序（与 DTO 字段声明顺序一致，前端展示稳定）、`Collectors.joining` 拼 message |
| 26-28 | `/** 全局异常处理。生产环境不返回详细堆栈。 */` | 明确写出安全要求：不返回堆栈 |
| 29 | `@RestControllerAdvice` | 组合注解：拦截所有 controller 抛出的异常，并让返回值直接序列化为响应体 |
| 30 | `public class GlobalExceptionHandler {` | 无状态，可单例 |
| 32 | `private static final Logger log = ...` | 统一日志出口 |
| 34 | `@ExceptionHandler(BizException.class)` | 业务异常专属分支，优先级高于最后的 `Exception` 兜底 |
| 35 | `public ResponseEntity<ApiResponse<Object>> handleBiz(BizException e)` | 返回 `ResponseEntity` 以便自行决定 HTTP 状态（其余分支多为固定 200） |
| 36 | `log.warn("业务异常: code={} msg={}", ...)` | 用 warn 而非 error：业务异常是可预期的，不应污染错误告警 |
| 37 | `ApiResponse.fail(e.getErrorCode().getCode(), e.getMessage())` | 错误码用枚举的数值，提示用抛出时的具体消息（例如"账号已锁定至 ..."） |
| 38-40 | `if (e.getPayload() != null) body.setData(e.getPayload());` | 把 payload 回填到 `data`，实现"失败也带结构化明细" |
| 41-46 | `switch (e.getErrorCode()) { ... }` | **错误码 → HTTP 状态的唯一映射点**：`UNAUTHORIZED`/`TOKEN_INVALID` → 401（浏览器与前端拦截器据此跳登录页）；`FORBIDDEN` → 403；`NOT_FOUND`/`DATASOURCE_NOT_FOUND` → 404；`default -> 200`。之所以默认 200，是因为 3xxx 输入错误、4xxx 算法错误、1000/1009 业务失败都需要前端读取 `code` 与 `data` 做精细化展示，而不是被当成 HTTP 层故障 |
| 50 | `/** 参数校验失败：返回字段级错误，对应方案 8.3 "输入错误"。 */` | 方法注释直接标注与方案 8.3 的对应 |
| 51 | `@ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})` | 一次覆盖两种校验失败：`@RequestBody + @Valid` 抛前者，表单/查询参数绑定失败抛后者。之所以能写一个方法，是因为 Spring 6 中 `MethodArgumentNotValidException` 继承自 `BindException`（见第 52 行形参类型） |
| 53-56 | `Map<String, String> fieldErrors = new LinkedHashMap<>(); for (FieldError fe : ...) fieldErrors.putIfAbsent(...)` | 收集字段级错误；`putIfAbsent` 保证同一字段只保留第一条提示，避免同一字段多条约束互相覆盖导致提示抖动 |
| 57-59 | `fieldErrors.entrySet().stream().map(en -> en.getKey() + ": " + en.getValue()).collect(joining("; "))` | 生成人可读串，形如 `username: 用户名不能为空; password: 密码不能为空` |
| 60 | `log.warn("参数校验失败: {}", msg)` | 记录失败原因，便于排查前端漏传字段 |
| 61-62 | `return ResponseEntity.ok(ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), "参数校验失败 -> " + msg).withData(fieldErrors));` | **HTTP 200 + code 1001 + `data`=字段错误 Map**：200 是为了让前端拦截器正常解析出字段明细（若返回 400，Axios 会走 error 分支，表单拿不到逐字段提示）；`ErrorCode.PARAM_INVALID.getCode()` 显式取数值而非传枚举，保证与 `fail(int, String)` 重载匹配 |
| 65 | `@ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})` | 覆盖两类"缺参数"：必填 `@RequestParam` 缺失、`multipart` 文件分片缺失（如上传接口没带 `file`） |
| 66-67 | `ResponseEntity.ok(ApiResponse.fail(ErrorCode.PARAM_INVALID, "缺少必要参数: " + e.getMessage()))` | 仍归 1001；这里保留 `e.getMessage()` 是因为 Spring 的消息只含参数名（如 `Required request parameter 'id' ...`），不暴露内部实现 |
| 70-73 | `@ExceptionHandler(HttpMessageNotReadableException.class)` → `PARAM_INVALID` + "请求体格式错误，无法解析 JSON" | 请求体 JSON 语法错误/类型不匹配。**刻意不回显 `e.getMessage()`**：该消息会包含 Jackson 内部类名与字段解析路径，属于实现细节泄露 |
| 75-78 | `@ExceptionHandler(MaxUploadSizeExceededException.class)` → `ErrorCode.FILE_TOO_LARGE` + "上传文件超出大小限制" | 映射到 3003（输入错误段）。可触发性由配置保证：`application.yml` 第 32-33 行 `max-file-size: 200MB`、`max-request-size: 250MB`，超限即抛此异常 |
| 80-84 | `@ExceptionHandler(AccessDeniedException.class)` → `ResponseEntity.status(HttpStatus.FORBIDDEN)` + `ErrorCode.FORBIDDEN` | 方法级 `@PreAuthorize` 拒绝访问时抛出。这里**必须返回 403 而不是 200**：授权失败是真实的协议级语义，且与 `SecurityConfig` 第 64-65 行的 `accessDeniedHandler` 保持同一状态码与错误码，前端只需一套处理逻辑 |
| 86-89 | `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` → 200 + 1001 + "请求方法不支持: " + `e.getMethod()` | 方法用错（如对只支持 GET 的接口发 POST）。选择 200 + 1001 而非 405，是为了复用统一的参数错误提示通道；`getMethod()` 只回显方法名，安全 |
| 91-95 | `@ExceptionHandler(NoHandlerFoundException.class)` → `ResponseEntity.status(HttpStatus.NOT_FOUND)` + `ErrorCode.NOT_FOUND` + 请求 URL | 路径不存在。该分支可达的前提是 `application.yml` 第 56 行 `spring.mvc.throw-exception-if-no-handler-found: true` 已开启——否则 Spring 会自己返回 404 页面/wenti，不会进这里。返回 404 而非 200，是为了让前端路由守卫能统一跳 404 页面 |
| 97-119 | `/** 静态资源不存在：返回 404，且不打印堆栈。 */` + `@ExceptionHandler(NoResourceFoundException.class)` + `handleNoResource(NoResourceFoundException e)` | **本次整改新增的处理块**（提交 `4e41d8b`：修复白屏，缺失静态资源返回 404）。第 100-111 行 Javadoc 写明"必须单独处理"的两条理由：① 缺失静态资源返回 500 会**误导排障**——前端重新构建后 chunk 文件名（带内容 hash）会变，浏览器仍持旧页面时会去取已不存在的旧文件，那是"文件不存在"应为 404 而非"后端故障"；② 浏览器自动请求 `favicon.ico` 这类日常事件会**刷满 ERROR 堆栈**，与方案 9.1「生产环境关闭详细异常堆栈、日志不得打印密码和敏感配置」相悖。实现上第 115 行只 `log.debug("静态资源不存在: {}", e.getResourcePath())` 打一行，第 116-117 行返回 **HTTP 404 + `NOT_FOUND(1004)`** + "资源不存在: " + 资源路径；**不再落到 `handleOther` 打整串 ERROR 堆栈并返回 500** |
| 120-125 | `handleIntegrity(DataIntegrityViolationException e)` | 数据库唯一约束/外键冲突。第 122 行先 `log.warn` 记录最具体原因；第 123-124 行返回 **HTTP 200 + `DATA_CONFLICT(1009)`**，消息拼上 `rootMessage(e)`（唯一键冲突的真实字段名）——给业务人员"编码已存在"这类可操作提示，比 500 更有用。选 200 而不选 409，与前面"业务失败只走 code"的策略一致 |
| 127-132 | `@ExceptionHandler(Exception.class)` → `ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)` + `ErrorCode.SYSTEM_ERROR` + `"系统内部错误: " + rootMessage(e)` | 兜底分支（Spring 取"最具体"的处理方法，所以它只接住未预期的异常）；**注意它已不再是"兜底一切异常"**——`NoResourceFoundException` 已被第 113 行单独拦截（见上一行），静态资源缺失不会再走到这里。第 129 行 `log.error("系统异常", e)` 把**完整堆栈**写进服务端日志；第 130-131 行响应体只给 500 + 截断后的根因消息，**不含堆栈**——这就是类注释"生产环境不返回详细堆栈"的实现方式 |
| 134-144 | `private String rootMessage(Throwable e)` | 统一取根因的辅助方法：第 136-138 行沿 `getCause()` 走到最深处（`t.getCause() != t` 防自引用死循环）；第 139-142 行 message 为 null 时退化为类名简名，第 143 行截断到 400 字符，防止把整段 SQL 或超长驱动报错塞进响应体 |

**易错点/注意**：
1. **不会出现 400/405 响应**：参数与方法错误都被刻意映射成 `200 + 1001`，所以接口测试时"HTTP 200 但 `code=1001`"是预期行为，不是 bug。
2. 响应体里的 `rootMessage(e)` 仍可能包含数据库表名、约束名等内部信息（如 `Duplicate entry 'PRJ-2026-001' for key 'uk_project_code'`）。这是"排错友好"与"信息最小化"之间的取舍：若要对外网暴露，应改为按环境开关输出。
3. 该方法不处理过滤器链阶段（`SecurityConfig` 的 `authenticationEntryPoint` / `accessDeniedHandler`）的失败——那部分由 `SecurityConfig.writeJson` 独立产出，两者错误码一致但代码路径不同。
4. **静态资源 404 与接口 404 是两条不同路径**：`NoResourceFoundException`（第 113 行，Spring 6.1 起请求不到的静态文件）与 `NoHandlerFoundException`（第 91 行，接口路径不存在）都返回 404，但语义与日志级别不同——前者只 `log.debug`（浏览器的日常自动请求不该刷日志），后者不记日志。排查"静态资源返回 404"时，应先确认文件是否真的不存在（前端重新构建后带 hash 的 chunk 文件名已变化、浏览器仍持旧页面），不要当成后端故障；反过来，前端改了 chunk 命名却没有一起发布 `index.html`，也会在这里表现为 404 而不是 500。

### 2.6 `backend/src/main/java/com/ees/common/JsonUtils.java`

**作用**：把结构化对象与 JSON 文本互转，用于实体中以 TEXT 列保存的字段（算法参数、指标明细、质量报告、审计明细、错误明细等）。
**被谁使用**：13 个类——`algorithm/cpp/CppEngineAdapter`、`config/SecurityConfig`（WriteJson 输出响应）、`controller/SystemController`、`controller/UserController`，以及 `AuthService`、`BaselineService`、`DataBatchService`、`IndicatorService`、`ReportService`、`ResultService`、`SnapshotService`、`TaskService`。
**关键设计点**：
1. **一份共享 `ObjectMapper`**（第 19-23 行）：注册 `JavaTimeModule` 以支持 `LocalDateTime`，`NON_NULL` 让 TEXT 字段更紧凑，关闭时间戳序列化让 JSON 里是可读日期，`FAIL_ON_UNKNOWN_PROPERTIES=false` 让**旧数据在新代码下仍能解析**（对应方案 5.4 可追溯：历史任务参数不会因为新增字段而读不出来）。
2. **两套解析语义并存**：`toJson`/`parse` 用于系统自身产出的数据，失败即抛 `BizException(SYSTEM_ERROR)`（因为这属于程序缺陷）；`parseQuietly`、`toMap`、`toMapList`、`toStringList` 用于读取可能不完整的历史数据，失败返回默认值（空 Map / 空 List / 指定默认对象），绝不因为一条脏数据让整个列表接口 500。
3. 静态工具类、私有构造（第 25-26 行），避免被实例化。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.common;` | 公共基础包 |
| 3-8 | `import com.fasterxml.jackson...JsonInclude / TypeReference / DeserializationFeature / ObjectMapper / SerializationFeature / JavaTimeModule` | Jackson 核心类型 + JSR-310 时间模块（`LocalDateTime` 支持） |
| 10-12 | `import java.util.Collections; import java.util.List; import java.util.Map;` | 空值兜底需要 `Collections.emptyMap()` / `emptyList()` |
| 14-16 | `/** JSON 工具类，用于在实体中以 TEXT 保存结构化数据 ... */` | 说明用途边界：仅为 TEXT 列服务，不用于 HTTP 报文（那由 Spring MVC 的 `ObjectMapper` 负责） |
| 17 | `public final class JsonUtils {` | `final` 禁止继承 |
| 19-23 | `private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(...).setSerializationInclusion(NON_NULL).disable(WRITE_DATES_AS_TIMESTAMPS).disable(FAIL_ON_UNKNOWN_PROPERTIES);` | 全局唯一配置点：`JavaTimeModule` 让时间字段不报错；`NON_NULL` 省空间；前两个 `disable` 分别保证 JSON 中日期为 ISO 文本、历史数据多字段时不炸 |
| 25-26 | `private JsonUtils() { }` | 私有构造，纯静态工具类约定 |
| 28-30 | `public static ObjectMapper mapper()` | 暴露共享实例，供需要写自定义序列化的场景（如 `SecurityConfig` 之外的手工序列化）复用同一套配置 |
| 32-41 | `toJson(Object value)` | 第 33-35 行 null 直接返回 null（写入 TEXT 时用 null 比字符串 "null" 更干净）；第 37 行 `writeValueAsString`；第 39 行失败抛 `BizException(SYSTEM_ERROR, "JSON 序列化失败: ...", e)` 并带 cause |
| 43-52 | `toPrettyJson(Object value)` | 用 `writerWithDefaultPrettyPrinter()` 输出缩进 JSON，便于把配置/中间结果落库后人工阅读；第 50 行**失败时降级**调用 `toJson`——因为"好看"是次要目标，不该让功能整体失败 |
| 54-63 | `parse(String json, Class<T> type)` | 第 55-57 行 null/空白返回 null（对应"字段本来就没值"）；第 59 行按类解析；第 61 行失败抛 `BizException(SYSTEM_ERROR)`，属于严格模式 |
| 65-74 | `parse(String json, TypeReference<T> type)` | 泛型版本，用于 `List<Foo>`、`Map<String, Object>` 这类擦除后无法用 `Class` 表达的类型 |
| 76-86 | `parseQuietly(String json, TypeReference<T> type, T defaultValue)` | **宽松解析**：注释写明"用于读取历史数据"，失败返回 `defaultValue`。这是可追溯性要求下的必要妥协——历史 TEXT 若格式演进，列表页不应整体失败 |
| 88-98 | `toMap(String json)`（含 `@SuppressWarnings("unchecked")`） | 返回 `Map<String, Object>`，空/失败一律返回 `Collections.emptyMap()`。用于读取任务参数、快照统计等键值型 TEXT，调用方可以直接 `map.get(...)` 而不必判空 |
| 100-110 | `toMapList(String json)` | 返回 `List<Map<String, Object>>`，用于明细数组（如错误明细、比较明细）；用匿名 `TypeReference` 保留泛型；失败返回空列表 |
| 112-122 | `toStringList(String json)` | 返回 `List<String>`，用于角色权限清单这类字符串数组；`AuthService.permissionsOf` 用它读 `sys_role.permissions` |

**易错点/注意**：
1. 严格与宽松的边界要记牢：**写出去用 `toJson`，读回来自家数据用 `parse`，读历史/外部数据用 `parseQuietly`/`toMap*`**。用错会导致要么脏数据把列表接口打成 500，要么程序缺陷被静默吞掉。
2. `MAPPER` 是可变对象（`ObjectMapper` 本身线程安全，但通过 `mapper()` 拿到后如果调用方再改配置，会影响全局）。新增序列化定制时应优先考虑新建独立实例而非修改共享实例。

### 2.7 `backend/src/main/java/com/ees/security/JwtService.java`

**作用**：签发与校验 JWT。签发时把用户身份与授权信息写进自定义声明，校验时还原为轻量 `LoginUser`。**签名密钥不再取自配置字面量，而是由 `JwtSecretProvider` 解析后经构造注入提供**（见 2.8）。
**被谁使用**：`AuthService`（第 125-126 行登录时 `generate` 与 `expireSeconds`）、`JwtAuthenticationFilter`（第 34 行每请求 `parse`）；构造期依赖 `JwtSecretProvider`（Spring 注入，提供最终密钥）。
**关键设计点**：
1. **密钥来源由"读配置默认值"改为"注入解析结果"**：构造签名已由 `JwtService(EesProperties)` 变为 `JwtService(EesProperties properties, JwtSecretProvider secretProvider)`（第 40 行），第 42 行 `secretProvider.getSecret()` 拿到的是三级回退后的最终密钥。Spring 会先构造 `JwtSecretProvider`（其构造期就把最终密钥回写进 `EesProperties`）再构造本类，因此这里读到的永远不是配置里的空串。不继续在本类里写 `properties.getSecurity().getJwtSecret()` 的理由是：那会把"密钥从哪来"的知识散落到每个使用方，`CryptoUtils` 也就可能拿到另一个值——整改后两者必须共用同一把密钥（见 2.8、2.18）。
2. **类注释新增了"信任模型"说明（第 25-30 行）**：一段写"签名密钥不再从配置里直接取默认值，而是统一由 `JwtSecretProvider` 解析……这样交付包里不含任何可用密钥"；另一段写"本类只校验签名、签发者与有效期，**不比对数据库中的用户**，因此签名密钥一旦泄漏即等同于可伪造任意用户的全部权限——这也是必须移除内置默认密钥的原因"。第二段是本类最需要记住的一句话：`parse()` 不查库是刻意的无状态设计，所以**密钥就是唯一信任根**，它一旦是公开值，任何持有交付包的人都能签发 `role=SYSTEM_ADMIN`、`perms=["*"]` 的令牌通过全部鉴权（整改前实测可越权读写，见 2.8）。
3. **密钥长度不足必须补齐**：HS256 是 HMAC-SHA256，密钥短于 256 bit（32 字节）时 jjwt 的 `Keys.hmacShaKeyFor` 会抛 `WeakKeyException`，应用启动即失败。因此构造时先尝试 Base64 解码、再退回原始 UTF-8 字节，仍不足 32 字节时才做**确定性**补齐——确定性保证服务重启后同一密钥仍能校验旧令牌（若用随机补齐，重启即全员掉线）。
4. **空密钥有一道显式的防御性守卫（第 52-58 行）**：`if (bytes.length == 0) throw new IllegalStateException("JWT 签名密钥为空：请设置环境变量 EES_JWT_SECRET，或确保存储根目录可写，以便自动生成 <存储根>/.jwt-secret")`。**该分支在当前设计中不可达**——`JwtSecretProvider` 已保证解析出的密钥非空（无可用密钥时会自动生成，见 2.8），所以它是防御性代码而非正常业务分支。之所以仍然显式写出：一旦有人绕过 provider 拿到空密钥，失败点会停在**构造期**并抛出**语义清晰的 `IllegalStateException`**（消息里直接给出两条处置建议），而不是掉到第 64 行的 `bytes[i % bytes.length]` 抛出 `ArithmeticException: / by zero`——后者既不指向密钥问题、也不告诉运维该怎么办。它与 provider 的"非空保证"构成**纵深防御**：provider 负责让空密钥不出现，这里负责万一出现时把原因说清楚（详见易错点 1）。
5. **`parse` 失败返回 null 而不抛异常**：该过滤器在每个请求上运行，**包括免登录路径**（`/api/health`、静态资源等）。若过期/伪造令牌直接抛异常，匿名访问会被打断，且每个无效请求都会产生一条异常日志。返回 null 让过滤器"静默放行"，由 Spring Security 的 `anyRequest().authenticated()` + `authenticationEntryPoint` 去产出统一的 401 JSON。
6. 声明里带 `perms` 列表：无状态认证下服务端不查库就能完成 `@PreAuthorize("hasAuthority('data:batch:list')")` 判断，代价是权限变更要等令牌过期（480 分钟）才生效。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.security;` | 安全包 |
| 3 | `import com.ees.config.EesProperties;` | 读取 `ees.security.*` 配置（**有效期、签发者**；`jwtSecret` 已不再由本类直接读取） |
| 4-8 | `import io.jsonwebtoken...Claims / JwtException / Jwts / Decoders / Keys` | jjwt 0.12+ API：`Jwts.builder()`、`Jwts.parser().verifyWith(key)`、`Decoders.BASE64`、`Keys.hmacShaKeyFor` |
| 9-11 | `import org.slf4j...Logger/LoggerFactory; import org.springframework.stereotype.Component;` | 日志 + 注册为 Bean |
| 13-20 | `import javax.crypto.SecretKey; java.nio.charset.StandardCharsets; java.util.Date/HashMap/HashSet/List/Map/Set;` | 声明与集合类型 |
| 22-31 | `/** JWT 令牌服务。 ... */` | **类注释由 3 行扩成 10 行**：第 25-27 行写明密钥改由 `JwtSecretProvider` 统一解析、"交付包里不含任何可用密钥"；第 29-30 行写明本类只校验签名/签发者/有效期而**不比对数据库用户**，故密钥泄漏等于可伪造任意用户全部权限。这两段就是本次安全整改在代码内的书面结论 |
| 32-33 | `@Component public class JwtService {` | 单例 Bean |
| 35 | `private static final Logger log = ...` | 日志，仅用于 `parse` 失败的 debug 记录 |
| 37-38 | `private final EesProperties properties;` / `private final SecretKey key;` | 配置引用 + **启动时算好一次的密钥**，避免每请求重复派生 |
| 40-42 | `public JwtService(EesProperties properties, JwtSecretProvider secretProvider)` + `this.properties = properties; String secret = secretProvider.getSecret();` | **构造签名新增第二个参数**（整改点之一）。取值不再来自配置默认值，而是解析器的最终结果；Spring 的构造注入顺序保证 provider 已初始化完毕（含它对 `properties` 的回写），所以第 42 行取到的必定是三级回退后的非空密钥 |
| 43-48 | `byte[] bytes; try { bytes = Decoders.BASE64.decode(secret); if (bytes.length < 32) bytes = secret.getBytes(UTF_8); }` | 先假定密钥是 Base64（`JwtSecretProvider` 自动生成的密钥与推荐的 `EES_JWT_SECRET` 都是）；若解出来不足 32 字节，说明它其实是普通口令，改用原始 UTF-8 字节。这样同一个配置项既能写 Base64 也能写明文口令 |
| 49-51 | `catch (Exception e) { bytes = secret.getBytes(UTF_8); }` | 不是合法 Base64 时按明文处理，配置容错，避免启动失败 |
| 52-58 | `if (bytes.length == 0) { ... throw new IllegalStateException("JWT 签名密钥为空：请设置环境变量 EES_JWT_SECRET，或确保存储根目录可写，以便自动生成 <存储根>/.jwt-secret"); }` | **整改后追加的防御性守卫**（第 53-55 行注释原文即"正常情况下不可达"）：`JwtSecretProvider` 已保证解析出的密钥非空（无可用密钥时会自动生成），所以这一分支在当前设计下走不到。显式写出它的目的是——万一将来有人绕过 provider（手工 `new JwtService`、或在 provider 初始化之后改写 `properties`）拿到空密钥，失败点会停在这里并给出**语义清晰的启动失败原因**（消息带两条处置建议：设置 `EES_JWT_SECRET`、保证存储根目录可写），而不是掉到第 64 行的 `bytes[i % bytes.length]` 抛出 `ArithmeticException: / by zero`——后者既难定位，又容易被误判成算法或参数问题 |
| 59-67 | `if (bytes.length < 32) { byte[] padded = new byte[32]; System.arraycopy(...); for (...) padded[i] = (byte)(bytes[i % bytes.length] ^ (i * 31)); bytes = padded; }` | **HS256 要求密钥不少于 256 bit，不足时补足**（第 60 行注释原文）。前若干字节保留原密钥，其后用 `原字节[i % len] XOR (i*31)` 填充：确定性派生，重启后同一密钥仍得同一结果，旧令牌继续有效；XOR 混淆避免补齐部分出现规律性全 0。因第 52-58 行已把空数组拦在前面，此处 `bytes.length >= 1`，第 64 行的取模不会除零 |
| 68 | `this.key = Keys.hmacShaKeyFor(bytes);` | 生成 HMAC 密钥对象；长度已保证 ≥ 32 字节，故不会抛 `WeakKeyException` |
| 71 | `public String generate(LoginUser user)` | 签发入口，入参是登录上下文 |
| 72-77 | `Map<String, Object> claims = new HashMap<>(); claims.put("uid"/"name"/"role"/"scope"/"perms", ...)` | 自定义声明：`uid` 用户主键、`name` 真实姓名、`role` 角色编码、`scope` 数据范围、`perms` 权限码列表。第 77 行 `List.copyOf(...)` 把 `Set` 转为不可变 `List`（JSON 数组更通用，且避免后续被修改） |
| 78-79 | `Date now = new Date(); Date exp = new Date(now.getTime() + jwtExpireMinutes * 60_000L);` | 签发与过期时间；`60_000L` 用 long 字面量避免 int 溢出（480 分钟不会溢出，但写成 long 是防未来把有效期调到很大） |
| 80-87 | `Jwts.builder().claims(claims).subject(user.getUsername()).issuer(...).issuedAt(now).expiration(exp).signWith(key).compact()` | 标准声明：`sub` 放用户名（登录名唯一）、`iss` 放 `ees`（`parse` 时会强校验）、`iat`/`exp` 由 jjwt 校验；`signWith(key)` 自动按密钥长度选 HMAC-SHA256；`compact()` 产出 `xxx.yyy.zzz` 字符串 |
| 90-92 | `public long expireSeconds()` | 把配置的分钟数换成秒返回给前端（`LoginResponse.expiresIn`），前端据此做续期/超时提示 |
| 94 | `/** 解析令牌；非法或过期返回 null。 */` | 方法注释明确契约——调用方必须判空 |
| 96 | `try {` | 整个解析过程包在 try 中，任何异常都转为"无身份" |
| 97-102 | `Jwts.parser().verifyWith(key).requireIssuer(...).build().parseSignedClaims(token).getPayload()` | 校验三件事：签名（`verifyWith`，防篡改）、签发者（`requireIssuer`，防拿别系统令牌来用）、时间（`exp`/`nbf` 由 jjwt 自动校验）；`getPayload()` 取自定义声明。**这里刻意没有第四件事——不查库比对用户**（即类注释第 29-30 行所述信任模型） |
| 103-109 | `Set<String> perms = new HashSet<>(); Object raw = claims.get("perms"); if (raw instanceof List<?> list) { for (...) perms.add(String.valueOf(o)); }` | 把 JSON 数组还原为权限集合；用 `instanceof` 模式匹配 + `String.valueOf` 做防御式转换——即使令牌里的 `perms` 类型异常也不会抛异常，最差得到空集合（等价"无权限"，失败方向安全） |
| 110-117 | `Number uid = claims.get("uid", Number.class); return LoginUser.forToken(uid == null ? null : uid.longValue(), ...)` | JSON 数字反序列化后可能是 `Integer`/`Long`，统一按 `Number` 取再转 `long`；`uid` 缺失时传 null 而不报错，配合 `forToken` 构造轻量上下文 |
| 118-121 | `catch (JwtException \| IllegalArgumentException e) { log.debug(...); return null; }` | **只捕获这两类并返回 null**：`JwtException` 覆盖过期、签名错误、格式错误、签发者不符；`IllegalArgumentException` 覆盖 token 为空白串等情况。用 `debug` 级别：无效令牌多半是扫描器或过期前端，不应产生 warn 噪音。**整改后"用旧公开密钥伪造的令牌返回 HTTP 401"正是走的这一行**：签名不符在此被 `SignatureException`（`JwtException` 子类）接住 → 返回 null → 过滤器不建立认证 → `SecurityConfig` 的 `authenticationEntryPoint` 输出统一 401 |

**易错点/注意**：
1. **"空密钥"边界现在是"根因治理 + 显式兜底"两层，两者不矛盾**：上一版曾担心"`ees.security.jwt-secret` 配成空字符串会让取模那行抛 `ArithmeticException: / by zero`"。现在这一风险由两层分别处理——① **治理**：`JwtSecretProvider` 从根上保证密钥非空（`application.yml` 第 68 行写 `jwt-secret: ${EES_JWT_SECRET:}`，未设环境变量即为空串，provider 用 `trimToNull` 把空白一律当作"未配置"并回填自动生成的密钥），所以本类第 42 行正常情况下取不到空串；② **兜底**：本类第 52-58 行又显式加了一道 `bytes.length == 0` 守卫，**在当前设计中不可达、属防御性代码**，它的价值不在"现在会触发"，而在于：万一有人绕过 provider（单测/脚本手工 `new JwtService`，或在 provider 初始化之后执行 `properties.getSecurity().setJwtSecret("")`），失败会以 `IllegalStateException("JWT 签名密钥为空：请设置环境变量 EES_JWT_SECRET，或确保存储根目录可写……")` 这种**语义清晰的启动失败**出现，而不是第 64 行的 `ArithmeticException: / by zero`（那句话既看不出是密钥问题，也看不出该怎么处理）；同时它把"第 64 行取模安全"这一前提连同原因一起写在了代码里，后人删改时能看到依据。一句话：**provider 负责"不让空密钥出现"，守卫负责"万一出现时把原因说清楚"**。单测/脚本里手工构造本类时仍应给非空密钥。
2. 令牌一旦签发，**在有效期内无法撤销**（无状态设计）。`AccountNonLocked`/`isEnabled` 的变更只影响下次登录，详见 2.10 的易错点。叠加"密钥即唯一信任根"这一事实后可以推出：**签名密钥的保护等级等同于数据库口令**，泄露一次就等于全员全权限（见 2.8）。
3. `parse` 内部用 `String.valueOf` 宽松处理 `perms`，因此伪造的"权限字段类型错误"不会报错而是降级为空权限——这是刻意选的失败方向（安全优先）。
4. **短密钥只告警不报错（与"空密钥"的处理方式刻意不同）**：若把 `EES_JWT_SECRET` 设成 `abc`，provider 只 `log.warn`，本类第 59-67 行再把它补齐成 32 字节（签名强度实际低于 HS256 建议值）后照常启动。"能起来"不等于"安全"，长度下限只对自动生成的本地密钥文件强制（见 2.8）。对照第 52-58 行：**"空"意味着配置或存储出了问题，直接 `IllegalStateException` 启动失败；"短"只是强度不足，补齐 + 告警即可**——分级处理避免把"可运行但不理想"的部署也拦死。

### 2.8 `backend/src/main/java/com/ees/security/JwtSecretProvider.java`

> **本节为安全整改新增**（本类也是本次整改唯一新增的类）。整改前后的实测结论见下面「关键设计点 2」，答辩与交付检查可直接引用。

**作用**：解析 JWT 签名密钥的唯一入口。按"环境变量 → 本地密钥文件 → 进程内随机"三级顺序取出一个可用的密钥，并把最终结果**回写**到 `EesProperties.Security.jwtSecret`，使 `JwtService`（令牌签名）与 `CryptoUtils`（数据源密码的 AES 密钥派生）共用同一个值。
**被谁使用**：
- `security/JwtService` 第 40、42 行——构造注入本类并调用 `getSecret()` 作为签名密钥（见 2.7）；
- `config/StartupBanner` 第 24、32、67 行——调用 `describeSource()`，在启动横幅打印"签名密钥来源（未打印密钥本身）"；
- 反向关系：本类在构造期调用 `EesProperties.getSecurity().setJwtSecret(...)`，因此所有后续读该配置的代码（`CryptoUtils` 第 29 行）都拿到同一个最终密钥。
- 配置文件侧：`application.yml` 第 61-68 行把 `jwt-secret` 写成 `${EES_JWT_SECRET:}`，并注明三级顺序与"解析逻辑见 `com.ees.security.JwtSecretProvider`"。

**关键设计点**：
1. **抽出一个"密钥解析器"的原因**：整改前密钥有两个内置来源——`application.yml` 的 `${EES_JWT_SECRET:<写死的 Base64>}` 与 `EesProperties.Security.jwtSecret` 的写死默认值。两者都随源码交付，**等同公开值**。整改把 `EesProperties.Security.jwtSecret` 的默认值改成空字符串（该类归 01 分册讲解，本册只说明它带来的行为变化）、把 yml 的默认值去掉，再由本类统一决定"真正的密钥是什么"。这样交付包里不再含任何可用密钥，同时 `JwtService` 也不必知道密钥从哪来。
2. **整改前后的实测结论（本册最重要的一段事实）**：
   - **整改前（漏洞复现）**：用仓库内置的默认密钥在本地签发一枚 `role=SYSTEM_ADMIN`、`perms=["*"]` 的 HS256 令牌，访问 `/api/users`、`/api/configs`、`/api/audit-logs` **全部返回成功**（其中 `/api/audit-logs` 读到 69 条记录），并且 `POST /api/data-sources` 能成功写入数据——属**可越权读写**的真实漏洞。根因是 `JwtService.parse()` 只校验签名、签发者与有效期，**不比对数据库中的用户**（见 2.7 关键设计点 2），因此密钥泄漏即等于可伪造任意用户与全部权限。
   - **整改后（验证结果）**：用同一枚旧公开密钥伪造的令牌访问上述三个管理接口，**全部返回 HTTP 401**；`admin/admin123` 正常登录后由 `JwtService` 签发的令牌访问 `/api/users` 返回成功；**重启前后密钥文件 SHA256 一致**，且重启前签发的令牌重启后仍然有效；启动日志只打印密钥**来源**（例如"本地密钥文件 …"），**从不打印密钥本身**。
3. **三级回退的顺序就是"安全与可用性的取舍顺序"**：① 甲方有统一密钥管理要求时用环境变量/配置项；② 默认（离线交付）用本机首次启动自动生成并**持久化**的密钥文件，不加任何配置即可运行；③ 极端情况（`data` 目录只读）退化为进程内随机密钥——宁可"重启后需要重新登录"，也不能让系统启动失败。
4. **为什么必须持久化，而不能每次启动随机生成**：`CryptoUtils` 加密数据源密码的 AES 密钥由 `SHA-256("ees-datasource::" + jwtSecret)` 派生（见 2.18），若每次启动换密钥，**已保存的数据源密码将全部无法解密**（表现为"密码解密失败，可能加密密钥已变更"），同时所有已签发令牌失效。因此密钥跨重启必须稳定。密钥文件落在 `<ees.storage.root>`（默认 `./data`）下，而 `backend/data/` 已被 `.gitignore` 忽略，不会进入版本库——但反过来说，**备份数据目录时必须连它一起备份**。
5. **保留对旧公开密钥的"堵漏"**：`REMOVED_INSECURE_SECRETS` 把整改前的那两个公开密钥登记为黑名单，即使甲方配置里仍残留它们（沿用旧 `application.yml` 或旧环境变量脚本），也会被识别、忽略并给出显式告警，避免出现"文档改了但配置没改"的假整改。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.security;` | 安全包（与 `JwtService`、`JwtAuthenticationFilter` 同包，属同一条认证链路） |
| 3-6 | `import com.ees.config.EesProperties;` / `org.slf4j.Logger/LoggerFactory` / `org.springframework.stereotype.Component` | 读配置（`security.jwtSecret`、`storage.root`）、打印告警与来源日志、注册为 Bean |
| 8-15 | `import java.io.IOException; java.nio.charset.StandardCharsets; java.nio.file.Files/Path/Paths; java.security.SecureRandom; java.util.Base64/Set;` | NIO 文件读写、密码学随机数、Base64 文本化、`Set.of` 黑名单 |
| 17-48 | `/** JWT 签名密钥的解析与落地。 ... */` | 类注释就是一份整改说明：整改前的越权实测、三级回退、为什么必须持久化、以及对旧密钥的兼容处理。**方法实现的意图全部写在这里**，读代码前先读它 |
| 49 | `@Component` | 注册为单例 Bean，于是"密钥解析"发生在 Spring 启动阶段（配置或磁盘问题在启动时暴露，而不是等第一次登录才失败） |
| 50 | `public class JwtSecretProvider {` | 注意它不是工具类：持有 `properties` 引用，并在构造期产生副作用（回写配置） |
| 52 | `private static final Logger log = ...` | 全部告警与来源日志的唯一出口 |
| 54-55 | `/** 自动生成的部署密钥文件名 ... */ private static final String KEY_FILE_NAME = ".jwt-secret";` | 文件名以 `.` 开头（Linux 下即隐藏文件）；用常量而非拼接，改名只需改一处 |
| 57-58 | `/** 自动生成密钥的随机字节数（512 bit，远超 HS256 要求的 256 bit） */ private static final int KEY_BYTES = 64;` | 注释给出理由：HS256 的强度上限是 256 bit，这里生成 512 bit 的余量是为了让"Base64 解码后仍 ≥32 字节"这条路径绝对成立（64 字节 → Base64 后 88 字符，见第 147 行的写入内容） |
| 60-61 | `/** HS256 允许的最短密钥长度（256 bit），短于该长度视为不安全 */ private static final int MIN_SAFE_LENGTH = 32;` | 与 jjwt `Keys.hmacShaKeyFor` 的下限一致；同时用作"本地密钥文件内容是否可信"的判据（第 135 行）与配置密钥的告警阈值（第 112-115 行） |
| 63-71 | `/** 整改前内置在代码与配置里的"默认密钥" ... */ private static final Set<String> REMOVED_INSECURE_SECRETS = Set.of("<108 字符的 Base64 串>", "ees-default-secret-please-change-in-production-environment-2026");` | **整改的"堵漏"环节**：第 70 行是整改前 `application.yml` 的默认值（`ees-efficiency-evaluation-system-jwt-secret-key-2026-please-change-in-production` 的 Base64 形式，108 字符、解码后 80 字节），第 71 行是整改前 `EesProperties.Security.jwtSecret` 的字段默认值（63 字符明文）。登记为黑名单后，"改了代码却留着旧配置"的部署也能被识别并纠正。用 `Set.of`（不可变集合）而非 `List`：判定是等值包含，且不可变可防止运行期被篡改 |
| 73-75 | `private final EesProperties properties; / private final String secret; / private final String source;` | 三个字段都在构造期一次算好且为 `final`：Bean 创建后状态不可变，而 `getSecret()` 会被 `JwtService`（可能被并发调用）安全读取 |
| 77-85 | `public JwtSecretProvider(EesProperties properties) { this.properties = properties; String[] resolved = resolve(); this.secret = resolved[0]; this.source = resolved[1]; properties.getSecurity().setJwtSecret(this.secret); }` | 构造注入配置对象；第 79 行完成三级取值；第 80-81 行拆出密钥与来源说明；第 82-84 行（含两行注释）**把最终密钥回写进 `EesProperties`**——这是全类最关键的一步，见「为什么这么写」第 2 条 |
| 87-90 | `/** 最终生效的 JWT 签名密钥。 */ public String getSecret()` | 唯一的密钥出口，`JwtService` 第 42 行即取此处 |
| 92-95 | `/** 密钥来源的说明文字，用于启动自检横幅展示。 */ public String describeSource()` | 只暴露"来源"文本（如"本地密钥文件 D:\...\data\.jwt-secret"），**不暴露密钥本身**；`StartupBanner` 第 67 行打印它并显式注明"未打印密钥本身" |
| 97-121 | `/** @return 长度为 2 的数组：[0] 最终密钥，[1] 密钥来源说明。 */ private String[] resolve()` | 三级回退主流程（第一级在本方法内，第二、三级委托给 `loadOrCreateLocalKey`），逐行见下面 4 行 |
| 99 | `String configured = trimToNull(properties.getSecurity().getJwtSecret());` | 读配置并做"空白 → null"归一。现在 yml 写的是 `jwt-secret: ${EES_JWT_SECRET:}`（第 68 行），未设置环境变量时该值为**空串**，用 `trimToNull` 统一成"未配置"，后面只需判 `!= null` |
| 101-109 | `if (configured != null && REMOVED_INSECURE_SECRETS.contains(configured)) { log.warn(...6 行...); configured = null; }` | **第一级的第一道闸**：配置里仍是整改前的公开密钥时，输出 6 行显眼告警（含"任何人都能据此伪造系统管理员令牌"与处置建议"请删除配置中的该项，或改为设置环境变量 `EES_JWT_SECRET`"），然后把 `configured` 置空，流程自然落到下一级。**刻意不拒绝启动**：离线交付现场优先保证系统能起来，问题通过告警暴露给运维 |
| 111-118 | `if (configured != null) { if (configured.length() < MIN_SAFE_LENGTH) { log.warn(...); } log.info("JWT 签名密钥来源：环境变量 / 配置项 EES_JWT_SECRET（长度 {}）。", ...); return new String[]{configured, "环境变量 EES_JWT_SECRET"}; }` | **第一级（优先级最高）**：环境变量/配置项生效，适用于甲方有统一密钥管理的场景。第 112-115 行对短于 32 字符的密钥只告警不拒绝——拒绝会让该系统直接起不来，这属于"合规提示"而不是"准入校验"（真正的长度强制只针对自动生成的密钥文件，见第 135 行） |
| 120 | `return loadOrCreateLocalKey();` | 落到第二、三级：本地密钥文件优先，文件不可用时在该方法内部退化为进程内随机密钥 |
| 123-161 | `/** 读取本地密钥文件；不存在或内容不合法时生成一个新的并持久化。 */ private String[] loadOrCreateLocalKey()` | 见下面各行 |
| 129-130 | `Path keyFile = Paths.get(properties.getStorage().getRoot(), KEY_FILE_NAME).toAbsolutePath().normalize();` | 密钥路径 = `<ees.storage.root>/.jwt-secret`（默认 `./data/.jwt-secret`）。`toAbsolutePath().normalize()` 让日志与启动横幅里显示的是**可核对的绝对路径**（去掉 `./`、`..`），运维照抄即可定位文件 |
| 133-140 | `if (Files.isRegularFile(keyFile)) { String existing = trimToNull(new String(Files.readAllBytes(keyFile), UTF_8)); if (existing != null && existing.length() >= MIN_SAFE_LENGTH) { log.info("JWT 签名密钥来源：本地密钥文件 {}", keyFile); return new String[]{existing, "本地密钥文件 " + keyFile}; } log.warn("本地密钥文件 {} 内容为空或过短，将重新生成。", keyFile); }` | **第二级的"读"分支**：`isRegularFile` 同时排除目录、符号链接等不可读形态；`trimToNull`（第 134 行）吃掉第 147 行写入时补的换行符，避免密钥里带上 `\n` 导致每次算出的 HMAC 密钥都不同；长度不足 32 视为文件被截断或人工填错，不采用而是重新生成，第 139 行先告警说明"为什么密钥变了" |
| 142 | `String generated = generateKey();` | 生成新密钥（见第 163-167 行） |
| 143-146 | `Path parent = keyFile.getParent(); if (parent != null) { Files.createDirectories(parent); }` | **必须先建目录再写文件**：全新机器上 `ees.storage.root`（默认 `./data`）并不存在，若省掉这一步，第 147 行的 `Files.write` 会抛 `NoSuchFileException` 并直接跳到第 153 行的退化分支——结果就是"首次启动本应生成持久密钥，却拿到了临时密钥"。`getParent()` 在路径只有文件名时返回 null，故先判空；`createDirectories` 对已存在的目录是幂等成功 |
| 147 | `Files.write(keyFile, (generated + System.lineSeparator()).getBytes(UTF_8));` | 落盘（`Files.write` 默认 `CREATE + TRUNCATE_EXISTING + WRITE`，重写同一路径不会残留旧内容）。补一个**平台换行符**只是为了用记事本打开时文本不"粘"在光标上，读取时由第 134 行的 `trimToNull` 吃掉，两侧约定成对 |
| 148 | `restrictToOwner(keyFile);` | 尽力把文件权限收紧到"仅属主可读写"（第 169-183 行），失败不影响功能 |
| 149-151 | `log.info("首次启动：已自动生成本机专用的 JWT 签名密钥并保存到 {}", keyFile); log.info("该密钥不在版本库中，请随 data 目录一并备份；如需自行指定，可设置环境变量 EES_JWT_SECRET 覆盖。"); return new String[]{generated, "首次启动自动生成（已保存到 " + keyFile + "）"};` | 两条日志都**只打路径不打密钥**（对应技术方案 9.1"日志不得打印密码和敏感配置"）。第 150 行是给运维的硬性提醒：密钥只存在于 `data` 目录里，做数据备份就必须连它一起备份，否则恢复后数据源密码全部作废 |
| 153-160 | `catch (IOException \| RuntimeException e) { log.warn("无法读写本地密钥文件 {}（{}），本次启动退化为进程内随机密钥；重启后所有登录态失效，且已保存的数据源密码将无法解密。建议设置环境变量 EES_JWT_SECRET 以获得稳定密钥。", keyFile, e.toString()); return new String[]{generateKey(), "进程内随机密钥（临时，未持久化）"}; }` | **第三级的退化路径**：`IOException` 覆盖读写失败（目录只读、磁盘满、权限不足），`RuntimeException` 覆盖路径非法（如 `InvalidPathException`）等运行期问题。这里**只告警、不抛出**，保证系统仍能启动；告警文案把两个后果（登录态失效、数据源密码无法解密）与可执行的处置（设置 `EES_JWT_SECRET`）都写全。`e.toString()` 只带异常类名与消息、不带堆栈，避免日志被刷屏；两条注释（第 154-155 行）写明了上述取舍 |
| 163-167 | `private String generateKey() { byte[] raw = new byte[KEY_BYTES]; new SecureRandom().nextBytes(raw); return Base64.getEncoder().encodeToString(raw); }` | 用 `SecureRandom` 而不是 `Random`：`java.util.Random` 是可预测的线性同余序列，攻击者拿到若干输出即可反推后续值，用在密钥上等于没随机。输出为 Base64 文本（64 字节 → 88 字符），既能直接写文件，也能原样拷进 `EES_JWT_SECRET`；且它一定是合法 Base64，`JwtService` 第 45 行的解码分支会解出 64 字节 |
| 169-172 | `/** 尽力收紧密钥文件权限（仅属主可读写）。Windows 上 java.io.File 的权限接口实现有限，失败不影响功能，故静默忽略。 */` | 注释先说明了"尽力而为"的理由，这是理解下面空 catch 的前提 |
| 173-183 | `private void restrictToOwner(Path keyFile) { try { java.io.File file = keyFile.toFile(); file.setReadable(false, false); file.setReadable(true, true); file.setWritable(false, false); file.setWritable(true, true); } catch (RuntimeException ignored) { } }` | 四步是 `java.io.File` 权限 API 的固定套路：先"对所有人关闭"（第二个参数 `false` = 不限属主），再"只对属主打开"（第二个参数 `true` = 仅作用于属主）。第 180-182 行空 catch 是刻意的：权限收紧是加固措施而非功能前提，失败不该影响启动，也不该刷告警 |
| 185-191 | `private static String trimToNull(String s) { if (s == null) return null; String t = s.trim(); return t.isEmpty() ? null : t; }` | 全类唯一的字符串归一方法，被第 99 行（配置值）与第 134 行（文件内容）共用，见「为什么这么写」第 5 条 |

**为什么这么写（逐条说明动机与坑点）**：
1. **`resolve()` 返回 `String[]`（[0] 密钥、[1] 来源说明）而不是自定义小对象**：这两条信息在本类里总是成对产生——每一处 `return` 都同时给出（第 117、137、151、159 行），用长度固定为 2 的数组可以省掉一个只在本类内部使用的类型。来源说明是给启动横幅/日志看的**展示字符串**，不是结构化数据，因此不需要 getter 载体。代价是可读性（`resolved[0]` 不如 `getSecret()` 直观），所以第 80-81 行立刻把它拆成两个有名字的 `final` 字段，数组本身不往外传。
2. **构造期回写 `properties.getSecurity().setJwtSecret(...)`（第 82-84 行）是整个设计的关键**：`JwtService` 与 `CryptoUtils` 都从 `EesProperties` 读 `jwtSecret`，而配置里现在写的是空串（`${EES_JWT_SECRET:}`）。如果只让 `JwtService` 用 provider 的值、而 `CryptoUtils` 继续读配置，两者就会用**不同的密钥**——数据源密码用空串派生加密、却要用真实密钥解密，必然失败。回写之后"最终密钥只有一个来源"，两处口径永远一致。副作用是 `EesProperties` 在运行期被改写，属刻意为之（见易错点 6）。
3. **`Files.createDirectories` 不能省**（第 143-146 行）：它是"首次启动能生成持久密钥"与"退化到临时密钥"的分水岭（详见表格对应行）。这是本类最容易在联调时踩到的一点：本地机器上 `data` 目录早已存在，路径永远正常；交付到新机器才暴露为"每次重启都要重新登录"，而日志里的告警正好能指向原因。
4. **`restrictToOwner()` 是"尽力而为"而不是安全边界**：在 Linux 上它能把文件权限收到 `rw-------`，在 Windows 上 `java.io.File` 的权限接口基本是空实现（`setReadable` 往往返回 false 而不抛异常）。之所以仍然调用：加固零成本、失败不影响功能；之所以不能依赖它：跨平台不成立。真正的保护手段是"`data` 目录不放到共享盘/不随源码交付"。
5. **`trimToNull()` 存在的理由**：同一个"取值"动作有两个易错输入——配置里的空串（yml 的 `${EES_JWT_SECRET:}` 语法）和文件末尾的换行符（写文件时为了可读性补的）。把两者都归一为 `null`/去空白串之后，`resolve()` 的判断可以只写 `!= null`，不必到处写 `isBlank()`。（它同时承担了安全作用：**只含空白字符的"密钥"会被当作未配置**，从而落到自动生成分支，而不是变成一个可预测的弱密钥。）
6. **`catch (IOException \| RuntimeException)` 的告警语义**（第 153-160 行）：捕获面故意覆盖"文件系统问题"与"运行期路径问题"两类；处理方式是**降级而非中断**——因为对离线交付系统来说"能启动但需要重新登录"远好过"起不来"。告警里必须写清后果与处置办法（重启后登录态失效、数据源密码无法解密、建议设置 `EES_JWT_SECRET`），否则运维只看到一句"无法读写文件"会误判为普通权限问题。注意此处**不能改为 `catch (Exception)` 后静默**，那会把"密钥不持久"这一严重状态藏起来。
7. **黑名单而非"拒绝启动"**（第 101-109 行）：命中旧公开密钥时选择"告警 + 忽略 + 继续按第二/三级取密钥"，这样即使甲方配置没清理，系统也是**安全地**运行（用的是新生成的密钥），旧令牌自然全部失效——这正是整改想要的效果。

**易错点/注意**：
1. **配置密钥短于 32 字符只告警不拒绝**（第 112-115 行）：把 `EES_JWT_SECRET` 写成 `abc` 也能正常启动，签名强度实际低于 HS256 的建议值。长度下限只在自动生成的密钥文件上强制（第 135 行）。生产交付检查时要把"密钥长度 ≥32 字符且非旧公开值"写进检查表。
2. **删除或替换密钥文件 = 换密钥**：所有已签发令牌立即失效，且**已保存的数据源密码全部无法解密**（`CryptoUtils` 会报"密码解密失败，可能加密密钥已变更，请重新录入数据源密码"）。因此 `<storage.root>/.jwt-secret` 必须纳入备份范围（`docs/DEPLOY.md` 5.1、第 290 行有对应条目）。
3. **退化到进程内随机密钥是"会在重启后被用户发现"的故障**：只体现在日志与启动横幅那一行（`describeSource()` 返回"进程内随机密钥（临时，未持久化）"）。生产环境启动后应确认横幅里出现的是"本地密钥文件 …"或"环境变量 EES_JWT_SECRET"。
4. **黑名单是等值匹配**：`REMOVED_INSECURE_SECRETS.contains(configured)` 只能识别"与旧值完全相同"的密钥。若有人在旧密钥末尾加一个字符，仍会被当作合法配置接受（顶多触发长度告警）。"这个密钥是否来自交付包"无法在代码里判定，只能靠交付检查与人工确认。
5. **权限收紧在 Windows 上不可依赖**（第 169-183 行）：不要对外声称"密钥文件只有属主可读"。Linux 部署时应确认 `data` 目录不在共享位置、也不在容器的只读层里。
6. **本类在构造期改写 `EesProperties`**：这意味着 `properties.getSecurity().getJwtSecret()` 在 provider 初始化**之后**读到的是真实密钥，在此之前（例如同级的其他 Bean 构造期）读到的是空串。当前只有 `JwtService` 与 `CryptoUtils` 使用该配置，前者通过构造依赖保证顺序，后者在请求处理时才调用 `key(properties)`，因此都安全；**将来若有 Bean 在构造期就取 `jwtSecret`，必须同样声明对 `JwtSecretProvider` 的依赖**，否则会静默拿到空串。
7. `resolve()` 只在 Bean 创建时执行一次：修改 `EES_JWT_SECRET` 或替换密钥文件都必须**重启**才生效（热更新不在当前实现范围内）。

### 2.9 `backend/src/main/java/com/ees/security/JwtAuthenticationFilter.java`

**作用**：每请求执行一次，从三处取令牌 → 校验 → 把 `LoginUser` 放进 `SecurityContextHolder`，使后续的 `@PreAuthorize` 与 `SecurityUtils` 可用。
**被谁使用**：`config/SecurityConfig` 第 70 行 `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` 注册进过滤器链。
**关键设计点**：
1. **三种取令牌位置各有不可替代的用途**：
   - `Authorization: Bearer xxx` —— 标准位置，前端 Axios 拦截器默认走这里（`frontend/src/api/request.ts` 第 49 行 `config.headers['Authorization'] = 'Bearer ' + token`）。
   - `X-Token` —— 备用位置，用于 `Authorization` 被反向代理/Nginx 规则占用或过滤、以及用 curl/Postman 手工验证接口的场景；也让前端在不需要改动主拦截器的特殊请求上单独传令牌。
   - 查询参数 `?token=` —— **导出下载场景新窗口打开**（第 54 行注释原文）。浏览器直接打开下载链接（`window.open` / `<a href>` 导航）时**无法附加自定义请求头**，只能把令牌放在 URL 上；`docs/API.md` 第 13 行也把"下载类接口也支持 `?token=<token>`"写进了接口契约。代价是令牌会进入访问日志与浏览器历史，因此这是"仅用于下载"的受限通道，令牌本身 480 分钟后过期。
2. **过滤器只"认身份"，不"拦请求"**（第 33-41 行没有 return/403 分支）：认证失败时它什么都不做，请求继续走到 `SecurityConfig` 的 `anyRequest().authenticated()`，由 `authenticationEntryPoint` 输出统一的 `2001` 401 JSON。好处是全站未登录响应格式完全一致。
3. `OncePerRequestFilter` + `getAuthentication() == null` 双重防重：前者保证转发/包含时不重复执行，后者避免覆盖已存在的认证（例如测试或后续新增的其他认证机制）。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.security;` | 安全包 |
| 3-6 | `import jakarta.servlet...FilterChain / ServletException / HttpServletRequest / HttpServletResponse` | Servlet 过滤器契约 |
| 7 | `import org.springframework.lang.NonNull;` | 参数非空标注，便于静态检查 |
| 8-10 | `import ...UsernamePasswordAuthenticationToken / SecurityContextHolder / WebAuthenticationDetailsSource` | 构造认证对象、写入上下文、附加请求细节（IP、SessionId） |
| 11-12 | `import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;` | 注册为 Bean 且保证单请求单次执行 |
| 14 | `import java.io.IOException;` | `doFilterInternal` 声明抛出 |
| 16-18 | `/** JWT 认证过滤器：从 Authorization: Bearer xxx 或 X-Token 中解析令牌。 */` | 注释只提了两种位置，实际还有第三种（见第 54 行），属注释滞后 |
| 19-20 | `@Component public class JwtAuthenticationFilter extends OncePerRequestFilter {` | 该 Bean 由 `SecurityConfig` 注入并插入过滤器链；注意它**不是** `@Component` 自动生效的，必须显式 `addFilterBefore` |
| 22 | `private final JwtService jwtService;` | 依赖解析服务 |
| 24-26 | `JwtAuthenticationFilter(JwtService jwtService)` | 构造注入 |
| 28-31 | `protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)` | 过滤器主方法签名，三个参数均由容器注入 |
| 32 | `String token = resolveToken(request);` | 统一取令牌（三种来源的顺序见第 45-60 行） |
| 33 | `if (token != null && SecurityContextHolder.getContext().getAuthentication() == null)` | 两个条件缺一不可：无令牌则什么都不做（免登录路径与匿名请求照常通过）；已有认证则不重复解析（避免覆盖） |
| 34 | `LoginUser user = jwtService.parse(token);` | 解析，非法/过期返回 null（见 2.7） |
| 35 | `if (user != null) {` | 只有解析成功才建立认证；失败保持匿名，交由后续授权规则拒绝 |
| 36-37 | `UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());` | 三个参数：principal 为 `LoginUser`（`SecurityUtils` 依赖此类型做 `instanceof` 判断）、credentials 为 null（无状态，不需要密码）、authorities 来自 `LoginUser.getAuthorities()`（`ROLE_xxx` + 细粒度权限码） |
| 38 | `auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));` | 附加请求细节（远端 IP 等），`AuditService` 记录审计日志时可取用 |
| 39 | `SecurityContextHolder.getContext().setAuthentication(auth);` | 写入线程本地上下文，同一请求后续所有代码（controller、service）都能读到 |
| 42 | `chain.doFilter(request, response);` | **无条件放行**：无论认证成功与否都继续走链，把"是否允许"交给 Spring Security 的授权规则——这是过滤器"只认证不授权"的分工 |
| 45 | `private String resolveToken(HttpServletRequest request)` | 取令牌的优先级顺序实现 |
| 46-49 | `String header = request.getHeader("Authorization"); if (header != null && header.startsWith("Bearer ")) return header.substring(7).trim();` | 标准分支：`Bearer ` 恰好 7 个字符，`substring(7)` 后 `trim()` 容错多余空格；大小写敏感是 OAuth2 规范要求 |
| 50-53 | `String alt = request.getHeader("X-Token"); if (alt != null && !alt.isBlank()) return alt.trim();` | 备用头，覆盖代理剥离/占用 `Authorization` 的场景；`isBlank()` 判空避免空白串被当作令牌 |
| 54 | `// 导出下载场景允许通过查询参数携带令牌（前端新窗口打开）` | 注释解释第三种位置的**存在理由**：新窗口/直接导航无法设置请求头 |
| 55-58 | `String qp = request.getParameter("token"); if (qp != null && !qp.isBlank()) return qp.trim();` | 查询参数分支。注意 `getParameter` 会触发请求体解析规则（对 `application/x-www-form-urlencoded` 会读取 body），但对本系统的 JSON 接口与前三种令牌来源无影响 |
| 59 | `return null;` | 三处都没有 → 无令牌 |

**易错点/注意**：
1. 类注释（第 17 行）只写了两种取令牌方式，实际实现有三种；读代码时以 `resolveToken` 为准。
2. `?token=` 通道是安全敏感项：URL 会进 Nginx access log、浏览器历史、Referer。若后续要对公网暴露，应改为一次性下载票据（短期 ticket）而非长期 JWT。
3. 过滤器在每个请求上先 `getAuthentication() == null` 判断，再决定是否解析——这意味着**同一请求内多次经过该过滤器**（转发场景）不会重复消耗解析开销。

### 2.10 `backend/src/main/java/com/ees/security/LoginUser.java`

**作用**：登录用户上下文，实现 Spring Security 的 `UserDetails`，同时承载本系统需要的业务字段（用户主键、真实姓名、角色编码/名称、数据范围、权限码集合）。
**被谁使用**：8 个类——`JwtAuthenticationFilter`（作为 principal 存入上下文）、`JwtService`（签发时读取字段）、`SecurityUtils`（读取当前用户）、`AuthController`、`AuthService`（登录时构造）、`AuditService`、`ProjectService`（数据范围过滤）。
**关键设计点**：
1. **权限双轨制**：`getAuthorities()` 同时产出 `ROLE_<角色编码>` 与每条细粒度权限码，因此 `@PreAuthorize` 可以写成 `hasAuthority('data:batch:list') or hasRole('SYSTEM_ADMIN')`（`DataBatchController` 第 87、108 行即此写法）。角色管"哪一类人"，权限码管"哪个动作"，两者并存才能既粗又细地授权。
2. **`forToken` 造轻量上下文（第 42-46 行）**：令牌路径下把 `password` 传 null。BCrypt 哈希没有任何必要常驻每次请求的 `SecurityContext`，少一个敏感字段就少一处泄露面。
3. **构造期做空值兜底**（第 37-38 行）：`dataScope` 为 null 时默认 `"PROJECT"`（数据范围收敛到项目级，失败方向安全）；`permissions` 为 null 时给空集合，避免第 90 行 for 循环 NPE。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.security;` | 安全包 |
| 3-5 | `import ...GrantedAuthority / SimpleGrantedAuthority / UserDetails` | Spring Security 的权限与用户契约 |
| 7-11 | `import java.util.ArrayList / Collection / Collections / List / Set;` | 构造权限列表与空集合兜底 |
| 13-15 | `/** 登录用户上下文。权限集合包含 ROLE_xxx 与细粒度权限码。 */` | 一句话点明双轨权限设计 |
| 16 | `public class LoginUser implements UserDetails {` | 实现标准接口，才能被 Spring Security 的 `@PreAuthorize`、`SecurityContextHolder` 正确识别 |
| 18-26 | 9 个 `final` 字段：`userId`、`username`、`password`、`realName`、`roleCode`、`roleName`、`dataScope`、`permissions`、`enabled` | 全部 `final`：登录上下文一旦建立即不可变，避免多线程共享时的可见性与篡改问题 |
| 28-30 | 全参构造签名 | 显式 9 参而不是 Builder，字段少、调用点在 2 处（`AuthService` 第 121 行、`forToken`） |
| 31-36 | 逐个字段赋值 | 直接装配 |
| 37 | `this.dataScope = dataScope == null ? "PROJECT" : dataScope;` | 数据范围为空的兜底：默认按项目级隔离（而非全量），越权风险最小的默认值 |
| 38 | `this.permissions = permissions == null ? Collections.emptySet() : permissions;` | 空集合兜底，保证第 90 行循环安全 |
| 39 | `this.enabled = enabled;` | 账号启用状态 |
| 42-46 | `public static LoginUser forToken(Long userId, String username, String realName, String roleCode, String dataScope, Set<String> permissions)` → `new LoginUser(..., null /*password*/, ..., null /*roleName*/, ..., true)` | 令牌解析路径专用工厂：不含密码、不含角色名称（令牌里没存），`enabled` 固定 true。**注意**：这意味着"令牌有效"等价于"账号可用"，账号后来被停用不会让已签发令牌失效 |
| 48-50 | `getUserId()` | 业务主键，审计与归属判断用 |
| 52-54 | `getRealName()` | 前端顶栏显示姓名 |
| 56-58 | `getRoleCode()` | 角色编码，数据范围与 `ROLE_` 前缀权限的来源 |
| 60-62 | `getRoleName()` | 角色中文名（登录时从 `sys_role` 查出） |
| 64-66 | `getDataScope()` | 数据范围（如 `PROJECT` / `DEPT` / `ALL`），`ProjectService` 据此过滤可见项目 |
| 68-70 | `getPermissions()` | 细粒度权限码集合，`LoginResponse.UserInfo.permissions` 与前端按钮级授权用它 |
| 72-75 | `isSuperAdmin()` → `"SYSTEM_ADMIN".equals(roleCode) \|\| permissions.contains("*")` | 超级管理员双重判据：角色编码硬编码 `SYSTEM_ADMIN`（与 `db/data.sql` 初始化角色一致），或权限集合含通配符 `*`。后者让"临时给某人全部权限"无需改代码 |
| 77-82 | `hasPermission(String permission)` | 第 78-80 行先判超管（超管恒真），第 81 行做精确集合包含。**注意这里是精确匹配，不做前缀通配**（除超管的 `*`） |
| 84-94 | `getAuthorities()` | 第 87-89 行把角色编码包装成 `ROLE_ + roleCode`（配合 `hasRole`）；第 90-92 行把每条权限码原样包装（配合 `hasAuthority`）。两者合并返回，这就是"双轨"的落点 |
| 96-99 | `getPassword()` | 返回 BCrypt 哈希；令牌路径下为 null，因此令牌路径上不能用 `DaoAuthenticationProvider` 做二次认证 |
| 101-104 | `getUsername()` | 登录名 |
| 106-109 | `isAccountNonExpired()` 固定 true | 本系统不做账号有效期，故直接 true |
| 111-114 | `isAccountNonLocked()` 固定 true | **锁定状态不在令牌路径上体现**：`AuthService` 在登录时（第 87-92 行）检查 `lockedUntil` 并拒绝登录；令牌一旦签发就不再复查。固定 true 是让"锁定期间已有令牌仍可用"这一现状显式化 |
| 116-119 | `isCredentialsNonExpired()` 固定 true | 不做凭据有效期 |
| 121-124 | `isEnabled()` | 唯一真实返回状态的方法，值为构造时传入的 `enabled` |

**易错点/注意**：
1. **停用/锁定/改密不会立刻生效**：JWT 无状态，`isEnabled`/`isAccountNonLocked` 只在登录时被检查。管理员停用某账号后，该用户手上的令牌在 480 分钟内仍然有效。若业务要求即时失效，需要引入令牌黑名单或"用户状态版本号"声明。
2. `isSuperAdmin()` 里的 `"SYSTEM_ADMIN"` 是字符串硬编码，与数据库角色编码强耦合；改角色编码必须同时改这里。
3. `permissions` 集合由调用方传入且未做防御性拷贝（第 38 行只判空），`AuthService` 传的是 `permissionsOf()` 新造的 `LinkedHashSet`，因此当前安全；若将来直接传实体的集合，可能被后续代码修改。

### 2.11 `backend/src/main/java/com/ees/security/SecurityUtils.java`

**作用**：从 `SecurityContextHolder` 取当前登录用户，并提供"必须登录""必须有权限"两个断言方法。
**被谁使用**：18 个类——11 个 controller（取当前用户名做审计/归属）、`config/AuditFilter`、`AuditService`、`AuthService`、`ProjectService`、`ResultService`、`TaskService`。
**关键设计点**：
1. 返回 `Optional<LoginUser>` 的"查询式"方法与抛异常的"断言式"方法分开：`currentUser()`/`currentUsername()` 用于"匿名也能跑"的路径（如审计过滤器），`require()`/`requirePermission()` 用于"必须登录/必须授权"的业务路径。前者不抛异常是为了不干扰免登录接口。
2. `currentUsername()` 在无用户时返回字符串 `"anonymous"` 而非 null，保证审计日志的 `username` 列不会出现 null，查询与统计更省事。
3. `requirePermission` 与 `@PreAuthorize` 是**互补而非重复**：`@PreAuthorize` 在方法入口静态声明，`requirePermission` 用于权限码由运行时数据决定的动态场景（例如批量操作中逐个对象判断）。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.security;` | 安全包 |
| 3-4 | `import com.ees.common.BizException; import com.ees.common.ErrorCode;` | 断言失败改用统一异常 + 错误码（1001/2001/2003），保证全局处理器能正确映射 |
| 5-6 | `import ...Authentication; import ...SecurityContextHolder;` | 读线程本地认证上下文 |
| 8 | `import java.util.Optional;` | 查询式返回 |
| 10-12 | `/** 当前登录用户工具。 */` | 类注释 |
| 13 | `public final class SecurityUtils {` | `final` + 静态方法 |
| 15-16 | `private SecurityUtils() { }` | 私有构造，禁止实例化 |
| 18 | `public static Optional<LoginUser> currentUser()` | 查询入口，永不抛异常 |
| 19 | `Authentication auth = SecurityContextHolder.getContext().getAuthentication();` | 取当前线程的认证对象（由 `JwtAuthenticationFilter` 写入） |
| 20 | `if (auth == null \|\| !auth.isAuthenticated() \|\| !(auth.getPrincipal() instanceof LoginUser lu))` | 三重校验：上下文为空（未经过滤器）、`isAuthenticated()` 为 false（匿名占位对象）、principal 类型不是 `LoginUser`（其他认证来源）。第三项用模式匹配同时完成判断与取值 |
| 21-22 | `return Optional.empty();` | 未登录 → 空 Optional |
| 23 | `return Optional.of(lu);` | 已登录 → 带用户 |
| 26-28 | `require()` → `currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期"))` | 断言已登录；错误码 2001 会被 `GlobalExceptionHandler.handleBiz` 映射为 HTTP 401，前端拦截器据此跳登录页 |
| 30-32 | `currentUsername()` → `map(LoginUser::getUsername).orElse("anonymous")` | 审计日志用；无用户时给 `"anonymous"` 而不是 null，避免审计表 username 为空 |
| 34-36 | `currentUserId()` → `map(LoginUser::getUserId).orElse(null)` | 归属字段用；这里允许 null 是因为"系统自动执行"确实没有用户 ID |
| 38-40 | `hasPermission(String permission)` → `map(u -> u.hasPermission(permission)).orElse(false)` | 未登录即无权限（失败方向安全） |
| 42-46 | `requirePermission(String permission)` | 无权限时抛 `BizException(ErrorCode.FORBIDDEN, "无权执行该操作：" + permission)`；2003 被映射为 HTTP 403。把**缺失的权限码写进消息**，便于现场排查是角色配置问题还是代码写错权限码 |

**易错点/注意**：
1. `SecurityContextHolder` 默认是 `ThreadLocal` 策略：**在 `@Async` 异步线程、导出线程、`CompletableFuture` 回调里读不到当前用户**（会得到 `"anonymous"`）。需要用户名时应在提交异步任务前先取好并作为参数传入——这也是 `TaskService`/`ReportService` 等方法普遍显式接收 `operator` 参数的原因。
2. `currentUser()` 依赖 principal 恰为 `LoginUser`。若将来引入其他认证方式（如 API Key）而 principal 换了类型，这里会静默返回空，表现为"已认证但取不到用户"，排查时优先检查这一点。

### 2.12 `backend/src/main/java/com/ees/dto/LoginRequest.java`

**作用**：登录接口入参。用 Jakarta Validation 注解声明约束，由 `@Valid` 触发校验、`GlobalExceptionHandler.handleValidation` 输出字段级错误。
**被谁使用**：`controller/AuthController`（`@RequestBody @Valid LoginRequest`）与 `service/AuthService.login`（第 74、93 行读取 username/password）。
**关键设计点**：
1. 约束只做"形状校验"（非空、长度上限），**不做业务校验**（账号是否存在、密码是否正确、是否锁定），后者属于 `AuthService`，需查库且要记审计——这是"参数校验"与"业务校验"的分层。
2. 用 `@Size(max = ...)` 设上限是防拒绝服务：超长字符串（未设限时可达数 MB）经 BCrypt 比对会消耗大量 CPU。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.dto;` | DTO 包 |
| 3-4 | `import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;` | Jakarta Validation（Spring Boot 3 用 `jakarta.*` 而非 `javax.*`） |
| 5-6 | `import lombok.Getter; import lombok.Setter;` | Lombok 生成访问器，减少样板代码 |
| 8-9 | `@Getter @Setter` | 类级注解，作用于所有字段 |
| 10 | `public class LoginRequest {` | 无 `Serializable`：仅作 HTTP 报文载体，不落库不缓存 |
| 12-13 | `@NotBlank(message = "用户名不能为空") @Size(max = 64, message = "用户名长度不能超过 64") private String username;` | `@NotBlank` 同时挡 null、空串、纯空白；`max=64` 与 `sys_user.username` 列宽一致（64），避免"校验通过但入库截断" |
| 16-17 | `@NotBlank(message = "密码不能为空") @Size(max = 128, message = "密码长度不能超过 128") private String password;` | 密码**只校验非空与上限，不校验下限**：下限由 `ChangePasswordRequest` 在设置密码时把守，登录时若也加下限会导致历史弱密码用户彻底无法登录 |
| 20-22 | `private String captcha; private String captchaKey; private Boolean rememberMe;` | 三个字段当前**没有任何读取点**（已用全仓库检索确认）：验证码与"记住我"是预留能力，为内网离线环境保留接口兼容性。`Boolean` 用包装类型而非 `boolean`，使"未传"与"传 false"可区分 |

**易错点/注意**：`captcha`/`captchaKey`/`rememberMe` 是**未实现的预留字段**——前端传了也不会被校验，答辩时不要声称系统已启用图形验证码。登录防暴力破解实际由 `AuthService` 的失败计数 + 锁定实现（`maxLoginFail` 次后锁定 `lockMinutes` 分钟）。

### 2.13 `backend/src/main/java/com/ees/dto/LoginResponse.java`

**作用**：登录接口出参，含令牌、令牌类型、到期秒数与用户信息；用户信息用内部类 `UserInfo` 归拢，避免污染顶层。
**被谁使用**：`AuthService.login`（第 124-139 行逐字段装配）与 `controller/AuthController`（直接返回）。
**关键设计点**：
1. `tokenType` 字段默认值 `"Bearer"`（第 14 行）：前端拼 `Authorization` 头时无需写死前缀，也符合 `docs/API.md` 第 23 行的响应契约。
2. 只返回**脱敏后的用户信息**：不含密码哈希、登录失败次数、锁定时间等安全字段，只给前端渲染必需的项。
3. `menus`（菜单编码列表）与 `permissions`（权限码列表）分开：菜单决定侧栏能点哪些页面，权限码决定页面内按钮的可用性，前端路由守卫与按钮级授权各取所需。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.dto;` | DTO 包 |
| 3-4 | `import lombok.Getter; import lombok.Setter;` | Lombok 访问器 |
| 6-7 | `import java.util.List; import java.util.Set;` | `Set` 用于权限（天然去重）、`List` 用于菜单（需要顺序） |
| 9-10 | `@Getter @Setter` | 类级注解 |
| 11 | `public class LoginResponse {` | 顶层出参 |
| 13 | `private String token;` | JWT 字符串（三段式） |
| 14 | `private String tokenType = "Bearer";` | 固定 Bearer，前端拼头时直接用，避免前后端各自硬编码不一致 |
| 15 | `private long expiresIn;` | 有效期秒数，由 `JwtService.expireSeconds()` 提供；前端可据此显示"登录状态剩余时间"或提前续期 |
| 16 | `private UserInfo user;` | 用户信息聚合对象 |
| 18-19 | `@Getter @Setter`（内部类上） | 内部类需要自己的 Lombok 注解，类级注解不会自动下沉 |
| 20 | `public static class UserInfo {` | `static` 内部类：不隐含外部实例引用，Jackson 可独立序列化 |
| 21 | `private Long id;` | 用户主键，前端用于归属展示与后续接口参数 |
| 22-23 | `private String username;` / `realName;` | 登录名与真实姓名 |
| 24-25 | `private String roleCode;` / `roleName;` | 角色编码（前端做条件渲染时比中文名稳定）与中文名（直接展示） |
| 26 | `private String orgName;` | 所属单位，顶栏与报表落款用 |
| 27 | `private String dataScope;` | 数据范围，前端可按此隐藏"全部项目"筛选项，与后端 `ProjectService` 的过滤保持一致 |
| 28 | `private Set<String> permissions;` | 权限码集合，前端 `v-permission` 类指令/按钮显隐依据 |
| 29 | `private List<String> menus;` | 菜单编码有序列表，侧栏与路由守卫依据 |
| 30 | `private String lastLoginAt;` | 上次登录时间，**格式化为字符串**（`AuthService` 第 137 行用 `LocalDateTime.format(DTF)`）而非 `LocalDateTime` 对象，避免前端额外处理时区与格式 |

**易错点/注意**：`lastLoginAt` 是已格式化的字符串（形如 `2026-09-22 10:00:00`），与项目其他接口返回 ISO 时间的情况可能不一致；前端若统一按日期库解析要注意兼容。

### 2.14 `backend/src/main/java/com/ees/dto/UserSaveRequest.java`

**作用**：用户新增与修改共用的入参。靠 `id` 是否为空区分"新增/修改"，靠 `password` 是否为空区分"是否改密"。
**被谁使用**：`controller/UserController`（新增/修改两个接口都用它）与 `service/AuthService.saveUser`（第 167-203 行）。
**关键设计点**：
1. **一个 DTO 服务两种语义**：新增和修改的字段几乎相同，合并可避免两份几乎一样的校验注解产生漂移。代价是"新增必填密码、修改可留空"这类条件必填无法用注解表达，只能放到 service（`AuthService` 第 182-184 行显式判空抛 `PARAM_INVALID`）——这是有意为之：注解负责形状，service 负责语境。
2. `password` 不写 `@NotBlank`（见上一条）、`status` 给默认值 `"ENABLED"`，都体现"字段可缺省"的设计。
3. `@Size(max = ...)` 的上限逐字段与数据库列宽对齐（username 64、password 128、realName 64、orgName 128、email 128、phone 32、remark 500），从源头杜绝"校验通过但入库报 `Data too long`"。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.dto;` | DTO 包 |
| 3-6 | `import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size; import lombok.Getter; import lombok.Setter;` | 与 `LoginRequest` 相同的依赖 |
| 8-9 | `@Getter @Setter` | 类级访问器 |
| 10 | `public class UserSaveRequest {` | 入参对象 |
| 12 | `private Long id;` | 主键：非空=修改，为空=新增（`AuthService` 第 169 行即以此分支）。用包装类型 `Long` 才能表达"未传" |
| 14-15 | `@NotBlank(message = "用户名不能为空") @Size(max = 64) private String username;` | 用户名两种场景都必填；`@Size` 未写 message，失败时使用框架默认英文提示（见易错点） |
| 18 | `/** 新增时必填；修改时留空表示不修改密码 */` | 文档化条件必填语义，读代码的人一眼能懂 |
| 19-20 | `@Size(max = 128) private String password;` | 明文密码入参，由 `AuthService` 用 `BCryptPasswordEncoder` 加密后落库（第 191 行），**数据库里不存明文** |
| 22-23 | `@Size(max = 64) private String realName;` | 真实姓名，可空 |
| 25-26 | `@NotBlank(message = "角色不能为空") private String roleCode;` | 角色必填；`AuthService` 第 186-187 行还会校验角色在 `sys_role` 中真实存在（注解无法查库） |
| 28-29 | `@Size(max = 128) private String orgName;` | 所属单位 |
| 31-32 | `@Size(max = 128) private String email;` | 邮箱。**只用 `@Size` 不用 `@Email`**：内网环境存在非标准邮箱格式的账号，避免误拦 |
| 34-35 | `@Size(max = 32) private String phone;` | 联系电话 |
| 37 | `private String status = "ENABLED";` | 默认启用；`AuthService` 第 198 行还做了 `null` 兜底（`request.getStatus() == null ? "ENABLED" : ...`），双重保险 |
| 39-40 | `@Size(max = 500) private String remark;` | 备注 |

**易错点/注意**：`roleCode` 的 `@NotBlank` 有中文 message，而 `username` 的 `@Size(max = 64)` 没有——长度超限时前端会显示框架默认英文提示（如 `size must be between 0 and 64`），与系统全中文提示风格不一致。这是当前实现的实际状态，属于待优化项。

### 2.15 `backend/src/main/java/com/ees/dto/ChangePasswordRequest.java`

**作用**：修改密码入参，只有原密码与新密码两个字段。
**被谁使用**：`controller/AuthController`（`POST /api/auth/change-password`）与 `service/AuthService.changePassword`（第 153-164 行）。
**关键设计点**：
1. `newPassword` 用 `@Size(min = 6, max = 64)` 表达**密码强度下限**：登录请求不能加下限（否则历史弱密码用户无法登录），但"设置新密码"必须加，否则弱口令会持续产生。这条约束放在 DTO 而非 service，是因为它不依赖任何运行时数据。
2. 只有两个字段、无 `id`：改密对象永远是"当前登录用户"，由 `AuthController` 从 `SecurityUtils` 取用户名后传给 service（第 153 行签名 `changePassword(String username, ChangePasswordRequest request, ...)`），**不接受前端指定用户 ID**，从设计上杜绝越权改他人密码。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.dto;` | DTO 包 |
| 3-6 | `import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size; import lombok.Getter; import lombok.Setter;` | 校验注解 + Lombok |
| 8-9 | `@Getter @Setter` | 类级访问器 |
| 10 | `public class ChangePasswordRequest {` | 入参对象 |
| 12-13 | `@NotBlank(message = "原密码不能为空") private String oldPassword;` | 原密码必填，仅校验非空（不设长度上限：原密码应由历史校验逻辑判断，且长度上限在登录/建号时已约束过） |
| 15-16 | `@NotBlank(message = "新密码不能为空") @Size(min = 6, max = 64, message = "新密码长度需在 6 到 64 之间") private String newPassword;` | 新密码必填且 6~64 位。**这里没有"新密码不得与原密码相同"的校验**，代码中也不存在该判断（`AuthService` 第 156-161 行只比对原密码、再加密保存），属当前实现的空白点 |

**易错点/注意**：项目内密码长度上限不统一——`LoginRequest.password` 与 `UserSaveRequest.password` 为 `max = 128`，而 `ChangePasswordRequest.newPassword` 为 `max = 64`。若用户通过"用户管理"设了 100 位密码，之后走自助改密接口也会通过（上限更严的是新密码），但自助改密无法把密码再设回 100 位。使用上无碍，交接时应知晓这是历史遗留的不一致。

### 2.16 `backend/src/main/java/com/ees/util/TabularReader.java`

**作用**：把 XLSX / XLS / CSV 三种表格文件统一读成"表头 + 行 Map + 原始行号"的中性结构，并提供数值与日期的容错解析。对应方案 5.1 文件导入方式。
**被谁使用**：
- `IndicatorService.importIndicators`（第 141 行 `TabularReader.read(in, fileName)`；第 198 行 `parseNumber` 读权重；第 539 行 `parseNumber` 解析评分规则阈值）
- `DataBatchService.importFile`（第 137 行 `read`；第 302 行 `parseDateTime` 解析数据时间；第 334 行 `parseNumber` 解析指标值）
- `TableData` 还被**手工构造**用作中性容器：`DataBatchService` 第 213 行（数据库抽取结果复用同一套校验流程）与第 497 行（模板预览）。也就是说 `TableData` 是"文件行"与"库行"共同的表达方式，这是本类真正的价值所在。
**关键设计点**：
1. **读取粒度是"整文件一次读入 + 全量返回"**，不做流式游标：数据批次导入需要先知道总行数、逐行生成质量报告与错误明细、再决定整批是否通过，流式反而让"行号 ↔ 错误"的对应更难维护。代价是内存占用（见易错点 2）。
2. **不抛异常式的取值 API**：`TableData.get(row, "indicator_code", "指标编码")` 支持多候选列名、大小写不敏感、跳过空白值。这一层"别名解析"让业务代码同时兼容英文列名文件与中文表头文件，也让模板里的"中文说明行"不至于破坏解析。
3. **解析失败返回 null 而不是抛异常**：脏数据是"用户输入的一部分"，必须能继续处理其余有效行并生成行级错误明细（`DataBatchService.validate` 第 334-341 行正是靠 `parseNumber` 返回 null 来标记"数值格式非法"并给出修正建议）。这与 `CryptoUtils`/`JsonUtils` 关键路径直接抛异常形成对照——**面向用户数据要容错，面向系统状态要快速失败**。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.util;` | 工具包 |
| 3-4 | `import com.ees.common.BizException; import com.ees.common.ErrorCode;` | 结构性失败（文件类型不支持、解析失败）抛统一异常，映射到 3002/3004 |
| 5 | `import lombok.Getter;` | 给内部类 `TableData` 生成访问器 |
| 6-8 | `import org.apache.commons.csv.CSVFormat / CSVParser / CSVRecord;` | Commons CSV 解析器（处理引号包裹、逗号转义等 RFC 4180 细节，不用手工 split） |
| 9-16 | `import org.apache.poi.ss.usermodel...Cell / CellType / DataFormatter / DateUtil / Row / Sheet / Workbook / WorkbookFactory;` | POI 通用接口层（`ss.usermodel` 对 HSSF/XSSF 统一），`WorkbookFactory` 负责按文件魔数自动选实现 |
| 18-23 | `import java.io.BufferedReader / IOException / InputStream / InputStreamReader / Charset / StandardCharsets;` | 字节流读取与字符集（注意 `InputStreamReader` 被导入但 CSV 路径实际用 `StringReader`，见易错点 4） |
| 24-26 | `import java.time.LocalDate / LocalDateTime / DateTimeFormatter;` | 日期解析（Java 8 时间 API，线程安全） |
| 27-32 | `import java.util.ArrayList / LinkedHashMap / LinkedHashSet / List / Map / Set;` | 保序集合：`LinkedHashMap` 保持列顺序便于前端展示，`LinkedHashSet` 用于表头去重且保序 |
| 34-36 | `/** 表格文件读取（XLSX / XLS / CSV）。对应方案 5.1 文件导入方式。 */` | 标注与方案的对应关系 |
| 37 | `public final class TabularReader {` | 静态工具类 |
| 39 | `private static final DataFormatter FORMATTER = new DataFormatter();` | 复用一个格式化器：按单元格显示格式（如 `0.00`、日期格式）产出文本，而不是拿到 POI 的原始 double。静态共享实例在多线程下需复核（见易错点 5） |
| 40-47 | `private static final DateTimeFormatter[] DATE_FORMATS = { "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMddHHmmss", "yyyyMMdd" };` | 六种日期格式按"信息量从多到少"排列：先试带时分的，再试只有日期的，最后是紧凑无分隔符的。覆盖"Excel 单元格文本""系统导出""手工录入"三类常见来源 |
| 49-50 | `private TabularReader() { }` | 私有构造 |
| 52-53 | `/** 读取结果。 */ @Getter` | 内部类由 Lombok 生成 `getHeaders/getRows/getRowNumbers/getSheetName` |
| 54 | `public static class TableData {` | 静态内部类，作为"文件/库行"的统一容器 |
| 55 | `private final List<String> headers = new ArrayList<>();` | 有序表头（已去重、已补空列名） |
| 56 | `private final List<Map<String, String>> rows = new ArrayList<>();` | 每行一个"列名 → 文本值"映射，值已 `trim`，缺失为 null |
| 57-58 | `/** 每行对应的原始行号（1 基，含表头） */ private final List<Integer> rowNumbers = new ArrayList<>();` | **错误明细可定位的关键**：与 `rows` 同下标，直接给出用户在 Excel 里看到的行号 |
| 59 | `private String sheetName;` | 记录读取的工作表名，落库到导入批次便于追溯 |
| 61-63 | `public int size()` | 有效数据行数（不含表头、不含全空行） |
| 65-72 | `public boolean hasColumn(String name)` | 大小写不敏感地判断某列是否存在，供业务做"该可选列是否有值"的前置判断 |
| 74-86 | `public String get(Map<String, String> row, String... candidates)` | **别名取值**：按候选名顺序遍历，对每个候选再全行做大小写不敏感匹配（第 75-77 行），命中且值非空白时 `trim` 返回（第 78-81 行），否则继续下一个候选；全部落空返回 null。有了它，`DataBatchService` 第 289-297 行才能写成 `data.get(row, "indicator_code", "指标编码")`，同一份代码同时吃下英文与中文表头 |
| 89-99 | `public static TableData read(InputStream in, String fileName)` | **入口分派**：第 90 行文件名转小写（null 安全）；第 91-93 行 `.xlsx`/`.xls` → `readExcel`；第 94-96 行 `.csv` → `readCsv`；第 97-98 行其余一律抛 `BizException(FILE_TYPE_NOT_ALLOWED, "暂不支持的文件类型：...")`，消息里带上文件名与原文件名，前端可直接展示。注意分派**只看扩展名、不看魔术字节**，所以把 xlsx 改名成 .csv 会走进 CSV 分支并解析失败 |
| 101-103 | `public static TableData readExcel(InputStream in)` + `try (Workbook wb = WorkbookFactory.create(in))` | try-with-resources 保证工作簿关闭（释放 POI 的临时文件/内存）；`WorkbookFactory` 按魔数自动识别 `.xls`(HSSF) 与 `.xlsx`(XSSF)，所以扩展名写错成 `.xls` 的真 xlsx 也能读出来 |
| 104-106 | `if (wb.getNumberOfSheets() == 0) throw new BizException(FILE_PARSE_FAILED, "Excel 文件不包含任何工作表");` | 空工作簿防护，避免后续 `getSheetAt(0)` 抛 `IllegalArgumentException` 并落到"解析失败"的笼统提示 |
| 107-108 | `Sheet sheet = wb.getSheetAt(0); data.sheetName = sheet.getSheetName();` | **只读第一个工作表**（数据导入约定：模板的第一个 sheet 是数据 sheet；`dataTemplate` 生成时也是这样安排的）。记录表名用于落库追溯 |
| 109-113 | `int headerRowIdx = findHeaderRow(sheet); Row headerRow = sheet.getRow(headerRowIdx); if (headerRow == null) throw new BizException(FILE_PARSE_FAILED, "未找到表头行");` | 探测表头行并做 null 兜底 |
| 114 | `int lastCol = headerRow.getLastCellNum();` | 以**表头行**的最后一列为准确定列数；`getLastCellNum()` 返回"最后单元格下标 + 1"，即列数 |
| 115-127 | `Set<String> seen = new LinkedHashSet<>(); for (int c = 0; c < lastCol; c++) { String h = cellString(...); if (h == null \|\| h.isBlank()) h = "COL" + (c + 1); h = h.trim(); if (seen.contains(h)) h = h + "_" + (c + 1); seen.add(h); data.headers.add(h); }` | 表头规范化三件事：① 空列名补成 `COL1`、`COL2`…，保证每一列都有键、业务可按位置定位；② 重名列追加 `_列号` 后缀（如 `备注_7`），避免 `LinkedHashMap` 的键冲突导致整列被后面的列覆盖；③ `LinkedHashSet` 去重同时保持列顺序 |
| 128 | `for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++)` | 从表头下一行遍历到最后一物理行 |
| 129-132 | `Row row = sheet.getRow(r); if (row == null) continue;` | POI 对完全空行会返回 null，跳过（也顺带跳过 Excel 中部的大段空白） |
| 133-141 | `Map<String, String> map = new LinkedHashMap<>(); boolean empty = true; for (int c = 0; c < data.headers.size(); c++) { String v = cellString(row.getCell(c)); if (v != null && !v.isBlank()) empty = false; map.put(data.headers.get(c), v); }` | 逐列取值并放入保序 Map；以"本行是否有任一非空单元格"标记 `empty`。**列数按表头列数循环**，因此数据行比表头多出的列被忽略、少列的位置补 null |
| 142-146 | `if (empty) continue; data.rows.add(map); data.rowNumbers.add(r + 1);` | 全空行不计入 `rows`（否则会把模板末尾的空白行当成脏数据）；有效行记录**物理行号 r+1**（POI 行号 0 基，+1 换算成 Excel 里看到的行号，与第 57 行注释"1 基，含表头"一致） |
| 148-149 | `catch (BizException e) { throw e; }` | **先捕获自己的业务异常原样抛出**：否则它会被下面的 `catch (Exception)` 改写成"Excel 解析失败：Excel 文件不包含任何工作表"，把精确错误码 3004 的语义污染成双层消息 |
| 150-152 | `catch (Exception e) { throw new BizException(ErrorCode.FILE_PARSE_FAILED, "Excel 解析失败：" + e.getMessage(), e); }` | 其余异常（加密工作簿、损坏文件、POI 版本不支持等）统一包装为 3004 并保留 cause 供 `rootMessage` 追溯 |
| 156 | `/** 在前 10 行内寻找表头行（含"指标"或列名最多的行）。 */` | 注释描述了两种策略，**实际只实现了第二种**（见易错点 1） |
| 157-160 | `int best = sheet.getFirstRowNum(); int bestScore = -1; int limit = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 9);` | 候选区间为"首个物理行起的 10 行"；`Math.min` 防止行数不足 10 时越界。`bestScore = -1` 保证第一行一定会被采纳为初值 |
| 161-165 | `for (int r = ...; r <= limit; r++) { Row row = sheet.getRow(r); if (row == null) continue;` | 逐行扫描，空行跳过 |
| 166-172 | `int nonEmpty = 0; for (int c = 0; c < row.getLastCellNum(); c++) { String v = cellString(row.getCell(c)); if (v != null && !v.isBlank()) nonEmpty++; }` | 统计该行非空单元格个数作为"像表头"的评分 |
| 173-176 | `if (nonEmpty > bestScore) { bestScore = nonEmpty; best = r; }` | **严格大于**决定平手时保留更靠前的行。这一条是模板能工作的关键：模板第 1 行是英文列名（如 15 列全非空），第 2 行是中文说明（也是 15 列全非空），分数相同 → 选中第 1 行英文列名。用户随后导入时，业务代码用 `data.get(row, "weight", "权重")` 之类的别名取值，因此"选中哪一行"都不会丢数据 |
| 178 | `return best;` | 返回探测到的表头行下标 |
| 181-183 | `public static TableData readCsv(InputStream in)` | CSV 分支（不走 POI） |
| 184-188 | `byte[] bytes; try { bytes = in.readAllBytes(); } catch (IOException e) { throw new BizException(FILE_PARSE_FAILED, "读取 CSV 失败：" + e.getMessage(), e); }` | **一次性读入全部字节**：必须先拿到全部字节才能判断编码（见 `decode`），因此无法边读边解析。内存代价见易错点 2 |
| 189 | `String text = decode(bytes);` | 编码自适应（重点见下） |
| 190-197 | `try (BufferedReader reader = new BufferedReader(new StringReader(text)); CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).build().parse(reader))` | 解析器配置逐项含义：`setHeader()` 无参 = 用**文件第一行**作表头名；`setSkipHeaderRecord(true)` = 表头行不算数据；`setIgnoreEmptyLines(true)` = 跳过空行；`setTrim(true)` = 去除每个字段首尾空白；`DEFAULT` 是 RFC 4180（逗号分隔、双引号包裹、`""` 转义）。两个资源都在 try-with-resources 内，异常路径也会关闭 |
| 198 | `data.headers.addAll(parser.getHeaderNames());` | 直接采用解析器给出的表头名。**与 Excel 路径不同，这里没有"空列名补 COLn""重名加后缀"的兜底**（见易错点 3） |
| 199-206 | `for (CSVRecord rec : parser) { Map<String, String> map = new LinkedHashMap<>(); for (String h : data.headers) { map.put(h, rec.isMapped(h) ? rec.get(h) : null); } data.rows.add(map); data.rowNumbers.add((int) rec.getRecordNumber() + 1); }` | 逐条记录构造与 Excel 一致的 `Map`；`rec.isMapped(h)` 防止列数不足时 `rec.get` 抛异常；行号取 `getRecordNumber() + 1`（记录号 1 基，+1 得到"文件第几行"，假设表头占第 1 行）。**CSV 路径没有"全空行跳过"判断**（因为 `setIgnoreEmptyLines` 已在解析层过滤） |
| 207-209 | `catch (Exception e) { throw new BizException(FILE_PARSE_FAILED, "CSV 解析失败：" + e.getMessage(), e); }` | 统一包装为 3004 |
| 213 | `/** 自动识别 UTF-8 / GBK 编码，避免中文乱码。 */` | 方法意图 |
| 214-217 | `if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) return new String(bytes, 3, bytes.length - 3, UTF_8);` | 识别 **UTF-8 BOM**（EF BB BF）并跳过这 3 个字节：若不跳过，BOM 会粘在第一个表头名上（变成 `\uFEFFindicator_code`），导致按列名取值全部取不到。`& 0xFF` 是为把 byte 转成无符号整数再比较 |
| 218-221 | `String utf8 = new String(bytes, UTF_8); if (!utf8.contains("\uFFFD")) return utf8;` | **先按 UTF-8 试解，用"是否出现替换字符 U+FFFD"作为合法性判据**：Java 的 UTF-8 解码器遇到非法字节序列会产出 `\uFFFD`，因此"没有 `\uFFFD`"意味着"这段字节是合法 UTF-8"，直接采用 |
| 222-226 | `try { return new String(bytes, Charset.forName("GBK")); } catch (Exception e) { return utf8; }` | **回退到 GBK**：中文 Windows 环境下 Excel"另存为 CSV"默认使用 ANSI/GBK 编码，其字节序列在 UTF-8 下几乎必然非法（GBK 汉字两字节都在 0x81-0xFE 区间，作为 UTF-8 前导字节时后续字节常不满足续字节格式），因而上一步会产出 `\uFFFD` 并走到这里。若运行环境没有 GBK 字符集（极少数精简 JRE），`Charset.forName` 抛异常则退回已经乱码的 UTF-8 结果，至少不中断流程 |
| 229-239 | `private static String cellString(Cell cell)` | 单元格 → 文本的统一转换，三个分支：第 230-232 行 null 单元格返回 null；第 233-236 行"数值型且被格式化为日期"时用 `getLocalDateTimeCellValue()` 并按 `yyyy-MM-dd HH:mm:ss` 输出（**日期必须是文本**，否则下游 `parseDateTime` 拿到的是 45678 这种序列号）；第 237-238 行其余情况交给 `DataFormatter.formatCellValue` 产出"用户在 Excel 里看到的样子"，再 `trim` |
| 241-252 | `public static Double parseNumber(String s)` | 数值解析：第 242-244 行空值返回 null；第 246 行**去掉英文逗号与中文全角逗号**再解析（处理"1,234.5"与中文输入法打出的"，"）；第 248 行 `Double.parseDouble`；第 249-251 行失败返回 null。方法注释"失败返回 null 并输出原因"——实际**只返回 null，不输出原因**，原因由调用方（如 `DataBatchService` 第 337-339 行）拼进错误明细，注释措辞不够准确 |
| 254-258 | `public static LocalDateTime parseDateTime(String s)` | 日期解析入口：第 255-257 行空值返回 null；第 258 行 `trim` |
| 259-268 | `for (DateTimeFormatter f : DATE_FORMATS) { try { if (f.toString().contains("HH")) return LocalDateTime.parse(t, f); return LocalDate.parse(t, f).atStartOfDay(); } catch (Exception ignored) { } }` | **多格式兜底**：逐个尝试 6 种格式，靠 `f.toString().contains("HH")` 区分"含时分的格式"（走 `LocalDateTime.parse`）与"纯日期格式"（走 `LocalDate.parse(...).atStartOfDay()`，补 00:00:00 使返回类型统一为 `LocalDateTime`）。全部失败才继续往下走。第 265-267 行的空 catch 是刻意的：这里"失败"是正常控制流 |
| 269-273 | `Double num = parseNumber(t); if (num != null && num > 20000 && num < 80000) { return LocalDate.of(1899, 12, 30).plusDays(num.longValue()).atStartOfDay(); }` | **Excel 日期序列号转换，基准为 1899-12-30**。这个基准的理由：Excel 沿用 Lotus 1-2-3 的历史缺陷，把 1900 年当成闰年（序列号 60 对应并不存在的 1900-02-29），为了让 1900-03-01 之后的日期全部正确，必须使用 1899-12-30 而不是 1899-12-31——若用后者，所有日期都会早一天。区间 `(20000, 80000)` 约对应 **1954-10-03 ~ 2119-01-11**，即只对"合理的现代日期"生效；否则像 `2026`（想表达年份）、`88.5`（想表达百分比）这类纯数字会被错误解释成千年以前的日期。**该分支只在 6 种文本格式全部失败后才走到**，属于最后的兜底 |
| 274 | `return null;` | 全部手段失败 → 无日期，由业务层按缺失值策略处理 |

**四种读取路径与容错策略对比**（便于快速定位"某种文件为什么读不出来"）：

| 路径 | 实现 | 表头来源 | 列名兜底 | 行号来源 |
|---|---|---|---|---|
| `.xlsx` | POI XSSF（`WorkbookFactory` 自动识别） | `findHeaderRow` 在前 10 行中选非空单元格最多者 | 空列名 → `COL{n}`；重名 → 追加 `_{列号}` | POI 物理行号 `r + 1` |
| `.xls` | POI HSSF（同上） | 同上 | 同上 | 同上 |
| `.csv` | Commons CSV | 文件第一行（`setHeader()`） | 无 | `CSVRecord.getRecordNumber() + 1` |
| 其他扩展名 | 直接拒绝 | — | — | 抛 `3002 FILE_TYPE_NOT_ALLOWED` |

> 另有一条"非文件"路径：`DataBatchService` 用手工构造的 `TableData`（第 213、497 行）承载数据库抽取结果与模板预览数据，因此下游校验逻辑对"文件来源"和"库来源"完全一致。

**易错点/注意**：
1. **注释与实现不一致**：第 156 行的注释声称表头探测"含'指标'"，但 `findHeaderRow` 的实现**只统计非空单元格数**，没有任何关键字匹配逻辑。若某文件的表头行不是前 10 行里非空单元格最多的一行（例如表头稀疏、而某个数据行被填满），探测就会选错。生产导入前建议先看模板首两行结构。
2. **CSV 全量载入内存**：第 185 行 `in.readAllBytes()` + 第 218 行 `new String(bytes, UTF_8)`，而 `application.yml` 第 32-33 行允许 200MB 上传。一个 200MB 的 CSV 会同时占用约 200MB 字节数组与最多约 400MB 的 `String`（UTF-16），存在 OOM 风险。Excel 路径由 POI 的流式/临时文件机制缓解，但大 XLSX 同样占用可观内存。大批量导入前应评估 JVM 堆设置。
3. **CSV 表头无兜底**：`readCsv` 直接采用解析器表头名，未做空名补位与重名去重。若 CSV 首行有空列名，该列在 `Map` 中的键为空串，`TableData.get` 的候选取值基本取不到它；若首行有同名列，行为取决于 Commons CSV 对重复表头的处理（本册未逐行验证解析器内部实现，遇到该情况应以实际导入结果为准）。而 Excel 路径不存在这两个问题。
4. **行号语义在两种路径下不完全等价**：CSV 的 `getRecordNumber()` 只统计有效记录，若文件中存在空行（`setIgnoreEmptyLines(true)` 会跳过且不计数），错误明细里的行号会与物理行号产生偏移；Excel 路径用的是物理行号，不存在该偏差。用户拿错误明细去定位 CSV 行时需注意。
5. 第 20 行导入了 `InputStreamReader` 但 CSV 路径实际使用 `StringReader`（因为已完成解码），属未使用的导入项，不影响功能。
6. `FORMATTER` 是静态共享的 `DataFormatter` 实例，POI 未承诺其线程安全。当前导入是同步串行调用，并发导入（多用户同时上传）时应复核是否出现格式串扰。
7. `parseNumber` 只剥离千分位逗号，**不做百分号换算**：Excel 中显示为"50%"的单元格经 `DataFormatter` 会产出字符串 `50%`，`Double.parseDouble` 失败 → 返回 null → 被判定为 3007 数值格式非法。使用数据模板时"指标值"应按纯数值填写（如 `0.5` 而非 `50%`），这一点在模板的中文说明行里没有写明。
8. `parseDateTime` 对"只有时间没有日期"的输入（如 `10:30:00`）无法解析，返回 null，会被当作缺失值处理。

### 2.17 `backend/src/main/java/com/ees/util/ExcelTemplateWriter.java`

**作用**：用 POI 生成三种 xlsx 内容——① 指标体系导入模板、② 标准试验数据导入模板（附录 A）、③ 错误明细导出。
**被谁使用**：
- `controller/IndicatorController` 第 132 行 → `indicatorTemplate()`，对应 `GET /api/indicator-versions/template`
- `controller/DataBatchController` 第 102 行 → `dataTemplate()`，对应 `GET /api/data-batches/template`
- `controller/DataBatchController` 第 126 行 → `errorDetail(rows, headers)`，对应 `GET /api/data-batches/{id}/errors/export`
**关键设计点**：
1. **两个模板分别对应方案的哪里**：
   - `dataTemplate()`：方法注释明确写"标准试验数据导入模板（**附录 A**）"，其列定义取自 `DataBatchService.TEMPLATE_COLUMNS`（该常量第 51 行注释同样标注"标准字段（附录 A）"）。因此**数据模板对应方案附录 A 的标准试验数据字段结构**，这是本文件中唯一有明确附录标注的模板。
   - `indicatorTemplate()`：指标体系导入模板。列定义取自 `IndicatorService.TEMPLATE_COLUMNS`（15 列，注释为"与 `samples/指标模板` 一致"）。**代码中没有为它标注附录编号**（全仓库检索"附录"仅命中本文件第 69 行、`DataBatchService` 第 51 行、以及算法 I/O 的附录 B.1/B.2），因此它的方案章节归属未能从代码确认，引用时请以方案原文为准，不要臆断编号。
2. **错误明细导出的用途**：把导入校验产生的行级错误（行号、对象编码、指标编码、指标值、错误说明）导出为 xlsx 供业务人员离线修正后重新导入。对应验收项 **1.4.4"错误明细可导出"**（下载 xlsx，含行号、对象、指标、值、错误说明）与 **1.3.4"错误可定位"**（错误明细含行号与字段说明）。数据来源是 `DataBatchService.errorRows(id)`，导出文件名带批次号（`错误明细_<batchCode>.xlsx`），便于同一批次多次导出后区分版本。
3. **模板生成方式选"运行时代码生成"而不是"打包静态 xlsx 文件"**：列定义直接引用 service 里的 `TEMPLATE_COLUMNS` 常量，**列结构与导入解析逻辑同源**——改列定义只改一处，模板与解析不会漂移。这是本类唯一反向依赖 service 层的原因，也是值得保留的取舍。
4. 模板第 2 行放"中文说明行"，第 3 行起放示例数据：说明行让业务人员知道每列填什么，示例数据（指标模板三层结构、数据模板三个方案对象）让格式一看就懂。之所以不会污染导入，是因为 `TabularReader.findHeaderRow` 采用"非空单元格最多 + 平手取靠前"的策略，会选中第 1 行英文列名（见 2.16 关键设计点与第 173-176 行说明）。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.util;` | 工具包 |
| 3-4 | `import com.ees.service.DataBatchService; import com.ees.service.IndicatorService;` | **唯一的反向依赖**：仅为复用两个 `TEMPLATE_COLUMNS` 常量，保证模板列与导入解析列同源 |
| 5-12 | `import org.apache.poi.ss.usermodel...CellStyle / FillPatternType / Font / IndexedColors / Row / Sheet / Workbook; import org.apache.poi.xssf.usermodel.XSSFWorkbook;` | 通用样式接口 + **只用 XSSF**（生成 `.xlsx`，不生成老式 `.xls`） |
| 14-17 | `import java.io.ByteArrayOutputStream / IOException; java.util.List / Map;` | 内存中生成字节数组直接返回给 HTTP 响应，不落盘 |
| 19-21 | `/** Excel 模板与错误明细生成（支持模板下载与错误明细下载）。 */` | 类注释概括三种用途 |
| 22 | `public final class ExcelTemplateWriter {` | 静态工具类 |
| 24-25 | `private ExcelTemplateWriter() { }` | 私有构造 |
| 27-28 | `/** 指标体系导入模板。 */ public static byte[] indicatorTemplate()` | 指标体系模板入口 |
| 29-30 | `try (Workbook wb = new XSSFWorkbook()) { Sheet sheet = wb.createSheet("指标体系");` | 新建工作簿与名为"指标体系"的首个工作表（**必须是第一个 sheet**，因为 `TabularReader` 只读第 0 个） |
| 31 | `List<String> cols = IndicatorService.TEMPLATE_COLUMNS;` | 取 15 个英文列名，与 `IndicatorService.importIndicators` 的解析列同源 |
| 32-37 | `String[] comments = { "指标编码（唯一，主关联键）", ..., "备注" };` | 15 条中文说明，逐列解释含义与取值域。例如第 6 条明确写出方向取值 `POSITIVE / NEGATIVE / INTERVAL`（对应 `ErrorCode.DIRECTION_UNKNOWN` 的业务背景）、第 7 条写明"权重（0~1，同层之和建议为 1）" |
| 38 | `CellStyle head = headerStyle(wb);` | 表头样式（加粗 + 灰底） |
| 39-40 | `Row h = sheet.createRow(0); Row c = sheet.createRow(1);` | 第 1 行英文列名、第 2 行中文说明 |
| 41-46 | `for (int i = 0; i < cols.size(); i++) { h.createCell(i).setCellValue(cols.get(i)); h.getCell(i).setCellStyle(head); c.createCell(i).setCellValue(comments[i]); sheet.setColumnWidth(i, i == 1 ? 6000 : 4200); }` | 逐列写列名与说明并套样式；第 45 行给"指标名称"列更宽（6000），因为中文名称最长 |
| 47 | `// 示例数据（三层结构）` | 注释说明示例覆盖"一级指标 → 二级指标 → 三级指标" |
| 48-55 | `String[][] demo = { {"A", "综合效能", "", "1", ...}, {"A1", "作战能力", "A", "2", ...}, ... }` | 6 行示例，**演示关键约定**：顶层指标 `parent_code` 留空；`level` 从 1 递增；`weight` 同级之和为 1（0.6 + 0.4）；`direction` 用 `POSITIVE`（命中精度等越大越好）与 `NEGATIVE`（反应时间越小越好）；负向指标同时给出 `min_value=0`、`max_value=60` 作为标准化上下界。这些约定正是后续标准化算法的输入前提 |
| 56-62 | `int r = 2; for (String[] row : demo) { Row rr = sheet.createRow(r++); for (int i = 0; i < row.length; i++) rr.createCell(i).setCellValue(row[i]); }` | 从第 3 行（下标 2）开始写示例数据；`row.length` 即该行实际填的列数，未填的列为空 |
| 63-66 | `return toBytes(wb); } catch (IOException e) { throw new IllegalStateException("生成指标体系模板失败", e); }` | 转字节返回；模板生成失败属于程序缺陷（不依赖用户输入），故抛 `IllegalStateException` 而非 `BizException`——它会走 `GlobalExceptionHandler` 的兜底分支成为 500，语义正确 |
| 69 | `/** 标准试验数据导入模板（附录 A）。 */` | **本文件中唯一明确标注附录的地方：数据模板对应方案附录 A** |
| 70-73 | `public static byte[] dataTemplate()` + `Workbook wb = new XSSFWorkbook()` + `wb.createSheet("试验数据")` + `List<String> cols = DataBatchService.TEMPLATE_COLUMNS;` | 数据模板入口，9 个标准字段（`project_code`、`object_code`、`object_name`、`data_time`、`indicator_code`、`indicator_value`、`unit`、`source_batch`、`remark`）取自 `DataBatchService`，与第 289-297 行的别名解析一一对应 |
| 74-79 | `String[] comments = { "项目或试验项目编码", ..., "补充说明" };` | 9 条中文说明。第 4 条给出时间格式示例 `2026-09-22 10:00:00`（正好命中 `TabularReader.DATE_FORMATS` 的第一种），第 5 条强调"与指标体系版本中的指标编码对应"——这是导入能否通过 `INDICATOR_NOT_COVERED(3005)` 校验的关键 |
| 80-88 | 表头行 + 说明行 + 样式 + 列宽（第 87 行给指标编码列 4600） | 与指标模板同构；指标编码列更宽因为编码较长 |
| 89-99 | `String[][] demo = { {"PRJ-2026-001", "OBJ-001", "方案一", ...}, ... }` | 9 行示例，覆盖 **3 个被评估对象 × 3 个指标** 的完整矩阵，且三个对象在"反应时间"（负向，越小越好）与"命中精度"（正向，越大越好）上取值相反（18.5/92.4、25.0/85.0、12.8/97.1），用于演示综合评判会得出有区分度的排序结果。示例数据即 `docs/USER_GUIDE.md` 演示流程可复现的输入 |
| 100-106 | `int r = 2; for (String[] row : demo) { ... }` | 从第 3 行写示例数据 |
| 107-108 | `// 字段说明页` + `Sheet desc = wb.createSheet("字段说明");` | **第二个 sheet** 放字段字典。因为 `TabularReader` 只读第 0 个 sheet，这个说明页不会被误当作数据导入 |
| 109-120 | `String[][] fields = { {"字段", "必填", "类型", "说明"}, {"project_code", "是", "文本", ...}, ... }` | 10 行字段字典：字段名、是否必填、类型、说明。**"必填"列直接对应 `DataBatchService.validate` 的必填校验**：`project_code`/`object_code`/`indicator_code`/`indicator_value` 标"是"，与第 310-332 行的校验分支一致（其中 `project_code` 在实际代码中还允许由上下文补齐，模板标"是"是更严格的引导）。（对应验收项 1.4.1"数据模板可下载：得到 xlsx，含字段说明页"） |
| 121-132 | `CellStyle head2 = headerStyle(wb); int dr = 0; for (String[] row : fields) { Row rr = desc.createRow(dr); for (int i = 0; i < row.length; i++) { rr.createCell(i).setCellValue(row[i]); if (dr == 0) rr.getCell(i).setCellStyle(head2); } dr++; }` | 循环写字段字典；第 127-129 行只给第 0 行（表头）套样式，数据行保持默认 |
| 133-134 | `desc.setColumnWidth(0, 4200); desc.setColumnWidth(3, 9000);` | 第 4 列"说明"给足 9000 宽度，避免长说明被截断显示 |
| 135-138 | `return toBytes(wb); } catch (IOException e) { throw new IllegalStateException("生成数据模板失败", e); }` | 返回字节；失败抛 `IllegalStateException` |
| 141-142 | `/** 错误明细导出。 */ public static byte[] errorDetail(List<Map<String, String>> rows, String[] headers)` | 通用"表头 + 行数据"导出：入参是外部准备好的行（`DataBatchController` 第 112-124 行把 `DataBatchService.errorRows` 的结果按中文表头重组），因此本方法不关心业务字段含义，只负责排版 |
| 143-145 | `try (Workbook wb = new XSSFWorkbook()) { Sheet sheet = wb.createSheet("错误明细"); CellStyle head = headerStyle(wb);` | 工作表名"错误明细"，用户一眼能认出文件内容 |
| 146-151 | `Row h = sheet.createRow(0); for (int i = 0; i < headers.length; i++) { h.createCell(i).setCellValue(headers[i]); h.getCell(i).setCellStyle(head); sheet.setColumnWidth(i, 4200); }` | 表头行；**列宽固定 4200**（不区分列），当前表头是"行号/项目编码/对象编码/数据时间/指标编码/指标值/单位/错误说明"，其中"错误说明"较长会被显示截断（Excel 中双击列边界可展开，不影响数据完整性） |
| 152-159 | `int r = 1; for (Map<String, String> row : rows) { Row rr = sheet.createRow(r++); for (int i = 0; i < headers.length; i++) { String v = row.get(headers[i]); rr.createCell(i).setCellValue(v == null ? "" : v); } }` | 数据从第 2 行开始；第 157 行把 null 写成空字符串而不是跳过——保证每行单元格数与表头一致，避免出现错列 |
| 160-163 | `return toBytes(wb); } catch (IOException e) { throw new IllegalStateException("生成错误明细失败", e); }` | 返回字节；失败抛 `IllegalStateException` |
| 166-174 | `private static CellStyle headerStyle(Workbook wb)` | 表头样式工厂：第 167-168 行新建样式与字体，第 169 行 `font.setBold(true)` 加粗，第 171-172 行设灰色底纹（`GREY_25_PERCENT` + `SOLID_FOREGROUND`）。**每个 Workbook 必须用自己创建的 CellStyle**（样式对象绑定在工作簿上，跨工作簿复用会失效或报错），这也是它接收 `Workbook` 参数的原因 |
| 176-182 | `private static byte[] toBytes(Workbook wb) throws IOException` | 第 177 行 `ByteArrayOutputStream` 在 try-with-resources 中；第 178-179 行 `wb.write(out)` 后立即 `wb.close()` 释放资源；第 180 行 `out.toByteArray()` 返回独立副本——`ByteArrayOutputStream` 关闭后仍可安全取数组，因为数据已在内存 |

**易错点/注意**：
1. **模板列顺序即解析依据，不能随意调整**：`IndicatorService.TEMPLATE_COLUMNS` / `DataBatchService.TEMPLATE_COLUMNS` 既是模板生成顺序，也是导入时 `data.get(row, "英文名", "中文名")` 的取值依据。增删列要同时确认两端（本方法只负责生成，不负责校验列是否被解析覆盖）。
2. `indicatorTemplate()` 的示例数据只有 6 行、且只覆盖 `weight`/`min_value`/`max_value`/`direction` 等部分列，`score_rule`（JSON 文本）、`interval_lower/upper`、`optimal_value` 在示例中均为空。使用区间型指标（`INTERVAL`）时，这些列没有示例可参照，需要另读方案或 `samples/指标体系样例.csv`。
3. 三个方法都会在异常时抛 `IllegalStateException`，即"生成模板失败"对用户表现为 HTTP 500 + `SYSTEM_ERROR`。这在语义上正确（属服务端缺陷），但排查时需要看服务端日志的根因消息。
4. 下载接口使用了 `URLEncoder.encode(name, UTF_8).replace("+", "%20")` 并同时给出 `filename` 与 `filename*=UTF-8''` 两种形式（`DataBatchController` 第 131-134 行），这是为了兼容各浏览器对中文文件名的处理——文件名编码不属于本类职责，但与本类产出的内容一起交付给用户，排查"下载得到乱码文件名"时应看 controller 而非本类。

### 2.18 `backend/src/main/java/com/ees/util/CryptoUtils.java`

**作用**：对数据源密码做对称加密后落库，页面不回显完整密码（方案 9.1）；解密仅供后端在建立数据库连接时使用。
**被谁使用**：`service/DataSourceService` 第 129 行（保存数据源时 `encrypt`）与第 366 行（建立连接时 `decrypt`）。`DataSourceService` 第 131 行还会在密码为空时抛 `PARAM_INVALID`，即本类不接收空值进入加密流程。
**关键设计点**：
1. **AES/GCM 而非 AES/CBC**：GCM 是带认证的加密模式（AEAD），密文被篡改时解密会直接抛异常，无需另配 HMAC；`TAG_BITS = 128` 是 GCM 推荐的认证标签长度。CBC 则无法检测篡改，且需要额外的填充与 IV 管理约定。
2. **IV 随密文前置**：每次加密用 `SecureRandom` 生成 12 字节随机 IV，并把它拼在密文最前面一起 Base64。GCM 模式下 **IV 重复使用是致命缺陷**（会泄露明文异或关系并可能被伪造标签），因此必须每次随机；而 IV 本身不需要保密，前置存储是最简单可靠的"自描述"方案——解密方从同一串字节里就能取出 IV，无需数据库额外加列。
3. **`ENC:` 前缀的作用**：给密文打一个自描述标记，使 `decrypt` 能区分"加密存储的新数据"与"系统上线前遗留的明文数据"，从而在**不改数据、不停机**的前提下平滑兼容历史记录（第 62-65 行的兼容分支就是它的用途）。`isEncrypted` 也依赖同一前缀，供页面判断"该字段是否已有密码"而不去解密。
4. 密钥不是配置里单独的项，而是**从最终生效的 JWT 密钥派生**（第 29 行读 `properties.getSecurity().getJwtSecret()`，而该值在 `JwtSecretProvider` 构造时已被回写为三级回退后的最终密钥，见 2.8；第 30-33 行做 `SHA-256("ees-datasource::" + secret)`）：避免新增一个必须保管的密钥项，同时用固定盐 `ees-datasource::` 保证与其他用途（JWT 签名）使用不同密钥。代价是**换密钥会导致所有已存数据源密码无法解密**——第 75-78 行的错误提示正是针对这一场景。反过来说，整改把密钥持久化到 `<storage.root>/.jwt-secret` 之后，"重启服务"不再会换密钥，只有"删除/替换该文件或改 `EES_JWT_SECRET`"才会。

| 行号 | 代码 | 说明 |
|---|---|---|
| 1 | `package com.ees.util;` | 工具包 |
| 3-5 | `import com.ees.common.BizException; import com.ees.common.ErrorCode; import com.ees.config.EesProperties;` | 失败抛统一异常（1500 系统错误）；密钥派生需要读配置 |
| 7-13 | `import javax.crypto.Cipher / spec.GCMParameterSpec / spec.SecretKeySpec; java.nio.charset.StandardCharsets; java.security.MessageDigest / SecureRandom; java.util.Base64;` | 密码学 API：`Cipher`（加解密）、`GCMParameterSpec`（GCM 参数：标签长度 + IV）、`SecretKeySpec`（AES 密钥）、`MessageDigest`（SHA-256 派生）、`SecureRandom`（IV 生成）、`Base64`（文本化） |
| 15-17 | `/** 对称加密工具：数据源密码加密保存，页面不回显完整密码（方案 9.1）。 */` | 明确用途与方案对应，同时点出"不回显"这一产品要求 |
| 18 | `public final class CryptoUtils {` | 静态工具类 |
| 20 | `private static final String ALGO = "AES/GCM/NoPadding";` | 算法串三段含义：`AES` 对称算法、`GCM` 认证加密模式（密文被改动会在解密时被发现）、`NoPadding`（GCM 是流式模式，不需要 PKCS#5 填充） |
| 21 | `private static final int TAG_BITS = 128;` | 认证标签 128 bit（16 字节），GCM 推荐值。标签越长伪造难度越高；128 bit 已是标准上限 |
| 22 | `private static final int IV_BYTES = 12;` | IV 12 字节（96 bit），**GCM 的标准推荐长度**（也是唯一能被硬件加速的长度）。其他长度需先经 GHASH 派生，效率与兼容性都更差 |
| 23 | `private static final String PREFIX = "ENC:"` | 密文标记前缀，用于区分明文遗留数据 |
| 25-26 | `private CryptoUtils() { }` | 私有构造 |
| 28 | `private static SecretKeySpec key(EesProperties properties)` | 密钥派生，每次调用重新派生 |
| 29 | `String secret = properties.getSecurity().getJwtSecret();` | 复用 JWT 密钥配置项，避免新增密钥管理项。**注意这里读到的是 `JwtSecretProvider` 回写后的最终密钥**（不是 yml 里的空串）——这正是"令牌签名与数据源密码加密共用同一把密钥"的落点，详见 2.8 |
| 30-33 | `MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] k = md.digest(("ees-datasource::" + secret).getBytes(UTF_8)); return new SecretKeySpec(k, "AES");` | 用 SHA-256 把"固定盐 + 配置密钥"压成**恰好 32 字节**（AES-256 密钥长度），绕开"用户配置的密钥长度不固定"问题；固定盐把本用途与 JWT 签名用途的密钥域分开（域分离） |
| 34-36 | `catch (Exception e) { throw new BizException(SYSTEM_ERROR, "生成加密密钥失败", e); }` | SHA-256 理论上必然存在；抛异常仅为编译期要求与极端环境兜底 |
| 39-41 | `public static String encrypt(String plain, EesProperties properties)` + `if (plain == null \|\| plain.isEmpty()) return plain;` | **空值原样返回**：空密码不需要加密，避免把 null 变成 `"ENC:..."` 之类的无效数据 |
| 43-45 | `byte[] iv = new byte[IV_BYTES]; new SecureRandom().nextBytes(iv);` | **每次加密都生成新的随机 IV**。这里直接 `new SecureRandom()` 而非静态复用：`SecureRandom` 是线程安全的，每次新建只是轻微开销，但可避免"共享实例被误用为确定性种子"的风险 |
| 46-47 | `Cipher cipher = Cipher.getInstance(ALGO); cipher.init(Cipher.ENCRYPT_MODE, key(properties), new GCMParameterSpec(TAG_BITS, iv));` | 初始化加密器：模式为加密、密钥由派生函数给出、GCM 参数带本次的 IV |
| 48 | `byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));` | 加密。**UTF-8 编码是必要的**：中文密码/特殊字符必须有一致的字节表示，否则解密后还原不出原串。返回值已包含 16 字节认证标签（GCM 的 `doFinal` 把 tag 附在密文后） |
| 49-51 | `byte[] out = new byte[iv.length + enc.length]; System.arraycopy(iv, 0, out, 0, iv.length); System.arraycopy(enc, 0, out, iv.length, enc.length);` | **IV 前置拼接**：`out = IV(12) ‖ 密文 ‖ 标签(16)`。解密方只需知道 `IV_BYTES = 12` 这一个常量即可切分，不需要额外存储字段 |
| 52 | `return PREFIX + Base64.getEncoder().encodeToString(out);` | 加前缀并 Base64 编码为文本，可安全存入数据库 `VARCHAR` 列与 JSON 传输（Base64 只含字母数字与 `+/=`，无控制字符） |
| 53-55 | `catch (Exception e) { throw new BizException(SYSTEM_ERROR, "密码加密失败", e); }` | 加密失败属系统级问题（算法不可用、密钥派生异常），抛 1500 并保留 cause |
| 58-60 | `public static String decrypt(String cipherText, EesProperties properties)` + `if (cipherText == null \|\| cipherText.isEmpty()) return cipherText;` | 与 `encrypt` 对称的空值处理 |
| 62-65 | `if (!cipherText.startsWith(PREFIX)) { // 兼容历史明文数据 return cipherText; }` | **历史明文兼容分支**：系统早期版本或手工导入的数据源可能直接存明文，此时不做任何解密尝试，原样返回。效果是"升级后老数据仍然可用"，代价是明文数据不会自动迁移（见易错点 2） |
| 66-69 | `byte[] all = Base64.getDecoder().decode(cipherText.substring(PREFIX.length())); byte[] iv = new byte[IV_BYTES]; System.arraycopy(all, 0, iv, 0, IV_BYTES);` | 去掉前缀后 Base64 解码，并按固定长度切出前 12 字节作为 IV（与 `encrypt` 第 49-51 行严格对称） |
| 70-71 | `byte[] data = new byte[all.length - IV_BYTES]; System.arraycopy(all, IV_BYTES, data, 0, data.length);` | 剩余部分即"密文 ‖ 标签"，作为 `doFinal` 的输入 |
| 72-73 | `Cipher cipher = Cipher.getInstance(ALGO); cipher.init(Cipher.DECRYPT_MODE, key(properties), new GCMParameterSpec(TAG_BITS, iv));` | 用同一密钥与同一 IV 初始化解密器；若 IV 或标签不匹配，`doFinal` 会抛 `AEADBadTagException` |
| 74 | `return new String(cipher.doFinal(data), StandardCharsets.UTF_8);` | 解密并还原 UTF-8 字符串 |
| 75-78 | `catch (Exception e) { throw new BizException(SYSTEM_ERROR, "密码解密失败，可能加密密钥已变更，请重新录入数据源密码", e); }` | 把最常见的根因（JWT 密钥被改导致派生密钥变化）**直接写进提示**，给运维可执行的动作；同时保留 cause 供日志定位是标签校验失败还是 Base64 损坏 |
| 81-83 | `public static boolean isEncrypted(String s)` → `s != null && s.startsWith(PREFIX)` | 只做前缀判断，**不解密、不校验有效性**，因此它回答的是"看起来是不是密文"而非"密文是否可用"。供前端/服务层判断"是否需要重新录入密码" |

**易错点/注意**：
1. **密钥轮换的爆炸半径**：数据源密码的加密密钥由"最终生效的 JWT 密钥"派生，即 `EES_JWT_SECRET` 的值，或本地密钥文件 `<storage.root>/.jwt-secret` 的内容（见 2.8）。**删除/替换该文件、或改动该环境变量**会同时导致**所有已存数据源密码无法解密**（报 1500 + "密码解密失败…请重新录入数据源密码"）与**所有已签发 JWT 失效**。因此整改后必须把该密钥文件纳入备份（`docs/DEPLOY.md` 5.1 已列明）；确实需要轮换时，必须配套执行"逐个数据源重新录入密码"的运维步骤。
2. **历史明文数据不会被自动加密**：`decrypt` 的兼容分支意味着老数据一直是明文。若安全审计要求"库中不得出现明文密码"，需要额外写一次性的迁移逻辑（读取 → 重新 `encrypt` → 回写），当前代码中不存在这段逻辑。
3. 页面"不回显完整密码"是**服务层与前端共同实现**的（`DataSourceService` 查询时不返回真实密码），本类只负责存储形态；不要误以为加密本身就等于脱敏。
4. `decrypt` 抛出的异常消息里含有面向运维的建议，会经 `GlobalExceptionHandler` 作为 `message` 返回给前端——这是刻意的可运维性设计，但也说明该提示不应包含密钥本身（当前实现没有泄露密钥内容）。
5. `AES/GCM/NoPadding` 要求 JCE 无限强度策略（Java 8u161+ 默认已开启）。当前项目使用 Java 21，无此问题；若被部署到极老的 JRE，`Cipher.getInstance` 或 256 位密钥会失败。

## 3. 本册小结

- 本册共覆盖 **18** 个文件、**1786** 行代码（`common` 6 个 581 行、`security` 5 个 548 行、`dto` 4 个 114 行、`util` 3 个 543 行），与分配清单一致，无遗漏、无多余。其中 `security/JwtSecretProvider.java`（192 行）为交付前安全整改**新增**的文件，`security/JwtService.java` 因整改由 109 行变为 123 行（新增"密钥由 provider 提供"的构造参数、类注释说明，以及第 52-58 行的空密钥防御性守卫）。
- **本次安全整改（JWT 内置默认密钥）是本册最需要讲清楚的一件事**：整改前 `application.yml` 与 `EesProperties` 各内置了一个"默认密钥"，随源码交付、等同公开值。**实测复现**：用该默认密钥在本地签发一枚 `role=SYSTEM_ADMIN`、`perms=["*"]` 的 HS256 令牌，访问 `/api/users`、`/api/configs`、`/api/audit-logs` 全部成功（`/api/audit-logs` 读到 69 条记录），`POST /api/data-sources` 也能成功写入数据——属**可越权读写**的真实漏洞；根因是 `JwtService.parse()` 只校验签名、签发者与有效期而**不比对数据库中的用户**，密钥泄漏即等于可伪造任意用户与全部权限。整改后新增 `JwtSecretProvider`（见 2.8）按"环境变量 `EES_JWT_SECRET` → 本地密钥文件 `<ees.storage.root>/.jwt-secret`（首次启动生成 64 字节随机密钥并**持久化**）→ 进程内随机密钥（仅告警）"三级解析，并把结果回写 `EesProperties`；**同一枚旧密钥伪造的令牌访问上述三个接口全部返回 HTTP 401**，`admin/admin123` 正常登录后签发的令牌访问 `/api/users` 仍成功，重启前后密钥文件 SHA256 一致且重启前签发的令牌重启后依然有效，启动日志只打印密钥**来源**、从不打印密钥本身。
- **密钥必须持久化，这是与 `CryptoUtils` 的硬耦合**：数据源密码的 AES 密钥由 `SHA-256("ees-datasource::" + jwtSecret)` 派生（见 2.18），若每次启动随机生成，已保存的数据源密码将全部解不开（表现为"密码解密失败，可能加密密钥已变更"）。因此 `<storage.root>/.jwt-secret` 必须随 `data` 目录一起备份；反过来，"删除/替换该文件或改 `EES_JWT_SECRET`"是唯一会同时导致"全员重新登录 + 数据源密码作废"的操作。
- **统一响应 + 统一错误码 + 全局异常处理三者构成前后端契约的唯一出口**：controller 里几乎看不到 `try/catch`，任何一层的失败最终都会变成 `code != 0` 的 JSON。改这三者的任何一处，等于改全站接口契约。
- **HTTP 状态码在本项目里是"弱语义"**：只有 401（未登录/令牌无效）、403（越权）、404（资源不存在，含静态资源缺失，见 2.5）、500（系统内部错误）被真实使用；3xxx 输入错误、4xxx 算法错误、1009 数据冲突等业务失败**一律返回 HTTP 200**。所以"HTTP 200 但 `code=1001`"是设计而非 bug，前端只按 `code` 分支。
- **认证是无状态的，权限是双轨的**：`JwtService` 把 uid/name/role/scope/perms 全部放进令牌，服务端不存 session（`SecurityConfig` 用 `STATELESS`），因此"登出"只是前端丢令牌，**账号被停用/锁定不会让已签发令牌立即失效**（有效期默认 480 分钟）；签名密钥由 `JwtSecretProvider` 三级解析后注入（见 2.8），不再是配置字面量。`LoginUser.getAuthorities()` 同时产出 `ROLE_xxx` 与细粒度权限码，所以 `@PreAuthorize` 能写成 `hasAuthority(...) or hasRole(...)`，`SYSTEM_ADMIN` 角色与 `*` 权限是超级管理员后门。
- **工具类的容错取向是分层的，不能一刀切**：`TabularReader` 面向"用户数据可能脏"，`parseNumber`/`parseDateTime` 失败返回 null 并由业务层生成行级错误明细（否则无法做到"错误可定位"）；`CryptoUtils`/`JsonUtils.toJson`/`parse` 面向"系统自身状态异常"，失败直接抛 `BizException(SYSTEM_ERROR)` 快速暴露。`JsonUtils.parseQuietly`/`toMap*` 是给历史数据留的宽松通道。
- **模板与解析同源是这套导入功能最值得保留的设计**：`ExcelTemplateWriter` 的列定义直接引用 `IndicatorService.TEMPLATE_COLUMNS` / `DataBatchService.TEMPLATE_COLUMNS`，模板因此不会与解析逻辑漂移；模板第 2 行的"中文说明行"之所以不破坏导入，靠的是 `findHeaderRow` 的"非空单元格最多 + 平手取靠前"策略与 `TableData.get` 的多候选列名别名解析——这两点互为配合，改动任一处都要连同验证。
- **本册已知的空白点（交接时需知晓）**：`ApiResponse.traceId` 无填充点；`LoginRequest` 的 `captcha`/`captchaKey`/`rememberMe` 无读取点；`ChangePasswordRequest` 不校验"新密码不得与原密码相同"；`TabularReader.decode` 不处理 UTF-16 BOM（Excel"Unicode 文本"导出的 CSV 会乱码）；CSV 路径无表头空名/重名兜底；`Reader` 的 `findHeaderRow` 注释提到的"含'指标'"关键字策略未实现；`com.ees.common.CodeGenerator` 不存在（编号生成已移至 `com.ees.service.CodeService`），`README.md` 第 18 行的描述属滞后。
