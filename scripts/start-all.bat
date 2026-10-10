@echo off
REM ===========================================================================
REM  效能评估系统 - 一键启动（MySQL + 后端 + 前端）
REM  脚本以 GBK(936) 编码保存，与中文 Windows 的 cmd 默认代码页一致
REM
REM  企业离线交付说明：
REM    本脚本不写死任何本机绝对路径。MySQL 通过"环境变量 -> Windows 服务"
REM    两级方式自动探测，探测不到只提示、不中断，便于在甲方环境（数据库由
REM    甲方 DBA 统一维护）中直接使用。
REM ===========================================================================
setlocal enabledelayedexpansion
title 效能评估系统 - 一键启动

set ROOT=%~dp0..
set BACKEND=%ROOT%\backend
set FRONTEND=%ROOT%\frontend
set BACKEND_URL=http://127.0.0.1:8080
set FRONTEND_URL=http://127.0.0.1:5173

echo ================================================================
echo   效能评估系统 - 一键启动
echo ================================================================
echo.

echo [1/4] 检查运行环境 ...
where java >nul 2>&1
if errorlevel 1 (
  echo   [ERROR] 未找到 java，请先安装 JDK 21 并加入 PATH。
  echo           可通过环境变量 JAVA_HOME 指定安装目录。
  pause
  exit /b 1
)
where mvn >nul 2>&1
if errorlevel 1 (
  echo   [ERROR] 未找到 mvn，请先安装 Maven 并加入 PATH。
  pause
  exit /b 1
)
where npm >nul 2>&1
if errorlevel 1 (
  echo   [ERROR] 未找到 npm，请先安装 Node.js 并加入 PATH。
  pause
  exit /b 1
)
echo   [OK] java / mvn / npm 均可用

echo.
echo [2/4] 启动 MySQL ...
call :start_mysql

echo.
echo [3/4] 启动后端服务（端口 8080）...
if not exist "%BACKEND%\pom.xml" (
  echo   [ERROR] 未找到后端工程：%BACKEND%
  pause
  exit /b 1
)
start "EES-Backend" cmd /k "cd /d %BACKEND% && mvn spring-boot:run"

echo       等待后端就绪 ...
set /a COUNT=0
:waitback
set /a COUNT+=1
call :probe %BACKEND_URL%/api/health
if !ERRORLEVEL! EQU 0 goto backok
if !COUNT! GEQ 40 (
  echo   [WARN] 后端启动较慢，请查看 EES-Backend 窗口日志
  goto backok
)
ping -n 4 127.0.0.1 >nul
goto waitback

:backok
echo   [OK] 后端已响应 %BACKEND_URL%/api/health

echo.
echo [4/4] 启动前端开发服务器（端口 5173）...
if not exist "%FRONTEND%\package.json" (
  echo   [ERROR] 未找到前端工程：%FRONTEND%
  pause
  exit /b 1
)
if not exist "%FRONTEND%\node_modules" (
  echo       首次运行，正在安装前端依赖 ...
  pushd "%FRONTEND%"
  call npm install --no-fund --no-audit
  popd
)
start "EES-Frontend" cmd /k "cd /d %FRONTEND% && npm run dev"

ping -n 9 127.0.0.1 >nul
echo.
echo ================================================================
echo   启动完成
echo     前端地址 : %FRONTEND_URL%
echo     后端地址 : %BACKEND_URL%
echo     健康检查 : %BACKEND_URL%/api/health
echo     默认账号 : admin / admin123
echo ----------------------------------------------------------------
echo   提示：若已执行过 scripts\build-all.bat，也可只运行后端
echo         （后端会直接托管 frontend\dist，单进程即可访问 8080）
echo ================================================================
start "" %FRONTEND_URL%
endlocal
exit /b 0

REM ---------------------------------------------------------------------------
REM  启动 MySQL：优先使用环境变量 EES_MYSQL_START，其次自动探测 Windows 服务
REM ---------------------------------------------------------------------------
:start_mysql
if defined EES_MYSQL_START (
  echo       使用 EES_MYSQL_START 指定的启动命令 ...
  call "%EES_MYSQL_START%"
  if errorlevel 1 (
    echo   [WARN] EES_MYSQL_START 执行失败，请确认数据库已启动。
  ) else (
    echo   [OK] 已按 EES_MYSQL_START 启动 MySQL
  )
  exit /b 0
)

set MYSQL_SVC=
for %%S in (MySQL80 MySQL84 MySQL57 MySQL56 MySQL) do (
  if not defined MYSQL_SVC (
    sc query "%%S" >nul 2>&1
    if not errorlevel 1 set MYSQL_SVC=%%S
  )
)

if not defined MYSQL_SVC (
  echo   [WARN] 本机未检测到 MySQL 的 Windows 服务（已尝试 MySQL80/84/57/56/MySQL）。
  echo          如果数据库部署在其它主机，或由甲方 DBA 维护，请忽略本提示；
  echo          需要本脚本代为启动时，可设置环境变量 EES_MYSQL_START 指向启动脚本。
  exit /b 0
)

sc query "%MYSQL_SVC%" | findstr /I "RUNNING" >nul 2>&1
if not errorlevel 1 (
  echo   [OK] MySQL 服务 %MYSQL_SVC% 已在运行
  exit /b 0
)

echo       正在启动 MySQL 服务 %MYSQL_SVC% ...
net start "%MYSQL_SVC%" >nul 2>&1
if errorlevel 1 (
  echo   [WARN] MySQL 服务 %MYSQL_SVC% 启动失败。
  echo          常见原因：当前不是管理员身份运行（启动/停止服务需要管理员权限），
  echo          或数据库服务被甲方策略禁用。可手动执行：
  echo              net start %MYSQL_SVC%
) else (
  echo   [OK] MySQL 服务 %MYSQL_SVC% 已启动
)
exit /b 0

REM ---------------------------------------------------------------------------
REM  探测 URL 是否可访问：优先 curl，缺失时退回 PowerShell
REM ---------------------------------------------------------------------------
:probe
where curl >nul 2>&1
if errorlevel 1 goto probe_by_powershell
curl -s -f -o nul %~1
exit /b %ERRORLEVEL%
:probe_by_powershell
powershell -NoProfile -Command "try{Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 '%~1' | Out-Null; exit 0}catch{exit 1}" >nul 2>&1
exit /b %ERRORLEVEL%
