@echo off
setlocal
set "BASE=%~dp0"
set "MVN=%BASE%.mvn\wrapper\apache-maven-3.9.9\bin\mvn.cmd"
if not exist "%MVN%" (
  echo Downloading Apache Maven 3.9.9...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip'; $z='%BASE%.mvn\wrapper\maven.zip'; Invoke-WebRequest -UseBasicParsing $u -OutFile $z; Expand-Archive -Force $z '%BASE%.mvn\wrapper'; Remove-Item -Force $z"
  if errorlevel 1 exit /b 1
)
call "%MVN%" %*
