@echo off
rem Trade Expansion: release build.
rem   1. Raises the last number of "version" in gradle.properties (0.0.7 -> 0.0.8) and the version line of README.md
rem      (skip with: release.bat nobump)
rem   2. Runs "gradlew build": tests, the mod jar (build\libs) and the distribution zip (build\distributions)
rem   Other arguments go to Gradle (e.g. release.bat --offline, release.bat nobump --offline)
setlocal
cd /d "%~dp0"

set "BUMP=1"
if /i "%~1"=="nobump" (
  set "BUMP=0"
  shift
)
if "%BUMP%"=="0" goto build

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = 'gradle.properties'; $t = [IO.File]::ReadAllText((Resolve-Path $p));" ^
  "$m = [regex]::Match($t, '(?m)^version=(\d+)\.(\d+)\.(\d+)');" ^
  "if (-not $m.Success) { Write-Host 'version not found in gradle.properties'; exit 1 }" ^
  "$old = $m.Groups[1].Value + '.' + $m.Groups[2].Value + '.' + $m.Groups[3].Value;" ^
  "$new = $m.Groups[1].Value + '.' + $m.Groups[2].Value + '.' + ([int]$m.Groups[3].Value + 1);" ^
  "$t = $t.Substring(0, $m.Index) + 'version=' + $new + $t.Substring($m.Index + $m.Length);" ^
  "[IO.File]::WriteAllText((Resolve-Path $p), $t);" ^
  "$r = '..\..\README.md'; if (Test-Path $r) { $u = [IO.File]::ReadAllText((Resolve-Path $r), [Text.Encoding]::UTF8); $u = $u.Replace('| ' + $old + [char]0xFF08, '| ' + $new + [char]0xFF08); [IO.File]::WriteAllText((Resolve-Path $r), $u, (New-Object Text.UTF8Encoding $false)) }" ^
  "Write-Host ('version ' + $old + ' -> ' + $new)"
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
echo Mod jar:          %~dp0build\libs
echo Distribution zip: %~dp0build\distributions
endlocal
