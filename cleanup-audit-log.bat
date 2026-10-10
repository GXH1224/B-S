@echo off
setlocal
title EES - Cleanup audit log

REM ============================================================
REM  Cleans sys_audit_log.
REM
REM  Usage:
REM    cleanup-audit-log.bat        - delete ALL rows (TRUNCATE)
REM    cleanup-audit-log.bat 7      - delete rows older than 7 days
REM
REM  Why this script exists:
REM    sys_audit_log is the only append-only table in the system.
REM    The app has no scheduled cleanup task, so load tests and daily
REM    use keep growing it forever. The in-app "clean expired logs"
REM    button only removes rows older than the retention days setting
REM    (default 365), which deletes nothing right after a load test.
REM ============================================================

set "MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 9.6\bin"
set "DB=efficiency_evaluation"
set "DBUSER=ees"
set "MYSQL_PWD=Ees@2026"
set "MYSQL=%MYSQL_BIN%\mysql.exe"
set "KEEP_DAYS=%~1"

if not exist "%MYSQL%" goto :nomysql

echo ============================================================
echo   Before
echo ============================================================
"%MYSQL%" -h 127.0.0.1 -P 3306 -u %DBUSER% %DB% --default-character-set=utf8mb4 -e "SELECT COUNT(*) AS total_rows, MIN(created_at) AS earliest, MAX(created_at) AS latest FROM sys_audit_log; SELECT ROUND((data_length+index_length)/1024/1024,2) AS size_mb FROM information_schema.TABLES WHERE TABLE_SCHEMA='efficiency_evaluation' AND TABLE_NAME='sys_audit_log';"

if not defined KEEP_DAYS goto :truncate

echo.
echo Deleting rows older than %KEEP_DAYS% day(s) ...
"%MYSQL%" -h 127.0.0.1 -P 3306 -u %DBUSER% %DB% --default-character-set=utf8mb4 -e "DELETE FROM sys_audit_log WHERE created_at < DATE_SUB(NOW(), INTERVAL %KEEP_DAYS% DAY);"
goto :after

:truncate
echo.
echo Truncating sys_audit_log - removing all rows ...
"%MYSQL%" -h 127.0.0.1 -P 3306 -u %DBUSER% %DB% --default-character-set=utf8mb4 -e "TRUNCATE TABLE sys_audit_log;"

:after
echo.
echo ============================================================
echo   After
echo ============================================================
"%MYSQL%" -h 127.0.0.1 -P 3306 -u %DBUSER% %DB% --default-character-set=utf8mb4 -e "SELECT COUNT(*) AS total_rows FROM sys_audit_log;"

echo.
echo Done.
pause
exit /b 0

:nomysql
echo [ERROR] mysql.exe not found at:
echo         %MYSQL_BIN%
pause
exit /b 1
