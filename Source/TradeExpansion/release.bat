@echo off
rem AK Trade Expansion: release build.
rem   1. Raises the middle number of "version" in gradle.properties and README.md, the last back to 0 (0.1.3 -> 0.2.0)
rem      (skip with: release.bat nobump)
rem   2. Runs "gradlew build releaseZip": tests, the mod jar (build\libs) and the distribution zip (build\distributions)
rem   3. Redraws the download page thumbnail with the new version (docs\release\thumbnail.png, needs pwsh)
rem   Other arguments go to Gradle (e.g. release.bat --offline, release.bat nobump --offline)
setlocal
cd /d "%~dp0"

set "BUMP=1"
if /i "%~1"=="nobump" (
  set "BUMP=0"
  shift
)
if "%BUMP%"=="0" goto build
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\version\bump.ps1" -Part minor
if errorlevel 1 (
  echo Version update failed.
  exit /b 1
)

:build
rem Full path: some environments do not run programs from the current folder (NoDefaultCurrentDirectoryInExePath).
call "%~dp0gradlew.bat" build releaseZip %1 %2 %3 %4
if errorlevel 1 (
  echo Build failed.
  exit /b 1
)

rem Thumbnail for the download page (has the version on it). Needs PowerShell 7 (pwsh): Windows PowerShell 5 garbles the Japanese text.
where pwsh >nul 2>nul
if errorlevel 1 (
  echo pwsh not found: thumbnail not updated. Run tools\thumbnail\genthumb.ps1 with PowerShell 7.
) else (
  pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\thumbnail\genthumb.ps1"
  if errorlevel 1 echo Thumbnail update failed.
)

echo.
echo Mod jar:          %~dp0build\libs
echo Distribution zip: %~dp0build\distributions
endlocal
