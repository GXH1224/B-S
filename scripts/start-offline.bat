@echo off
REM ===========================================================================
REM  效能评估系统 - 离线启动（生产档 prod，单进程）
REM  脚本以 GBK(936) 编码保存，与中文 Windows 的 cmd 默认代码页一致
REM
REM  企业离线交付说明：
REM    1. 本脚本不写死任何本机绝对路径，全部以脚本所在目录为基准；
REM    2. 使用 prod 生产档：关闭调试日志、不打印详细异常堆栈；
REM    3. 前端页面已打进 jar（classpath:/static/），单进程即可访问，
REM       目标机器无需 Node，也无需 Nginx，完全离线可用；
REM    4. 数据库连接参数全部来自环境变量，未设置时给出可读的默认值，
REM       密码未设置时会提示输入，避免把口令硬编码进交付物。
REM ===========================================================================
setlocal enabledelayedexpansion
title 效能评估系统 - 离线启动

set ROOT=%~dp0..
set BACKEND=%ROOT%\backend
set JAR=target\evaluation-backend.jar
set PORT=8080
set URL=http://127.0.0.1:%PORT%/

echo ================================================================
echo   效能评估系统 - 离线启动（生产档 prod）
echo ================================================================
echo.

echo [1/4] 检查运行环境 ...
where java >nul 2>&1
if errorlevel 1 (
  echo   [ERROR] 未找到 java，请先安装 JDK 21 并加入 PATH。
  pause
  exit /b 1
)
if not exist "%BACKEND%\%JAR%" (
  echo   [ERROR] 未找到后端程序：%BACKEND%\%JAR%
  echo           请先执行 scripts\build-all.bat 完成构建。
  pause
  exit /b 1
)
echo   [OK] java 可用，后端程序已就绪

echo.
echo [2/4] 准备数据库连接参数 ...
if not defined EES_DB_HOST set EES_DB_HOST=127.0.0.1
if not defined EES_DB_PORT set EES_DB_PORT=3306
if not defined EES_DB_NAME set EES_DB_NAME=efficiency_evaluation
if not defined EES_DB_USER set EES_DB_USER=ees

if not defined EES_DB_PASSWORD (
  echo   连接目标：%EES_DB_USER%@%EES_DB_HOST%:%EES_DB_PORT%/%EES_DB_NAME%
  echo   提示：先设置环境变量 EES_DB_PASSWORD 可跳过本次输入。
  set /p EES_DB_PASSWORD=  请输入数据库密码:
)
if not defined EES_DB_PASSWORD (
  echo   [ERROR] 未提供数据库密码，已终止。
  pause
  exit /b 1
)
echo   [OK] 连接目标：%EES_DB_USER%@%EES_DB_HOST%:%EES_DB_PORT%/%EES_DB_NAME%
echo        如数据库未启动，请先启动 MySQL 服务。

echo.
echo [3/4] 启动后端服务（端口 %PORT%，生产档）...
start "EES-Backend" /D "%BACKEND%" cmd /k "java -Dfile.encoding=UTF-8 -jar %JAR% --spring.profiles.active=prod"

echo       等待后端就绪 ...
set /a COUNT=0
:waitback
set /a COUNT+=1
call :probe %URL%api/health
if !ERRORLEVEL! EQU 0 goto backok
if !COUNT! GEQ 40 (
  echo   [WARN] 后端启动较慢，请查看 EES-Backend 窗口的输出。
  goto backok
)
ping -n 4 127.0.0.1 >nul
goto waitback

:backok
if !COUNT! LSS 40 (
  echo   [OK] 后端已响应 %URL%api/health
)

echo.
echo [4/4] 打开页面 ...
start "" %URL%
ping -n 2 127.0.0.1 >nul

echo.
echo ================================================================
echo   启动完成
echo     页面地址 : %URL%
echo     默认账号 : admin / admin123
echo     运行日志 : %BACKEND%\data\logs\evaluation-backend.log
echo ----------------------------------------------------------------
echo   关闭系统：执行 scripts\stop-all.bat，或直接关闭 EES-Backend 窗口
echo ================================================================
endlocal
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
