@echo off
REM One-shot build for SecureDroid. Usage: build.cmd [task]  (default: assembleDebug)
REM Outputs ONLY error lines + BUILD result to keep context small.
setlocal
set JAVA_HOME=C:\Android_build\jdk-17
set ANDROID_HOME=C:\Android_build\sdk
set ANDROID_SDK_ROOT=%ANDROID_HOME%
set ANDROID_USER_HOME=C:\Android_build\.android
set GRADLE_USER_HOME=C:\Android_build\.gradle
cd /d "%~dp0"
set TASK=%1
if "%TASK%"=="" set TASK=assembleDebug
call C:\Android_build\gradle-8.7\bin\gradle.bat %TASK% --console=plain 1>build_out.log 2>build_err.log
echo EXIT=%ERRORLEVEL%
findstr /b /c:"e: " /c:"BUILD " /c:"FAILURE" build_err.log build_out.log
endlocal
