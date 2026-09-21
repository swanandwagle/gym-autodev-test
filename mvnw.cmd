@REM Maven wrapper launcher script for Windows
@echo off
setlocal

set BASEDIR=%~dp0
set MVNWDIR=%BASEDIR%.mvn\wrapper
set WRAPPER_PROPERTIES=%MVNWDIR%\maven-wrapper.properties
set WRAPPER_JAR=%MVNWDIR%\maven-wrapper.jar

if "%JAVA_HOME%"=="" (
  set JAVACMD=java
) else (
  set JAVACMD=%JAVA_HOME%\bin\java.exe
)

set MAVEN_USER_HOME=%USERPROFILE%\.m2
for /F "tokens=1,* delims==" %%a in (%WRAPPER_PROPERTIES%) do (
  if "%%a"=="distributionUrl" set DISTRIBUTION_URL=%%b
)

%JAVACMD% -jar "%WRAPPER_JAR%" --baseDir "%BASEDIR%" %*
endlocal
