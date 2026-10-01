@echo off
REM One-shot build for SecureDroid. Usage: build.cmd [tasks...]  (default: assembleDebug)
REM Prints ONLY error lines plus the BUILD result, to keep agent context small.
REM GRADLE_USER_HOME lives on D: because C: once hit 0 bytes free and killed the Gradle daemon.
setlocal
set JAVA_HOME=C:\Android_build\jdk-17
set ANDROID_HOME=C:\Android_build\sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set ANDROID_USER_HOME=C:\Android_build\.android
set GRADLE_USER_HOME=D:\Android_build\.gradle
cd /d "%~dp0"
set TASK=%*
if "%TASK%"=="" set TASK=assembleDebug
call C:\Android_build\gradle-8.7\bin\gradle.bat %TASK% --console=plain 1>build_out.log 2>build_err.log
echo EXIT=%ERRORLEVEL%
findstr /b /c:"e: " /c:"BUILD " /c:"FAILURE" build_err.log build_out.log
endlocal
