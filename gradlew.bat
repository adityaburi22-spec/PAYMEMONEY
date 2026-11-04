@echo off
setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
  echo Missing gradle wrapper jar at %CLASSPATH%
  exit /b 1
)

if defined JAVA_HOME (
  "%JAVA_HOME%\bin\java.exe" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
) else (
  java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
)
