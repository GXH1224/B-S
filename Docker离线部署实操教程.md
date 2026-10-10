# 效能评估系统 · Docker 离线部署实操教程

> 适用对象：需要在**内网离线服务器**上部署本系统的操作人员。
> 工具：Windows 上的 **Docker Desktop**（图形界面 + 命令行）。
> 全程手动操作，不依赖任何自动化脚本。
>
> **本文档版本**：2026-10-07 实测版（所有尺寸、命令、输出均为本机实跑结果）

---

## 零、先搞懂：为什么这个项目特别好做

大部分项目的 Docker 化要拆三四个容器（Nginx + 后端 + 数据库 + 缓存），
但**这个项目只需要 2 个**。原因是代码本身做了两件很贴心的事：

| 特性 | 依据 | 带来的好处 |
|---|---|---|
| **前端页面已内嵌进 jar** | `evaluation-backend.jar` 内含 `BOOT-INF/classes/static/index.html` 等 56 个静态资源 | **不需要 Nginx 容器**，一个后端容器同时提供页面和接口 |
| **后端启动时自动建表 + 灌初始数据** | `application.yml:52-55` 配置 `spring.sql.init.mode: always`，执行 `classpath:db/schema.sql` 与 `db/data.sql` | **不需要手工执行建表 SQL**，只要数据库存在即可 |

⇒ **最终架构**：

```
┌──────────────────────────────────────────┐
│  Docker 宿主机（离线服务器）               │
│                                          │
│  ┌────────────┐      ┌────────────────┐  │
│  │ ees-mysql  │◄─────│  ees-backend   │  │
│  │ MySQL 9.6  │ 3306 │  jar + JRE 21  │  │
│  │            │      │  (含前端页面)   │  │
│  └────────────┘      └────────────────┘  │
│         ▲                    ▲           │
│    数据卷持久化          端口 8080 对外   │
└──────────────────────────────────────────┘
```

---

## 一、离线部署的核心原理

**为什么必须"有网机器构建、离线机器导入"？**

```
有网机器：docker build 构建镜像  →  docker save 导出 tar
                    ↓ 把 tar 拷到离线服务器
离线服务器：装 Docker  →  docker load 导入镜像  →  docker compose up 启动
```

**关键点：镜像必须在有网机器上构建，不能等到离线机器上再 build。**

原因是 `docker build` 的过程需要下载**基础镜像**（如 `eclipse-temurin:21-jre`、`mysql:9.6`），
离线服务器拉不到这些基础镜像，build 一定会失败。

⇒ 所以我们的做法是：**在有网机器上把「运行环境 + 程序」一起封成镜像，导出成 `.tar` 带过去。**

---

## 二、准备工作：3 个配置文件（已放在项目根目录）

| 文件 | 作用 | 是否必需 |
|---|---|---|
| `Dockerfile` | 后端镜像的"构建说明书"：以 JRE 21 为基础，把 jar 拷进去 | **必需** |
| `.dockerignore` | 告诉 Docker 哪些文件不用发给构建引擎（排除 node_modules 等） | 强烈建议 |
| `docker-compose.yml` | 定义两个容器怎么跑、怎么连、数据存哪 | **必需** |

> **注意**：`Dockerfile` 不是"脚本"，它是 Docker 的**配置文件**（相当于一份菜谱）。
> Docker 不认识 .bat，只认识 Dockerfile。所以这个文件无法省略。

### 先确认前置条件

打开**项目根目录**，确认这个产物已存在：

```
backend\target\evaluation-backend.jar    ← 约 90 MB
```

如果 `evaluation-backend.jar` 不存在，需要先构建：
```
双击 scripts\build-all.bat
```

---

## 三、阶段 A：在有网机器上（就是你现在这台）

### A1　启动 Docker Desktop

1. 双击桌面的 **Docker Desktop** 图标
2. 等待左下角/右下角的**鲸鱼图标停止动画、变成稳定的绿色**
3. 首次启动可能需要 1~2 分钟

> ⚠️ 如果提示 `failed to connect to the docker API at npipe:...`，
> 说明 Docker 引擎还没起来，等图标变绿再操作。

**验证是否启动成功**——打开终端执行：

```bash
docker version
```

看到 **Client** 和 **Server** 两段都输出，就说明成功了。
（只有 Client 没有 Server，说明引擎还没起来。）

### A2　打开终端

有两种方式，任选：

- **方式一（推荐）**：Docker Desktop 界面右上角的 **Terminal** 按钮
- **方式二**：Windows 开始菜单搜索 `PowerShell` 打开

然后**切换到项目目录**：

```bash
cd /d D:\Projects\效能评估系统_v1.0.0\效能评估系统_v1.0.0
```

> `cd /d` 中的 `/d` 是让 CMD 允许跨盘符切换目录。

### A3　构建后端镜像

在项目根目录（能看到 `Dockerfile` 的那一层）执行：

```bash
docker build -t ees-backend:1.0.0 .
```

**参数解释**：

| 部分 | 含义 |
|---|---|
| `docker build` | 构建镜像 |
| `-t ees-backend:1.0.0` | 给镜像起名 `ees-backend`，标签 `1.0.0` |
| `.` | **构建上下文是当前目录**（末尾这个点不能漏！） |

**预期输出**（最后几行）：
```
 => [1/4] FROM eclipse-temurin:21-jre
 => [2/4] WORKDIR /app
 => [3/4] COPY backend/target/evaluation-backend.jar /app/app.jar
 => [4/4] RUN mkdir -p /app/data/...
 => exporting to image
 => => naming to docker.io/library/ees-backend:1.0.0
```

第一次执行会从镜像源下载 JRE 基础镜像（约 250 MB），需要几分钟。

> **实测**：本项目 `.dockerignore` 已配置得当，构建上下文仅 **1.18 kB**，
> 不会把 `node_modules`（几百 MB）发给构建引擎，构建很快。

### A4　拉取 MySQL 镜像

```bash
docker pull mysql:9.6
```

> **为什么用 `9.6` 而不是 `8.4`？**（三条理由）
> 1. **本机开发环境用的就是 MySQL Server 9.6.0**，`db/01_schema.sql` 已在 9.6 上实测通过；
> 2. `mysql:9.6` 镜像已在有网机器上准备好，离线部署时无需再下载；
> 3. 与开发环境版本完全一致，避免「开发能跑、交付报错」的版本差异问题。
>
> **若甲方明确要求 LTS 版本**：把 `docker-compose.yml` 里的 `image: mysql:9.6` 改成
> `mysql:8.4`，然后 `docker pull mysql:8.4` 重新拉取即可。功能上无差异。
>
> ⚠️ 若拉取时报 `short read: expected xxx bytes but got 0: unexpected EOF`，
> 是镜像源网络中断（不是你操作的问题），重试或换镜像源即可。

### A5　在 Docker Desktop 界面里确认镜像

1. 点左侧导航栏的 **Images**
2. 应该能看到两条记录：

| Repository | Tag | Size（实测） |
|---|---|---|
| `ees-backend` | `1.0.0` | **645 MB** |
| `mysql` | `9.6` | **1.27 GB** |

> 如果没看到，点右上角的刷新图标。

### A6　【强烈建议】先在本地试跑一次

**这一步非常重要** —— 避免把一个跑不起来的镜像带到离线服务器才发现问题。

```bash
docker compose up -d
```

**参数解释**：`-d` 表示后台运行（detached）。

然后观察容器状态：

```bash
docker compose ps
```

**预期结果**：两个容器都显示 `Up`，且 `ees-mysql` 显示 `(healthy)`。

再看后端日志，确认启动成功：

```bash
docker compose logs -f backend
```

看到 `Started EesApplication in xx seconds` 就说明成功了。按 `Ctrl+C` 退出日志跟踪。

**然后用浏览器验证**：打开 `http://127.0.0.1:8080/`
默认账号：`admin / admin123`

**验证通过后，把测试容器停掉**（数据卷会保留）：

```bash
docker compose down
```

> ⚠️ **如果本地 8080 端口被占用**（比如你正在跑本机后端），会报：
> ```
> Error response from daemon: ports are not available: exposing port TCP 0.0.0.0:8080
> -> 127.0.0.1:0: listen tcp 0.0.0.0:8080: bind: Only one usage of each socket address
> ```
> 两种解决办法（任选其一）：
> 1. **停掉本机后端**：任务管理器里找到 `java.exe` → 结束任务；
>    或命令行 `taskkill /PID <进程号> /F`
> 2. **改端口**：把 `docker-compose.yml` 里 `"8080:8080"` 改成 `"8081:8080"`，
>    之后访问 `http://127.0.0.1:8081/`

### A7　导出镜像为 tar 文件

这是离线部署的**核心步骤**：

```bash
docker save -o ees-images.tar ees-backend:1.0.0 mysql:9.6
```

**参数解释**：

| 部分 | 含义 |
|---|---|
| `docker save` | 把镜像导出成文件 |
| `-o ees-images.tar` | 输出文件名 |
| `ees-backend:1.0.0 mysql:9.6` | **可以一次导出多个镜像**，打包进同一个 tar |

> 也可以分开导出（文件更清晰，但要多拷一次）：
> ```bash
> docker save -o ees-backend-1.0.0.tar ees-backend:1.0.0
> docker save -o mysql-9.6.tar mysql:9.6
> ```

导出完成后会出现 `ees-images.tar`，**实测 463 MB**（约 0.45 GB）。

> **为什么两个镜像加起来 1.9 GB，tar 只有 463 MB？**
> 因为镜像层在仓库里本来就是**压缩存储**的，`docker save` 直接搬运压缩后的层，
> 不会解压。这是正常的，不是导出不完整。

**验证 tar 内容是否完整**（可选，但推荐）：

```bash
tar -xOf ees-images.tar manifest.json
```

应该能看到两条 `RepoTags`：

```
"RepoTags":["ees-backend:1.0.0"]
"RepoTags":["mysql:9.6"]
```

### A8　整理要带走的文件

交付文件夹 `docker-offline/` 内容如下（已整理好）：

```
docker-offline/
├── ees-images.tar            ← 核心：两个镜像（463 MB）
├── docker-compose.yml        ← 编排文件
├── Dockerfile                ← 备用（离线机器若要重建时用）
├── .dockerignore             ← 备用
└── 部署说明.md                ← 本教程
```

> **`Dockerfile` 和 `.dockerignore` 其实离线机器上用不到**（镜像已经构建好了），
> 但建议一起带上，方便日后镜像需要重建时使用。

> **关于数据卷：不需要一起带走，也不要带。**
> 你可能在网上看到「要把 `ees-app-data` / `ees-mysql-data` 打包成 tar 一起搬过去」的说法 ——
> **本项目不需要这么做**，原因如下：
>
> 1. **镜像里不含数据，但后端会自动重建全部数据**。启动时会自动执行：
>    - `db/schema.sql` —— 用 **`CREATE TABLE IF NOT EXISTS`** 建 24 张表
>    - `db/data.sql` —— 用 **`INSERT IGNORE`** 灌入角色、配置、质量规则、算法基线等基础数据
>    - 两个脚本**全程幂等**（无任何 `DROP` 语句），重复执行不会报错、不会覆盖已有数据
>    - MySQL 容器由 `MYSQL_DATABASE` 环境变量自动建库、建账号
> 2. **不该把开发/测试数据带到甲方**。你本机卷里的项目、任务、评估结果、审计日志
>    都是测试产生的垃圾数据，交付时应当**干净部署**。
> 3. `ees-app-data` 里的 `.jwt-secret` 会在甲方机器上**首次启动时自动重新生成**，
>    这是正确行为（甲方还没有任何用户，不存在"被踢下线"问题）。
>
> ⇒ **结论：只带 `ees-images.tar` + 配置文件即可，数据卷留在本机。**
>
> **唯一例外**：如果你确实想带一份演示数据去给甲方看，请用项目里的
> `db/99_reset_demo_data.sql`（专门准备演示数据的脚本），而不是搬运你本机的卷。


---

## 四、阶段 B：拷贝到离线服务器

用 U 盘或移动硬盘把 `docker-offline/` 整个文件夹拷到离线服务器上，例如放到：

```
/opt/ees/
```

> **Linux 服务器**用 `scp` 也行：
> ```bash
> scp -r docker-offline/ user@192.168.1.100:/opt/ees/
> ```

---

## 五、阶段 C：在离线服务器上部署

### C1　安装 Docker

离线服务器上没有网络，**不能直接 `apt install docker.io`**。有两种办法：

**办法一（推荐）：用 Docker 官方的离线 deb/rpm 包**

在有网机器上下载好 Docker Engine 的安装包，一起拷过去：

**从哪下载 —— 优先用阿里云镜像源**（国内速度差异很大，实测对比）：

| 下载源 | 实测响应 |
|---|---|
| `https://mirrors.aliyun.com/docker-ce/` ← **推荐** | 0.49s |
| `https://download.docker.com/`（Docker 官方） | 2.26s，且常超时 |

URL 拼接规则：

```
# Ubuntu / Debian（<codename> 换成 ubuntu 的 jammy/noble 或 debian 的 bookworm 等）
https://mirrors.aliyun.com/docker-ce/linux/ubuntu/dists/<codename>/pool/stable/amd64/

# CentOS / RHEL / openEuler 系
https://mirrors.aliyun.com/docker-ce/linux/centos/9/x86_64/stable/Packages/
```

> 在浏览器打开上面的目录地址，按文件修改时间排序，取**最新一版**的这几个文件即可。
> ⚠️ **`amd64` 要换成服务器实际架构** —— 若是 ARM 服务器（鲲鹏/飞腾）则用 `arm64`。

| 系统 | 下载什么 |
|---|---|
| Ubuntu / Debian | `containerd.io_*.deb`、`docker-ce_*.deb`、`docker-ce-cli_*.deb`、`docker-buildx-plugin_*.deb`、`docker-compose-plugin_*.deb` |
| CentOS / RHEL | 对应的 `.rpm` 包 |

然后离线安装：
```bash
sudo dpkg -i *.deb          # Ubuntu/Debian
# 或
sudo yum localinstall -y *.rpm   # CentOS/RHEL
```

**办法二：如果服务器本身是 Windows**

先分清楚是 **Windows 10 / 11** 还是 **Windows Server** —— 这两者差别很大：

| Windows 版本 | 可行 | 怎么做 |
|---|---|---|
| Windows 10 / 11 | ✅ 可以 | 装 **Docker Desktop**（提前下载好安装包），后续操作与本机一致 |
| **Windows Server** | ⚠️ **有坑** | **Docker Desktop 官方不支持 Windows Server**。需 Server 2022+ 启用 WSL2 后手动装 Docker Engine，或在 Hyper-V 里跑一个 Linux 虚拟机再装 Docker |

> **为什么？根本原因**：本系统的两个镜像都是 **Linux 镜像**
> （`eclipse-temurin:21-jre`、`mysql:9.6`），必须由 Linux 内核承载。
> **Windows 容器跑不了 Linux 镜像**，这是硬限制。
>
> ⇒ **强烈建议离线服务器使用 Linux 发行版**，可省去全部额外复杂度。
>
> ⚠️ 另注意：离线环境安装 Docker Desktop 时，安装程序**可能尝试联网更新 WSL2 内核**，
> 需提前把该组件也准备好，否则安装会卡住。

> **补充说明（构建机不需要是 Linux）**：
> 镜像的操作系统由 `Dockerfile` 里的 `FROM` 决定，**与构建机的操作系统无关**。
> Windows 上的 Docker Desktop 内部运行着一个 Linux 虚拟机，`docker build` 实际是在
> 那个 Linux 环境里执行的 —— 所以本机是 Windows 完全没问题，产出的依然是 Linux 镜像。

**安装后验证**：
```bash
docker version
docker compose version
```

### C2　导入镜像

进入放置 tar 的目录：

```bash
cd /opt/ees
```

导入：

```bash
docker load -i ees-images.tar
```

**预期输出**：
```
Loaded image: ees-backend:1.0.0
Loaded image: mysql:9.6
```

> **兼容性说明**：本 tar 为 **OCI 镜像格式**（Docker 25+ 默认）。
> Docker **20.10 及以上**版本均可正常 `docker load`。
> 如果离线服务器上的 Docker 版本过老（< 20.10）报格式不识别，
> 请在**有网机器**上改用 `docker save --format docker -o ees-images.tar ...` 重新导出。

确认导入成功：

```bash
docker images
```

应该能看到 `ees-backend:1.0.0` 和 `mysql:9.6` 两条记录。

### C3　启动

确保 `docker-compose.yml` 与 tar 在同一个目录（或上一级），然后：

```bash
docker compose up -d
```

**首次启动会比较慢**（MySQL 初始化数据库约需 30~60 秒），耐心等待。

查看状态：

```bash
docker compose ps
```

**当 `ees-mysql` 显示 `(healthy)` 后，`ees-backend` 才会开始启动** —— 这是
`depends_on: condition: service_healthy` 在起作用，避免后端因连不上库而反复重启。

跟踪后端日志：

```bash
docker compose logs -f backend
```

看到 `Started EesApplication in xx seconds` 即启动成功。
（**实测**：本机首次启动为 `Started EesApplication in 7.289 seconds`）

> ⚠️ **日志里若出现这一行，属于误报，可忽略：**
> ```
> 前端资源   : 未找到 frontend/dist
>              请先执行 npm run build，或改跑前端 npm run dev 后访问 5173
> ```
> 这是 `StartupBanner.java` 在 prod 档的**误报 bug** —— 它只检查
> `static-locations` 里的 `file:` 路径，没有检查 jar 内嵌的 `classpath:/static/`，
> 而 prod 档恰好没有配 `static-locations`，所以恒判为"未找到"。
> **实际前端是正常的**：浏览器打开 `http://IP:8080/` 会返回 `HTTP 200` 和正确的页面标题。
> 修复方式（可选，一行）见附录。

### C4　在 Docker Desktop 界面里确认

如果离线服务器也是 Windows + Docker Desktop：

1. 左侧点 **Containers**
2. 应该看到两个容器都在 **Running** 状态：

| Name | Image | Status | Ports |
|---|---|---|---|
| `ees-backend` | `ees-backend:1.0.0` | Running | `8080:8080` |
| `ees-mysql` | `mysql:9.6` | Running (healthy) | 3306 |

**点容器名进去**，有三个有用的标签页：

| 标签 | 用途 |
|---|---|
| **Logs** | 看容器日志（排查启动失败） |
| **Exec** | 进容器内部执行命令（例如进 MySQL 容器执行 SQL） |
| **Inspect** | 查看容器详细信息（IP、环境变量、挂载卷） |

**进 MySQL 容器验证数据库**（Exec 标签页里执行）：

```bash
mysql -uroot -pRoot@2026 -e "SHOW DATABASES;"
```

应该能看到 `efficiency_evaluation` 库。

再确认表已自动创建（**实测：24 张表**）：

```bash
mysql -uroot -pRoot@2026 -e "USE efficiency_evaluation; SHOW TABLES;" | wc -l
```

---

## 六、验证部署结果

在浏览器打开：

```
http://<离线服务器的IP>:8080/
```

例如 `http://192.168.1.100:8080/`

**默认账号**：

| 账号 | 密码 | 角色 |
|---|---|---|
| `admin` | `admin123` | 系统管理员 |
| `evaluator` | `eval123` | 评估人员 |

能看到登录页面并成功登录，就说明部署完成了。

---

## 七、日常运维（全部可在 Docker Desktop 界面完成）

| 操作 | Docker Desktop 界面 | 命令行 |
|---|---|---|
| 查看容器状态 | Containers 页面 | `docker compose ps` |
| 查看日志 | 点容器 → Logs | `docker compose logs -f backend` |
| 重启后端 | 点容器 → 右上角重启按钮 | `docker compose restart backend` |
| 停止全部 | 逐个点 Stop | `docker compose down` |
| 重新启动 | — | `docker compose up -d` |
| 进容器终端 | 点容器 → Exec | `docker exec -it ees-backend sh` |
| 查看数据卷 | Volumes 页面 | `docker volume ls` |

**⚠️ 绝对不要执行 `docker compose down -v`** —— 那个 `-v` 会连数据卷一起删除，
**数据库里的全部业务数据会永久丢失**。

**两个数据卷的作用**：

| 卷名 | 挂载点 | 存什么 | 删了会怎样 |
|---|---|---|---|
| `ees-mysql-data` | `/var/lib/mysql` | 全部业务数据 | **数据永久丢失** |
| `ees-app-data` | `/app/data` | 上传文件、报告、**JWT 签名密钥** | 用户被强制重新登录，已上传文件丢失 |

---

## 八、常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| `failed to connect to the docker API` | Docker Desktop 没启动 | 双击图标启动，等鲸鱼变绿 |
| `docker build` 卡在下载基础镜像 | 镜像源不通 | 检查 `~/.docker/daemon.json` 的 `registry-mirrors`；或在 Docker Desktop 设置里配代理 |
| `docker pull` 报 `short read: ... unexpected EOF` | 镜像源网络中断 | 重试；或换镜像源；或改用本机已有的版本 |
| `ports are not available: ... 8080` | 宿主机 8080 被占用 | 停掉占用进程（`taskkill /PID xxx /F`）或改端口映射 |
| 后端反复重启 | MySQL 还没就绪 | 等 `ees-mysql` 变成 `(healthy)`；`docker compose logs backend` 看报错 |
| 后端报 `Access denied for user 'ees'` | 密码不匹配 | 检查 `docker-compose.yml` 里 `MYSQL_PASSWORD` 与 `EES_DB_PASSWORD` 是否一致 |
| 日志报"未找到 frontend/dist" | `StartupBanner` 误报 | **忽略**，前端实际正常（见附录） |
| 8080 端口打不开 | 端口被占用或防火墙 | `docker compose ps` 看端口映射；服务器防火墙放行 8080 |
| 重启后所有人都要重新登录 | `ees-app-data` 卷没挂上，或被 `down -v` 删了 | 确认 `docker-compose.yml` 里 `EES_STORAGE_ROOT: /app/data` 与卷挂载都在；**只有 `docker compose down -v` 会删卷，正常 `down` 不会** |
| 页面打开是 404 | 访问路径不对 | 必须访问 `http://IP:8080/`，**不要加 `/api` 前缀** |
| `docker compose` 报 `version is obsolete` | 旧写法 | 本文件的 `docker-compose.yml` **已删除 `version:` 字段**，不会有此警告 |
| **需要把数据卷也拷到服务器吗？** | 不需要 | 数据库表和初始数据由后端**启动时自动创建**（`schema.sql` 用 `CREATE TABLE IF NOT EXISTS`、`data.sql` 用 `INSERT IGNORE`，全程幂等）。详见 A8 的说明 |
| 重启容器后数据会不会丢？ | 不会 | `schema.sql` 无任何 `DROP` 语句，`data.sql` 用 `INSERT IGNORE`，重复执行不影响已有数据；业务数据存在 `ees-mysql-data` 卷里，`restart` / `down` 都不会删卷 |

---

## 九、交付清单

给甲方交付时，建议打包以下内容：

| 文件/目录 | 必需 | 说明 |
|---|---|---|
| `ees-images.tar` | ✅ | 两个镜像（实测 463 MB） |
| `docker-compose.yml` | ✅ | 编排配置 |
| `Dockerfile` + `.dockerignore` | 建议 | 日后重建镜像用 |
| `部署说明.md`（本文件） | ✅ | 操作手册 |
| `docs/` 目录 | 建议 | 使用手册、验收清单、接口文档 |

**部署前必须修改的两处默认口令**（`docker-compose.yml`）：

```yaml
MYSQL_ROOT_PASSWORD: "Root@2026"    # ← 改成甲方指定的强口令
MYSQL_PASSWORD:      "Ees@2026"     # ← 同上
EES_DB_PASSWORD:     "Ees@2026"     # ← 必须与上面 MYSQL_PASSWORD 一致
```

另外建议显式指定 JWT 密钥（否则每次重建容器都会重新生成，导致用户被踢下线）：

```yaml
EES_JWT_SECRET: "请替换为一段足够长的随机字符串"
```

> 生成随机串的方法（有网机器上执行）：
> ```bash
> openssl rand -base64 48
> ```

---

## 附录：`StartupBanner` 前端资源误报的修复（可选）

**文件**：`backend/src/main/java/com/ees/config/StartupBanner.java`
**方法**：`frontendAvailable()`（约 102~120 行）

**问题**：该方法只识别以 `file:` 开头的 `static-locations`，完全忽略
`classpath:/static/`；而 `application-prod.yml` 未配置 `static-locations`，
导致 `getProperty("spring.web.resources.static-locations", "")` 返回空串，
循环不执行 → **恒返回 false** → 必然打印"未找到 frontend/dist"。

**建议修复**：在 `return false;` 之前追加一条 classpath 探测：

```java
// 追加：探测 jar 内嵌静态资源（prod 档前端由 jar 托管）
if (getClass().getResource("/static/index.html") != null) {
    return true;
}
```

> 该问题**仅影响启动日志提示文案**，不影响任何功能，
> 属于 P3 级（无害）。已列入代码问题交接单，可随下个版本一并修复。

---

## 附录二：本次实测记录（2026-10-07）

| 环节 | 实测结果 |
|---|---|
| `docker build` 构建上下文 | 1.18 kB |
| 后端镜像 | `ees-backend:1.0.0` = 645 MB |
| MySQL 镜像 | `mysql:9.6` = 1.27 GB |
| 容器启动 | `Started EesApplication in 7.289 seconds` |
| 页面响应 | `HTTP 200`，`<title>效能评估系统</title>` |
| 自动建表 | 24 张表，`admin` / `evaluator` 状态 ENABLED |
| JWT 密钥持久化 | 已保存到 `/app/data/.jwt-secret` |
| `docker save` 导出 | `ees-images.tar` = 463 MB，含两个镜像 |
| tar 格式 | OCI 格式（Docker 25+ 默认） |

### `ees-images.tar` 校验值（拷贝后请核对）

| 项 | 值 |
|---|---|
| 文件名 | `ees-images.tar` |
| 字节数 | `485375488` |
| 大小 | `462.9 MB`（0.45 GB） |
| **SHA256** | `ad660191475052b2bb6636b83d768dd9d9a2aa9e6642f5c2ac0282fbd8b969aa` |

**拷到离线服务器后，先核对校验值再导入**（U 盘/网络传输偶发损坏时，
`docker load` 会报一堆看不懂的错，提前校验能省很多排查时间）：

```bash
# Linux
sha256sum ees-images.tar

# Windows（PowerShell 或 CMD）
certutil -hashfile ees-images.tar SHA256
```

输出的哈希串必须与上表**完全一致**（Windows 的 certutil 输出是大写且带空格，
大小写不敏感，去掉空格后对比即可）。

**如果不一致**：说明文件传坏了，**不要继续导入**，重新拷贝一次。
若重拷仍不一致，检查 U 盘是否有坏块。
