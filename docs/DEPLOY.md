# 效能评估系统 —— 部署与运维手册

## 一、环境安装（本机已完成的配置）

### 1.1 已安装组件

| 组件 | 版本 | 安装位置 | 安装方式 |
|---|---|---|---|
| Microsoft Build of OpenJDK | 21.0.12.1 LTS | `D:\DevEnv\jdk-21` | 解压免安装 |
| Apache Maven | 3.9.16 | `D:\DevEnv\maven` | 解压免安装 |
| MySQL Community Server | **8.0.45** | 程序 `C:\Program Files\MySQL\MySQL Server 8.0`，数据 `D:\MySQLData\Data` | **官方 MSI 静默安装**，注册为 Windows 服务 `MySQL80`（自动启动） |
| Node.js / npm | 24.19.0 / 11.17.0 | `C:\Program Files\nodejs` | 已有 |
| IntelliJ IDEA | 2025.3（build 253.28294.334） | `D:\DevEnv\IDEA` | 解压免安装 |
| Navicat Premium Lite | 17 | `D:\DevEnv\Navicat` | Inno Setup 静默安装 |

### 1.2 环境变量（用户级，已写入注册表）

| 变量 | 值 |
|---|---|
| `JAVA_HOME` | `D:\DevEnv\jdk-21` |
| `MAVEN_HOME` / `M2_HOME` | `D:\DevEnv\maven` |
| `Path` | 追加 `D:\DevEnv\jdk-21\bin`、`D:\DevEnv\maven\bin`、`C:\Program Files\MySQL\MySQL Server 8.0\bin`、`D:\DevEnv\IDEA\bin`、`D:\DevEnv\Navicat` |

> **关于 `EES_DB_*` 与 `EES_STORAGE_ROOT`：本机刻意不设置这些环境变量。**
> 数据库账号、口令、存储目录一律以仓库中的 `application-dev.yml` / `application-prod.yml`
> 为唯一出处，避免出现「改了配置文件却不生效」这类难排查的问题。
> 企业环境如需集中下发口令，再按 3.2 节的方式通过环境变量注入。

### 1.3 依赖仓库配置

| 工具 | 配置文件 | 配置内容 |
|---|---|---|
| Maven | `C:\Users\Administrator\.m2\settings.xml` | 本地仓库 `D:\DevEnv\m2repo`；开发期使用阿里云公共仓库镜像加速；已预下载全部依赖（约 270 MB） |
| npm | 用户级 `.npmrc` | registry 指向 `https://registry.npmmirror.com` |

> **离线交付说明**：正式离线环境把 `settings.xml` 中的 `<mirror>` 段注释掉、将 `<offline>` 改为 `true`，
> 并把预填好的 `D:\DevEnv\m2repo` 一并打包，即可在完全断网条件下 `mvn -o package`。

---

## 二、数据库部署

### 2.1 安装形态（官方 MSI 安装版）

数据库采用 **MySQL 官方 MSI 安装包**，安装为 Windows 服务，开机自动运行、无控制台窗口。

| 项目 | 值 |
|---|---|
| 安装包 | `mysql-8.0.45-winx64.msi`（123.6 MB，来源 `https://cdn.mysql.com/Downloads/MySQL-8.0/`） |
| 安装包校验 | Authenticode 签名 **Valid**，签名者 `Oracle America, Inc.`（DigiCert 颁发）；<br>SHA256 `95638B094473D2FF5F60E5E8C7E4089747E0E1FBCB4BCB78A3E1F64928F9B07D` |
| 产品信息 | ProductName `MySQL Server 8.0`，ProductVersion `8.0.45`，Manufacturer `Oracle Corporation` |
| 程序目录 | `C:\Program Files\MySQL\MySQL Server 8.0` |
| 配置文件 | `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini` |
| 数据目录 | `D:\MySQLData\Data` |
| 错误日志 | `D:\MySQLData\mysql-error.log` |
| 慢查询日志 | `D:\MySQLData\mysql-slow.log` |
| 服务名 | `MySQL80`（启动类型：自动） |
| 客户端工具 | `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe`、`mysqldump.exe`、`mysqladmin.exe` |

### 2.2 启动与停止

服务为自动启动，**开机即运行，通常无需手工干预**：

```bat
sc query MySQL80          :: 查看状态（STATE : 4 RUNNING 为正常）
net start MySQL80         :: 手工启动（需管理员）
net stop  MySQL80         :: 手工停止（需管理员）
```

`scripts\start-all.bat` 与 `scripts\stop-all.bat` 会自动探测本机的 MySQL 服务名
（依次尝试 `MySQL80`/`MySQL84`/`MySQL57`/`MySQL56`/`MySQL`）并启停；
若数据库由甲方 DBA 维护或位于其它主机，脚本只提示、不会强行操作，
也可通过 `EES_MYSQL_START` / `EES_MYSQL_STOP` 指定自定义启停脚本。

### 2.3 重新安装步骤（离线或换机时）

```bat
:: 1) 静默安装（管理员）
msiexec /i mysql-8.0.45-winx64.msi /qn /norestart /l*v install.log

:: 2) 写入 my.ini（内容见 docs 与本机 C:\ProgramData\MySQL\MySQL Server 8.0\my.ini）

:: 3) 初始化数据目录，root 初始密码为空
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe" ^
  --defaults-file="C:\ProgramData\MySQL\MySQL Server 8.0\my.ini" --initialize-insecure

:: 4) 注册服务并启动
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqld.exe" --install MySQL80 ^
  --defaults-file="C:\ProgramData\MySQL\MySQL Server 8.0\my.ini"
sc config MySQL80 start= auto
net start MySQL80

:: 5) 设置 root 密码并创建账号（等价于官方 MySQLInstallerConsole 的配置步骤）
mysql -u root -e "source db/00_create_database.sql"
```

### 2.4 初始化脚本

| 脚本 | 执行时机 | 执行方式 |
|---|---|---|
| `db/00_create_database.sql` | 一次性 | 以 `root` 执行：设置 root 密码、创建数据库、应用账号 `ees`、只读账号 `ees_reader` |
| `db/01_schema.sql` | 首次启动后端时自动执行 | 24 张表，`CREATE TABLE IF NOT EXISTS`，幂等 |
| `db/02_seed.sql` | 每次启动后端时自动执行 | 角色、系统参数、质量规则、算法基准；`INSERT IGNORE` 不覆盖已有数据 |
| `db/99_reset_demo_data.sql` | 需要重新演示时手工执行 | 清空业务数据，保留用户/角色/参数 |

在 MySQL Workbench 或 Navicat 中依次执行 `00` → `01` → `02` 也可以完成同样的初始化，
随后把后端 `spring.sql.init.mode` 设为 `never` 即可（生产环境推荐）。

### 2.5 账号权限（最小权限原则）

| 账号 | 密码 | 权限 |
|---|---|---|
| `root@localhost` | `Root@2026` | 全部 |
| `ees@%` / `ees@127.0.0.1` / `ees@localhost` | `Ees@2026` | 仅 `efficiency_evaluation` 库 |
| `ees_reader@%` | `EesRead@2026` | 仅 `SELECT, SHOW VIEW`（用于演示“只读账号接入”） |

---

## 三、后端部署

### 3.1 开发模式

```bat
cd /d <项目根>\backend
mvn spring-boot:run
```

### 3.2 生产打包与运行

**推荐方式：单 jar 运行（离线交付首选）**

`backend/pom.xml` 已配置在 `prepare-package` 阶段把 `frontend/dist` 复制进 jar 的
`classpath:/static/`，因此**一个 jar 内同时包含前端页面与后端服务**，单进程即可访问：

```bat
cd /d <项目根>
scripts\build-all.bat          :: 先构建前端，再构建后端（顺序不可颠倒）

cd /d <项目根>\backend
java -Dfile.encoding=UTF-8 -jar target\evaluation-backend.jar --spring.profiles.active=prod
:: 浏览器打开 http://127.0.0.1:8080/
```

目标机器只需 **JDK 21 + MySQL**，不需要 Node，也不需要 Nginx。

也可以直接双击 `scripts\start-offline.bat`：它会检查环境、准备数据库连接参数
（口令未设置时提示输入）、以 prod 档启动后端并自动打开浏览器。

> **为什么必须用 prod 档**：prod 档关闭 DEBUG 日志与详细异常堆栈，
> 满足技术方案 9.1「生产环境关闭调试接口和详细异常堆栈，日志不得打印密码和敏感配置」。
> dev 档保留 `com.ees: DEBUG`，仅用于开发调试。
>
> 若只构建后端而未构建前端，复制步骤会自动跳过并给出提示（不会构建失败），
> 但此时 jar 内不含页面，prod 档访问 `/` 会 404。

生产环境必须通过环境变量注入的配置项：

| 环境变量 | 说明 | 默认值 |
|---|---|---|
| `EES_JWT_SECRET` | JWT 签名密钥（Base64，建议 ≥32 字节） | **无默认值**。未设置时按「本地密钥文件 → 首次启动自动生成」处理，详见下方说明 |
| `EES_DB_HOST` / `EES_DB_PORT` / `EES_DB_NAME` | 数据库地址 | `127.0.0.1` / `3306` / `efficiency_evaluation` |
| `EES_DB_USER` / `EES_DB_PASSWORD` | 数据库账号 | `ees` / **空（prod 档必须注入）** |
| `EES_STORAGE_ROOT` | 上传、快照、报告与日志根目录 | `./data` |
| `EES_ALGORITHM_ENGINE` | `JAVA_NATIVE`（内置）或 `CPP_ENGINE`（C++ 独立算法服务） | `JAVA_NATIVE` |
| `EES_CPP_ENGINE_URL` | C++ 算法服务地址（`CPP_ENGINE` 模式生效） | `http://127.0.0.1:9090` |
| `EES_PDF_FONT_PATH` | PDF 中文字体文件路径（`.ttf`/`.ttc`） | 自动搜索系统字体 |

#### 3.2.1 JWT 签名密钥的处理（重要）

**交付包内不含任何可用密钥。** 早期版本在 `application.yml` 与 `EesProperties` 中各内置了一个
「默认密钥」，该密钥随源码一起交付、等同公开值，任何人都能据此本地签发 `role=SYSTEM_ADMIN`
的令牌。交付前实测：用该默认密钥伪造令牌访问 `/api/users`、`/api/configs`、`/api/audit-logs`
**全部返回成功**，并且可以 `POST /api/data-sources` 写入数据，属可越权读写的真实漏洞，已整改。

整改后由 `com.ees.security.JwtSecretProvider` 按以下顺序解析密钥：

| 顺序 | 来源 | 说明 |
|---|---|---|
| 1 | 环境变量 `EES_JWT_SECRET` | 甲方有统一密钥管理要求时使用 |
| 2 | `<存储根>/.jwt-secret` | 首次启动自动生成 64 字节随机密钥并**持久化保存** |
| 3 | 进程内随机密钥 | 仅在密钥文件不可写时退化使用，会告警；重启后登录态失效 |

> **密钥必须持久化，不能每次启动随机生成**：`CryptoUtils` 加密数据源密码所用的 AES 密钥
> 同样派生自本密钥。若每次启动都换新密钥，已保存的数据源密码将无法解密
> （表现为「密码解密失败，可能加密密钥已变更」）。

运维注意事项：

1. `data/.jwt-secret` **必须纳入备份范围**（见 5.1），丢失后已保存的数据源密码需重新录入。
2. 该文件已被 `.gitignore` 忽略，不会进入版本库。
3. 启动日志与启动横幅会打印**密钥来源**（如「本地密钥文件 …」），但**从不打印密钥本身**，
   便于交付评审时自证合规。
4. 若配置中残留整改前的旧默认密钥，启动时会识别为不安全、忽略并打印告警，
   自动改用本地密钥文件，避免「改了配置仍不安全」。

### 3.3 健康检查与关键日志

| 检查项 | 地址 / 位置 |
|---|---|
| 健康检查 | `GET /api/health` → `{status, components:{database,algorithm,storage}}` |
| 系统信息 | `GET /api/info` |
| 应用日志 | `<存储根>\logs\evaluation-backend.log`（50 MB 滚动，保留 30 份） |
| MySQL 错误日志 | `D:\MySQLData\mysql-error.log` |
| MySQL 慢查询日志 | `D:\MySQLData\mysql-slow.log`（>2 秒） |

---

## 四、前端部署

### 4.1 开发模式

```bat
cd /d <项目根>\frontend
npm install
npm run dev          :: http://127.0.0.1:5173，/api 代理到 8080
```

### 4.2 生产构建

```bat
npm run build        :: 产物在 dist/
```

`vite.config.ts` 已设置 `base: './'`，静态资源使用相对路径，可部署在 Nginx 任意子路径下。
路由使用 hash 模式，无需额外的 history fallback 规则。

### 4.3 推荐：内嵌进后端 jar（离线交付首选）

无需单独部署前端服务。构建时 `backend/pom.xml` 的 `copy-frontend-dist-into-jar` 执行会把
`frontend/dist` 复制到 jar 的 `classpath:/static/`，随后由 Spring Boot 默认静态资源机制提供，
`/` 经 `WelcomePageHandlerMapping` 自动映射到 `index.html`。

```bat
scripts\build-all.bat        :: 先 npm run build，再 mvn clean package（内部完成复制）
```

因此企业离线交付只需：

| 项 | 内容 |
|---|---|
| 交付物 | `backend\target\evaluation-backend.jar` 单个文件 |
| 目标机器依赖 | JDK 21、MySQL 8.0（**无需 Node、无需 Nginx**） |
| 启动方式 | `scripts\start-offline.bat`（双击）或 `java -jar ... --spring.profiles.active=prod` |
| 访问地址 | `http://127.0.0.1:8080/`（单进程同时提供页面与接口） |

### 4.4 备选：Nginx 托管独立前端

若甲方已有 Nginx 且希望前后端分离部署，可用下列配置。此时后端仍以 prod 档运行，
前端使用 `frontend/dist` 目录。

```nginx
server {
    listen       80;
    server_name  _;
    charset      utf-8;

    root  /opt/ees/frontend/dist;
    index index.html;

    # 前端静态资源
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端接口
    location /api/ {
        proxy_pass         http://127.0.0.1:8080/api/;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 600s;   # 大批量导入与报告导出耗时较长
        client_max_body_size 200m; # 与后端 spring.servlet.multipart.max-file-size 保持一致
    }
}
```

---

## 五、备份与恢复

### 5.1 备份范围

| 对象 | 位置 | 说明 |
|---|---|---|
| 数据库 | `efficiency_evaluation` | 业务数据、审计日志 |
| 上传文件 | `<存储根>\upload` | 原始导入文件与批次留档 |
| 报告文件 | `<存储根>\report` | 导出的 Excel / Word / PDF |
| 配置文件 | `backend/src/main/resources/application*.yml`、`.m2/settings.xml` | 环境与依赖配置 |
| JWT 签名密钥 | `<存储根>\.jwt-secret` | **必须备份**：丢失后已保存的数据源密码无法解密，需重新录入 |
| MySQL 配置与数据 | `C:\ProgramData\MySQL\MySQL Server 8.0\my.ini`、`D:\MySQLData` | 数据库实例 |

### 5.2 备份命令

```bat
:: 数据库全量备份
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" -u ees -pEes@2026 ^
  --single-transaction --routines --triggers --default-character-set=utf8mb4 ^
  efficiency_evaluation > backup_%date:~0,4%%date:~5,2%%date:~8,2%.sql

:: 文件目录备份（含快照与报告）
robocopy "<项目根>\data" "D:\backup\ees\data_%date:~0,4%%date:~5,2%%date:~8,2%" /MIR
```

备份文件应记录生成时间、系统版本与 SHA256 校验值。

### 5.3 恢复步骤

```bat
:: 1. 停止后端
:: 2. 恢复数据库
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u ees -pEes@2026 --default-character-set=utf8mb4 ^
  efficiency_evaluation < backup_20260928.sql
:: 3. 恢复数据目录（robocopy /MIR 反向执行）
:: 4. 启动后端，执行恢复后检查：
::    - 登录成功
::    - 数据源连接测试成功
::    - 历史结果可查询且追溯信息完整
::    - 完成一次样例评估
```

首期恢复目标建议 RPO ≤ 24 小时、RTO ≤ 4 小时，最终以甲方制度为准。

---

## 六、故障处理

| 现象 | 可能原因 | 处理步骤 |
|---|---|---|
| 后端启动失败，日志出现 `Communications link failure` | MySQL 未启动 | 确认服务 `MySQL80` 处于 RUNNING（`sc query MySQL80`），必要时 `net start MySQL80` |
| 后端启动失败，`Access denied for user 'ees'` | 账号或密码不符 | 检查 `EES_DB_USER` / `EES_DB_PASSWORD`；必要时重跑 `db/00_create_database.sql` |
| 登录返回“账号已锁定” | 连续登录失败达 5 次 | 等待 15 分钟，或用 Navicat 将 `sys_user.status` 改为 `ENABLED`、`locked_until` 置空 |
| 导入文件报“文件类型不允许” | 扩展名不在白名单 | 允许 `xlsx/xls/csv/json/zip/txt`，可在 `ees.storage.allowed-extensions` 调整 |
| 导入后提示“阻断级错误” | 必填字段缺失、缺失值或指标未覆盖 | 查看质量报告的 `blockingReasons`，下载错误明细修正后重新导入 |
| 任务失败，`AHP_CR_NOT_PASSED` | 判断矩阵一致性比率 CR ≥ 0.1 | 修正判断矩阵；或经甲方确认后由管理员把 `algorithm.allow.force` 设为 `true` |
| 任务失败，`ALGORITHM_ENGINE_UNAVAILABLE` | `CPP_ENGINE` 模式下 C++ 服务不可达 | 检查 `EES_CPP_ENGINE_URL` 与服务进程；必要时临时切回 `JAVA_NATIVE` |
| PDF 导出失败，提示未找到中文字体 | 系统无可用 CJK 字体 | 通过 `EES_PDF_FONT_PATH` 指定 `.ttf`/`.ttc` 字体文件路径 |
| 数据源连接测试失败 | 网络、账号或驱动问题 | 确认目标库可达；确认对应 JDBC 驱动已加入部署包（`pom.xml` 已含 MySQL、Oracle、SQL Server） |
| 前端页面空白 | 未启动前端或静态资源路径错误 | 单 jar 方式：确认构建时已先执行 `npm run build`（jar 内 `BOOT-INF/classes/static/index.html` 应存在）；<br>独立部署方式：确认 `npm run dev` 在运行，或检查 Nginx `root` 与 `base: './'` |
| prod 档访问 `/` 返回 404 | 构建 jar 时 `frontend/dist` 不存在，前端未被打进 jar | 执行 `scripts\build-all.bat` 重新构建；该脚本会先构建前端再打包后端 |
| 提示「密码解密失败，可能加密密钥已变更」 | `<存储根>\.jwt-secret` 被删除或被替换 | 从备份恢复该文件；确实无法恢复时重新录入各数据源密码 |
| 重启后所有用户需重新登录 | `<存储根>` 不可写，密钥退化为进程内随机值 | 查看启动日志中「JWT 签名密钥来源」一行；为该目录授予写权限，或显式设置 `EES_JWT_SECRET` |
| 页面提示“请求超时” | 大批量导入或报告导出耗时较长 | 前端超时已设为 120 秒、上传 600 秒；如仍超时请调整 Nginx `proxy_read_timeout` |

---

## 七、离线部署要点

1. **前端**：`npm run build` 产物已内联全部依赖，无 CDN、无外部字体与图标请求；
   `index.html` 中的 favicon 使用内联 DataURL。
2. **后端**：全部依赖来自本地 Maven 仓库；PDF 使用 `openpdf-fonts-extra` 内置 CJK 字体或系统字体，不联网下载。
3. **算法**：默认 `JAVA_NATIVE` 内置实现，无需外部进程；
   切换到 `CPP_ENGINE` 时算法服务位于内网，同样不访问互联网。
4. **单文件交付**：前端已内嵌进 `evaluation-backend.jar`，目标机器只需 JDK 21 + MySQL，
   不需要 Node、不需要 Nginx，也不会因缺少某个静态目录而白屏。详见 4.3。
5. **无内置凭据**：交付包内不含任何 JWT 签名密钥或数据库口令默认值。
   签名密钥在目标机器首次启动时自动生成并保存到 `<存储根>\.jwt-secret`，
   每套部署各不相同；如甲方要求统一密钥，用 `EES_JWT_SECRET` 注入。
6. **脚本不绑定本机路径**：`scripts\*.bat` 全部以脚本所在目录为基准，
   MySQL 通过 `EES_MYSQL_START` / `EES_MYSQL_STOP` 或自动探测
   `MySQL80/84/57/56/MySQL` 服务；探测不到只提示、不中断，
   便于在数据库由甲方 DBA 统一维护的环境中直接使用。
7. **验证方法**：断开外网后完整走通「登录 → 导入指标体系 → 导入数据 → 生成快照 → 执行三种算法 → 八类图形 → 导出报告」，
   并在浏览器开发者工具中确认无外部域请求失败。
