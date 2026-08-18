@echo off
setlocal
set GRADLE_VERSION=9.5.0
set APP_HOME=%~dp0
set DIST_DIR=%APP_HOME%.gradle-dist
set GRADLE_HOME=%DIST_DIR%\gradle-%GRADLE_VERSION%
set ZIP=%DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip
set URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip

if exist "%GRADLE_HOME%\bin\gradle.bat" goto run
if not exist "%DIST_DIR%" mkdir "%DIST_DIR%"
if not exist "%ZIP%" (
  echo Downloading Gradle %GRADLE_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ZIP%'"
  if errorlevel 1 exit /b 1
)
echo Extracting Gradle %GRADLE_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '%ZIP%' -DestinationPath '%DIST_DIR%'"
if errorlevel 1 exit /b 1

:run
call "%GRADLE_HOME%\bin\gradle.bat" %*
endlocal
