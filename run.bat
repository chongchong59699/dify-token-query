@echo off
setlocal
cd /d "%~dp0"

if not exist "target\dify-token-query-tool.jar" (
  echo target\dify-token-query-tool.jar not found.
  echo Please run: mvn clean package
  pause
  exit /b 1
)

set "JAVA_CMD=java"
if exist "C:\Program Files\Java\jdk1.8.0_202\bin\java.exe" (
  set "JAVA_CMD=C:\Program Files\Java\jdk1.8.0_202\bin\java.exe"
)

"%JAVA_CMD%" -jar "target\dify-token-query-tool.jar"
endlocal
