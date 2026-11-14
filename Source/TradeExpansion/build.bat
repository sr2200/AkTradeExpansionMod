@echo off
rem AK Trade Expansion: development build.
rem   1. Raises the last number of "version" in gradle.properties and README.md (0.1.2 -> 0.1.3)
rem      (skip with: build.bat nobump)
rem   2. Runs "gradlew build": tests and the mod jar (build\libs). No distribution zip (use release.bat).
rem   3. With "gametest": also runs the server game tests (no window, about 1 minute) and records the results in
rem      docs\release\ingame-checklist.md and docs\release\autotest-report.md (needs node). The client game tests
rem      (game window) run only with autotest.bat.
rem   Other arguments go to Gradle (e.g. build.bat --offline, build.bat nobump gametest --offline)
setlocal
cd /d "%~dp0"

set "BUMP=1"
set "GAMETEST=0"
:args
if /i "%~1"=="nobump" (
  set "BUMP=0"
  shift
  goto args
)
if /i "%~1"=="gametest" (
  set "GAMETEST=1"
  shift
  goto args
)
if "%BUMP%"=="0" goto build
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\version\bump.ps1" -Part patch
if errorlevel 1 (
  echo Version update failed.
  exit /b 1
)

:build
set "TASKS=build"
if "%GAMETEST%"=="1" (
  set "TASKS=build runGameTest --continue"
  rem The last client results (autotest.bat) are recorded and reported again with these.
  if exist "%~dp0build\autotest\server-results.json" del "%~dp0build\autotest\server-results.json"
)
rem Full path: some environments do not run programs from the current folder (NoDefaultCurrentDirectoryInExePath).
call "%~dp0gradlew.bat" %TASKS% %1 %2 %3 %4
set "GRADLE_ERROR=%errorlevel%"
if "%GAMETEST%"=="1" node "%~dp0tools\checklist\record.js"
if not "%GRADLE_ERROR%"=="0" (
  echo Build failed.
  if "%GAMETEST%"=="1" echo If only game tests failed, see docs\release\autotest-report.md. The jar may still have been made.
  exit /b 1
)

echo.
echo Mod jar: %~dp0build\libs
if "%GAMETEST%"=="1" echo Game test results: docs\release\autotest-report.md
endlocal
