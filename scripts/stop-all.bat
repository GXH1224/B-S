@echo off
REM ===========================================================================
REM  效能评估系统 - 停止全部服务（前端 + 后端 + MySQL）
REM  脚本以 GBK(936) 编码保存，与中文 Windows 的 cmd 默认代码页一致
REM
REM  企业离线交付说明：
REM    不写死任何本机绝对路径。MySQL 仅在本机存在对应 Windows 服务时才尝试
REM    停止；若数据库由甲方 DBA 维护（或部署在其它主机），本脚本不会去动它。
REM ===========================================================================
setlocal enabledelayedexpansion
title 效能评估系统 - 停止全部服务

echo ================================================================
echo   停止效能评估系统全部服务
echo ================================================================
echo.

echo [1/3] 关闭 EES 服务窗口 ...
taskkill /FI "WINDOWTITLE eq EES-Frontend*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq EES-Backend*" /T /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq EES-MySQL*" /T /F >nul 2>&1

echo [2/3] 释放端口 8080 / 5173 ...
call :killport 8080
call :killport 5173

echo [3/3] 停止 MySQL ...
call :stop_mysql

echo.
echo 全部服务已停止。
endlocal
exit /b 0

:killport
set PORT=%~1
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":%PORT%" ^| findstr "LISTENING"') do (
  echo       结束占用 %PORT% 的进程 PID=%%p
  taskkill /PID %%p /F >nul 2>&1
)
exit /b 0

REM ---------------------------------------------------------------------------
REM  停止 MySQL：优先使用环境变量 EES_MYSQL_STOP，其次自动探测 Windows 服务。
REM  探测不到时只提示，避免误停甲方自行维护的数据库。
REM ---------------------------------------------------------------------------
:stop_mysql
if defined EES_MYSQL_STOP (
  echo       使用 EES_MYSQL_STOP 指定的停止命令 ...
  call "%EES_MYSQL_STOP%"
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
  echo   [WARN] 本机未检测到 MySQL 的 Windows 服务，未执行停止操作。
  echo          如需一并停止，可设置环境变量 EES_MYSQL_STOP 指向停止脚本。
  exit /b 0
)

echo       正在停止 MySQL 服务 %MYSQL_SVC% ...
net stop "%MYSQL_SVC%" >nul 2>&1
if errorlevel 1 (
  echo   [WARN] MySQL 服务 %MYSQL_SVC% 停止失败（可能未在运行）。
) else (
  echo   [OK] MySQL 服务 %MYSQL_SVC% 已停止
)
exit /b 0
