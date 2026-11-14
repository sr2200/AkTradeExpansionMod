@echo off
rem AK Trade Expansion: screenshots for the download page (docs\release\post-draft.md).
rem   Opens a game window for a few minutes (do not touch it while it runs): a new world with a fixed seed, a village
rem   with a stall and its trader, two village buildings, the trade screen and the board, all 1920x1080.
rem   The images go to docs\release\Screenshot\auto (stall.png, facility.png, trade.png, board.png).
rem   release.bat runs this after the build. Other arguments go to Gradle (e.g. shots.bat --offline).
setlocal
cd /d "%~dp0"

rem Full path: some environments do not run programs from the current folder (NoDefaultCurrentDirectoryInExePath).
call "%~dp0gradlew.bat" runClientGameTestShots %1 %2 %3 %4
if errorlevel 1 (
  echo Some screenshots could not be taken. See the log above.
  exit /b 1
)
echo Screenshots: %~dp0..\..\docs\release\Screenshot\auto
endlocal
