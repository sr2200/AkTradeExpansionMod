@echo off
rem AK Trade Expansion: development build.
rem   1. Raises the last number of "version" in gradle.properties and README.md (0.1.2 -> 0.1.3)
rem      (skip with: build.bat nobump)
rem   2. Runs "gradlew build": tests and the mod jar (build\libs). No distribution zip (use release.bat).
rem   Other arguments go to Gradle (e.g. build.bat --offline, build.bat nobump --offline)
setlocal
cd /d "%~dp0"

set "BUMP=1"
if /i "%~1"=="nobump" (
  set "BUMP=0"
  shift
)
if "%BUMP%"=="0" goto build
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\version\bump.ps1" -Part patch
if errorlevel 1 (
  echo Version update failed.
  exit /b 1
)

:build
rem Full path: some environments do not run programs from the current folder (NoDefaultCurrentDirectoryInExePath).
call "%~dp0gradlew.bat" build %1 %2 %3 %4
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

echo.
echo Mod jar: %~dp0build\libs
endlocal
