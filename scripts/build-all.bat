@echo off
REM ===========================================================================
REM  效能评估系统 - 构建（前端静态资源 + 后端 jar）
REM  脚本以 GBK(936) 编码保存，与中文 Windows 的 cmd 默认代码页一致
REM
REM  企业离线交付说明：
REM    本脚本不写死任何本机绝对路径，全部以脚本所在目录为基准。
REM    构建顺序不能颠倒：后端打包时会把 frontend\dist 复制进 jar 的
REM    classpath:/static/，因此必须先构建前端，再构建后端。
REM ===========================================================================
setlocal
title 效能评估系统 - 构建

set ROOT=%~dp0..
set BACKEND=%ROOT%\backend
set FRONTEND=%ROOT%\frontend

echo ================================================================
echo   构建效能评估系统（前端静态资源 + 后端 jar）
echo ================================================================
echo.

echo [1/2] 构建前端 ...
if not exist "%FRONTEND%\package.json" (
  echo   [ERROR] 未找到前端工程：%FRONTEND%
  pause
  exit /b 1
)
where npm >nul 2>&1
if errorlevel 1 (
  echo   [ERROR] 未找到 npm，请先安装 Node.js 并加入 PATH。
  pause
  exit /b 1
)
pushd "%FRONTEND%"
if not exist "node_modules" (
  echo       首次构建，正在安装前端依赖 ...
  call npm install --no-fund --no-audit
)
call npm run build
if errorlevel 1 (
  echo   [ERROR] 前端构建失败
  popd
  pause
  exit /b 1
)
popd
if not exist "%FRONTEND%\dist\index.html" (
  echo   [ERROR] 前端构建产物缺失：%FRONTEND%\dist\index.html
  pause
  exit /b 1
)
echo   [OK] 产物：%FRONTEND%\dist

echo.
echo [2/2] 构建后端（并自动把前端产物打进 jar）...
if not exist "%BACKEND%\pom.xml" (
  echo   [ERROR] 未找到后端工程：%BACKEND%
  pause
  exit /b 1
)
where mvn >nul 2>&1
if errorlevel 1 (
  echo   [ERROR] 未找到 mvn，请先安装 Maven 并加入 PATH。
  pause
  exit /b 1
)
pushd "%BACKEND%"
call mvn -B -ntp clean package -DskipTests
if errorlevel 1 (
  echo   [ERROR] 后端构建失败
  popd
  pause
  exit /b 1
)
popd
echo   [OK] 产物：%BACKEND%\target\evaluation-backend.jar

echo.
echo ================================================================
echo   构建完成
echo     后端（已内含前端页面）：%BACKEND%\target\evaluation-backend.jar
echo     前端静态资源目录     ：%FRONTEND%\dist
echo.
echo   企业离线部署：只需拷走 evaluation-backend.jar，目标机器装好
echo   JDK 21 与 MySQL 后执行 scripts\start-offline.bat 即可，
echo   无需 Node，也无需 Nginx。
echo ================================================================
pause
endlocal
