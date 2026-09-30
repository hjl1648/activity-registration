@echo off
setlocal
set JAVA_HOME=D:\jdk-17.0.6
set PATH=%JAVA_HOME%\bin;D:\Maven\apache-maven-3.6.1\bin;%PATH%

echo [1/3] Ensure MySQL 3307...
netstat -ano | findstr ":3307" >nul
if errorlevel 1 (
  start "" /B D:\phpstudy_pro\Extensions\MySQL8.0.12\bin\mysqld.exe --defaults-file=D:/activity-reg-runtime/my.ini
  timeout /t 5 >nul
)

echo [2/3] Ensure Redis 6379...
netstat -ano | findstr ":6379" >nul
if errorlevel 1 (
  start "" /B D:\activity-reg-runtime\redis\redis-server.exe D:\activity-reg-runtime\redis\redis.windows.conf
  timeout /t 2 >nul
)

echo [3/3] Start application...
cd /d "%~dp0.."
java -jar target\activity-registration-1.0.0.jar
