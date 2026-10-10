@echo off
REM ===========================================================================
REM  EES - one-time database initialisation.
REM
REM  Creates the database "efficiency_evaluation" and the application account
REM  "ees", WITHOUT touching the root password.
REM
REM  Why not the bundled db\00_create_database.sql:
REM    that one contains ALTER USER 'root'@'localhost' ... BY 'Root@2026',
REM    which would overwrite the root password you already have on this machine.
REM
REM  Table structure and seed data are deliberately NOT created here:
REM    the backend jar runs its own db/schema.sql (24 tables, all
REM    CREATE TABLE IF NOT EXISTS) and db/data.sql (INSERT IGNORE) on startup,
REM    and both are idempotent.
REM
REM  Usage: double-click, or run from the VS Code task panel.
REM  NOTE: pure ASCII on purpose - the project path contains Chinese characters,
REM        and a literal Chinese string inside a .bat would be mis-decoded
REM        under the GBK code page.
REM ===========================================================================
setlocal
set "ROOT=%~dp0.."
set "MYSQL=C:\Program Files\MySQL\MySQL Server 9.6\bin\mysql.exe"
set "SQL=%ROOT%\db\00_create_database_safe.sql"

echo ================================================================
echo   EES - initialise database (one time only)
echo ================================================================
echo.

if not exist "%MYSQL%" (
  echo [ERROR] MySQL client not found:
  echo         %MYSQL%
  call :maybe_pause
  exit /b 1
)
if not exist "%SQL%" (
  echo [ERROR] SQL script not found:
  echo         %SQL%
  call :maybe_pause
  exit /b 1
)

if not defined EES_ROOT_PASSWORD set /p "EES_ROOT_PASSWORD=MySQL root password: "
if not defined EES_ROOT_PASSWORD (
  echo [ERROR] No password supplied.
  call :maybe_pause
  exit /b 1
)

REM Passing the password via MYSQL_PWD avoids all cmd quoting problems.
set "MYSQL_PWD=%EES_ROOT_PASSWORD%"

echo.
echo [1/2] Creating database and account ...
"%MYSQL%" -uroot -h 127.0.0.1 -P 3306 --default-character-set=utf8mb4 < "%SQL%"
if errorlevel 1 (
  echo.
  echo [ERROR] Failed. Check the root password and that MySQL is running.
  set "MYSQL_PWD="
  call :maybe_pause
  exit /b 1
)

echo.
echo [2/2] Verifying ...
"%MYSQL%" -uroot -h 127.0.0.1 -P 3306 -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='efficiency_evaluation';"
echo   ^(table count above - 0 is expected, the backend creates them on start^)

set "MYSQL_PWD="
echo.
echo [OK] Database and account are ready.
echo      Next: run the VS Code task "Start system (jar / 8080)".
call :maybe_pause
endlocal
exit /b 0

REM ---------------------------------------------------------------------------
REM  Pause only when NOT running inside the VS Code integrated terminal,
REM  so a double-click keeps the window open but a task does not hang.
REM ---------------------------------------------------------------------------
:maybe_pause
if /i "%TERM_PROGRAM%"=="vscode" exit /b 0
pause
exit /b 0
