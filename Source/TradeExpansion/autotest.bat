@echo off
rem AK Trade Expansion: automated in-game tests (docs\release\ingame-checklist.md, test code in src\gametest).
rem   autotest.bat          server game tests (no window, about 1 minute), client game tests
rem                         (opens a game window for a few minutes: do not touch it while it runs) and the clients
rem                         with other mods left out (opens the window three more times, a minute each) and with
rem                         JEI, REI or JourneyMap (three more times, a minute each)
rem   autotest.bat server   server game tests only
rem   autotest.bat client   client game tests only (with Mod Menu and YACL)
rem   autotest.bat minimal  the client without Mod Menu and YACL only (checklist 1-1, 15-3)
rem   autotest.bat variants the clients with other mods left out only: without Mod Menu and YACL, Mod Menu without
rem                         YACL (1-6) and YACL without Mod Menu (1-7)
rem   autotest.bat mods     the clients with JEI, REI or JourneyMap only (checklist 25, 20-3, 20-5, 30-1)
rem   autotest.bat looks    pictures of every building in every look only (105 pictures for a person to look at,
rem                         about 5 minutes; not part of the full run)
rem   Then records the results in docs\release\ingame-checklist.md and docs\release\autotest-report.md (needs node).
rem   Other arguments go to Gradle, e.g. autotest.bat server --offline
rem   Only some cases: -Pautotest.only=2-10+2-11+stall (checklist numbers or case names, joined with +)
setlocal
cd /d "%~dp0"

set "TASKS=runGameTest runClientGameTest runClientGameTestMinimal runClientGameTestModMenuOnly runClientGameTestYaclOnly runClientGameTestJei runClientGameTestRei runClientGameTestJourneyMap"
set "SIDES=server client client-minimal client-modmenu-only client-yacl-only client-jei client-rei client-journeymap"
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
) else if /i "%~1"=="looks" (
  set "TASKS=runClientGameTestLooks"
  set "SIDES=client-looks"
  shift
) else if /i "%~1"=="mods" (
  set "TASKS=runClientGameTestJei runClientGameTestRei runClientGameTestJourneyMap"
  set "SIDES=client-jei client-rei client-journeymap"
  shift
) else if /i "%~1"=="variants" (
  set "TASKS=runClientGameTestMinimal runClientGameTestModMenuOnly runClientGameTestYaclOnly"
  set "SIDES=client-minimal client-modmenu-only client-yacl-only"
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
