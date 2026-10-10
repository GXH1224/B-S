@echo off
setlocal
title EES - Cleanup load-test data

REM ============================================================
REM  Removes data created by the JMeter load test:
REM    - project rows whose project_code starts with PERF-
REM    - their project_member rows
REM
REM  The demo project (PRJ-2026-001) is never touched.
REM  Safe to run repeatedly.
REM ============================================================

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 9.6\bin"
set "DB=efficiency_evaluation"
set "DBUSER=ees"
set "MYSQL_PWD=Ees@2026"

if not exist "%MYSQL_BIN%\mysql.exe" (
  echo [ERROR] mysql.exe not found at:
  echo         %MYSQL_BIN%
  pause
  exit /b 1
)

echo ============================================================
echo   Before cleanup
echo ============================================================
"%MYSQL_BIN%\mysql.exe" -h 127.0.0.1 -P 3306 -u %DBUSER% %DB% -e "SELECT COUNT(*) AS perf_projects FROM project WHERE project_code LIKE 'PERF-%%';"

echo.
echo Cleaning up ...
"%MYSQL_BIN%\mysql.exe" -h 127.0.0.1 -P 3306 -u %DBUSER% %DB% --default-character-set=utf8mb4 -e "DELETE FROM project_member WHERE project_id IN (SELECT id FROM project WHERE project_code LIKE 'PERF-%%'); DELETE FROM project WHERE project_code LIKE 'PERF-%%';"

echo.
echo ============================================================
echo   After cleanup - remaining projects
echo ============================================================
"%MYSQL_BIN%\mysql.exe" -h 127.0.0.1 -P 3306 -u %DBUSER% %DB% --default-character-set=utf8mb4 -e "SELECT id, project_code, project_name, status FROM project ORDER BY id;"

echo.
echo Done.
pause
endlocal
