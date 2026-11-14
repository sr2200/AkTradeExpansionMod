@echo off
rem AK Trade Expansion: automated in-game tests (docs\release\ingame-checklist.md, test code in src\gametest).
rem   autotest.bat          server game tests (no window, about 1 minute), client game tests
rem                         (opens a game window for a few minutes: do not touch it while it runs) and the client
rem                         without Mod Menu and YACL (checklist 1-1, opens the window once more for a minute)
rem   autotest.bat server   server game tests only
rem   autotest.bat client   client game tests only (with Mod Menu and YACL)
rem   autotest.bat minimal  the client without Mod Menu and YACL only
rem   Then records the results in docs\release\ingame-checklist.md and docs\release\autotest-report.md (needs node).
rem   Other arguments go to Gradle, e.g. autotest.bat server --offline
rem   Only some cases: -Pautotest.only=2-10+2-11+stall (checklist numbers or case names, joined with +)
setlocal
cd /d "%~dp0"

set "TASKS=runGameTest runClientGameTest runClientGameTestMinimal"
set "SIDES=server client client-minimal"
if /i "%~1"=="server" (
  set "TASKS=runGameTest"
  set "SIDES=server"
  shift
) else if /i "%~1"=="client" (
  set "TASKS=runClientGameTest"
  set "SIDES=client"
  shift
) else if /i "%~1"=="minimal" (
  set "TASKS=runClientGameTestMinimal"
  set "SIDES=client-minimal"
  shift
)

rem The results of the side that runs now are replaced; the other side's last results are recorded and reported again.
for %%s in (%SIDES%) do if exist "%~dp0build\autotest\%%s-results.json" del "%~dp0build\autotest\%%s-results.json"

rem Full path: some environments do not run programs from the current folder (NoDefaultCurrentDirectoryInExePath).
rem --continue: the client tests still run when a server case failed.
call "%~dp0gradlew.bat" %TASKS% --continue %1 %2 %3 %4
set "GRADLE_ERROR=%errorlevel%"

node "%~dp0tools\checklist\record.js"
set "RECORD_ERROR=%errorlevel%"

echo.
if not "%GRADLE_ERROR%"=="0" (
  echo Some automated tests failed or could not run. See docs\release\autotest-report.md and the log above.
  exit /b 1
)
if not "%RECORD_ERROR%"=="0" (
  echo Recording the results failed.
  exit /b 1
)
echo All automated tests passed. Results: docs\release\autotest-report.md
endlocal
