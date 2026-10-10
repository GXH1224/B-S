@echo off
setlocal

REM ============================================================
REM  EES backend launcher - prod profile, port 8080
REM
REM  evaluation-backend.jar is Java 21 bytecode (class major 65).
REM  The java on PATH on this machine is JDK 17, so running the
REM  jar with bare "java" aborts with UnsupportedClassVersionError.
REM  (scripts\start-offline.bat uses bare java and therefore fails
REM   on this machine.) This script locates a real JDK 21 first.
REM
REM  Search order:
REM    1. EES_JAVA_HOME
REM    2. D:\Dev\tools\jdk-21
REM    3. C:\Program Files\Java\jdk-21
REM    4. JAVA_HOME (accepted only when it really is 21)
REM    5. every java on PATH (first one reporting 21)
REM
REM  Note: the version probe below deliberately avoids
REM  for /f ('"path" -version'), because cmd mis-parses a quoted
REM  command inside for /f and fails with
REM  "The filename, directory name, or volume label syntax is incorrect."
REM ============================================================

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "JAR=target\evaluation-backend.jar"
set "PORT=8080"

if not exist "%BACKEND%\%JAR%" goto no_jar

set "JAVA_EXE="
call :pick "%EES_JAVA_HOME%\bin\java.exe"
call :pick "D:\Dev\tools\jdk-21\bin\java.exe"
call :pick "C:\Program Files\Java\jdk-21\bin\java.exe"
call :pick "%JAVA_HOME%\bin\java.exe"
if not defined JAVA_EXE (
  for /f "delims=" %%p in ('where java 2^>nul') do call :pick "%%p"
)
if not defined JAVA_EXE goto no_java
echo [OK] JDK 21 : %JAVA_EXE%

if not defined EES_DB_HOST     set "EES_DB_HOST=127.0.0.1"
if not defined EES_DB_PORT     set "EES_DB_PORT=3306"
if not defined EES_DB_NAME     set "EES_DB_NAME=efficiency_evaluation"
if not defined EES_DB_USER     set "EES_DB_USER=ees"
if not defined EES_DB_PASSWORD set "EES_DB_PASSWORD=Ees@2026"

netstat -ano | findstr ":%PORT%" | findstr "LISTENING" >nul 2>&1
if not errorlevel 1 goto port_busy

cd /d "%BACKEND%"

echo.
echo ============================================================
echo   EES backend  -  prod profile
echo ------------------------------------------------------------
echo   Page     : http://127.0.0.1:%PORT%/
echo   Health   : http://127.0.0.1:%PORT%/api/health
echo   Account  : admin / admin123
echo   Database : %EES_DB_USER%@%EES_DB_HOST%:%EES_DB_PORT%/%EES_DB_NAME%
echo   Log      : %BACKEND%\data\logs\evaluation-backend.log
echo ------------------------------------------------------------
echo   Stop     : press Ctrl+C in this window
echo ============================================================
echo.

"%JAVA_EXE%" -Dfile.encoding=UTF-8 -jar "%JAR%" --spring.profiles.active=prod --server.port=%PORT%

echo.
echo Backend stopped.
pause
exit /b 0

:no_jar
echo [ERROR] Jar not found:
echo         %BACKEND%\%JAR%
echo         Run scripts\build-all.bat first.
pause
exit /b 1

:no_java
echo [ERROR] No JDK 21 found on this machine.
echo         Set EES_JAVA_HOME to a JDK 21 folder and run again, e.g.
echo           set EES_JAVA_HOME=D:\Dev\tools\jdk-21
pause
exit /b 1

:port_busy
echo [WARN] Port %PORT% is already in use. Stop the old process first:
echo          netstat -ano ^| findstr :%PORT%
echo          taskkill /F /PID ^<pid^>
pause
exit /b 1

:pick
if defined JAVA_EXE exit /b 0
if not exist "%~1" exit /b 0
"%~1" -version 2>&1 | findstr /C:"21.0" >nul
if not errorlevel 1 set "JAVA_EXE=%~1"
exit /b 0
