@echo off
REM ===========================================================================
REM  Efficiency Evaluation System - LOCAL LAUNCHER (for this machine only)
REM
REM  Difference vs scripts\start-offline.bat:
REM    this one pins JDK 21 explicitly (D:\Dev\tools\jdk-21\bin\java.exe),
REM    because the "java" on PATH here is JDK 17 and cannot run this jar
REM    (jar classes are Java 21 bytecode, major version 65).
REM
REM  This file is pure ASCII on purpose, so it is safe under any code page.
REM ===========================================================================
setlocal enabledelayedexpansion
title EES - local launcher

set ROOT=%~dp0
set JAVA_EXE=D:\Dev\tools\jdk-21\bin\java.exe
set JAR=%ROOT%backend\target\evaluation-backend.jar
set PORT=8080
set URL=http://127.0.0.1:%PORT%/

echo ================================================================
echo   Efficiency Evaluation System - local launch (JDK21 / prod)
echo ================================================================
echo.

echo [1/4] Checking runtime ...
if not exist "%JAVA_EXE%" (
  echo   [ERROR] JDK 21 not found: %JAVA_EXE%
  echo           Extract JDK 21 there, or edit JAVA_EXE in this file.
  pause
  exit /b 1
)
if not exist "%JAR%" (
  echo   [ERROR] Backend jar not found: %JAR%
  pause
  exit /b 1
)
echo   [OK] JDK 21 found, backend jar ready

echo.
echo [2/4] Preparing database parameters ...
if not defined EES_DB_HOST set EES_DB_HOST=127.0.0.1
if not defined EES_DB_PORT set EES_DB_PORT=3306
if not defined EES_DB_NAME set EES_DB_NAME=efficiency_evaluation
if not defined EES_DB_USER set EES_DB_USER=ees
if not defined EES_DB_PASSWORD set EES_DB_PASSWORD=Ees@2026
echo   [OK] Target: %EES_DB_USER%@%EES_DB_HOST%:%EES_DB_PORT%/%EES_DB_NAME%
echo        If MySQL is down, start the Windows service MySQL96 first.

echo.
echo [3/4] Starting backend on port %PORT% ...
start "EES-Backend" /D "%ROOT%backend" "%JAVA_EXE%" -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.profiles.active=prod

echo       Waiting for backend ...
set /a COUNT=0
:waitback
set /a COUNT+=1
curl -s -f -o nul %URL%api/health
if !ERRORLEVEL! EQU 0 goto backok
if !COUNT! GEQ 40 (
  echo   [WARN] Backend is slow to start. Check the EES-Backend window.
  goto backok
)
ping -n 4 127.0.0.1 >nul
goto waitback

:backok
if !COUNT! LSS 40 echo   [OK] Backend responded at %URL%api/health

echo.
echo [4/4] Opening browser ...
start "" %URL%
ping -n 2 127.0.0.1 >nul

echo.
echo ================================================================
echo   READY
echo     URL      : %URL%
echo     Accounts : admin / admin123   evaluator / eval123
echo     Log file : %ROOT%backend\data\logs\evaluation-backend.log
echo ----------------------------------------------------------------
echo   To stop: run scripts\stop-all.bat, or close the EES-Backend window
echo ================================================================
endlocal
exit /b 0
