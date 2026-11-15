@echo off
rem AK Trade Expansion: copies the design documents (docs\trade-mod-design.md, docs\goods-design.md) to the GitHub Wiki.
rem   The documents in the repository are the source of truth: the wiki pages are generated, never edit them on GitHub.
rem   1. Clones the wiki repository to build\wiki (or pulls it if it is already there)
rem   2. Writes the pages again, split by chapter (tools\wiki\genwiki.js, needs node)
rem   3. Commits and pushes the wiki repository
rem   wiki.bat nopush : steps 1 and 2 only (look at build\wiki before pushing)
rem   The wiki must be enabled on GitHub and have its first page (create any page in the browser once).
setlocal
cd /d "%~dp0"

set "WIKI_URL=https://github.com/sr2200/AkTradeExpansionMod.wiki.git"
set "WIKI_DIR=%~dp0build\wiki"
set "PUSH=1"
if /i "%~1"=="nopush" set "PUSH=0"

if exist "%WIKI_DIR%\.git" (
  git -C "%WIKI_DIR%" pull --ff-only -q
  if errorlevel 1 (
    echo Could not update %WIKI_DIR%. Delete the folder and run again.
    exit /b 1
  )
) else (
  if exist "%WIKI_DIR%" rmdir /s /q "%WIKI_DIR%"
  git clone -q "%WIKI_URL%" "%WIKI_DIR%"
  if errorlevel 1 (
    echo Could not clone the wiki. Enable the wiki in the repository settings and create its first page in the browser.
    exit /b 1
  )
)

node "%~dp0tools\wiki\genwiki.js" "%~dp0..\.." "%WIKI_DIR%"
if errorlevel 1 (
  echo Wiki page generation failed.
  exit /b 1
)

rem The pages are written with LF: keep them as they are (no CRLF warnings).
git -C "%WIKI_DIR%" config core.autocrlf false
git -C "%WIKI_DIR%" add -A
git -C "%WIKI_DIR%" diff --cached --quiet
rem No changes: still push, an earlier "wiki.bat nopush" may have left a commit behind.
if not errorlevel 1 (
  echo No page changed.
  goto push
)

for /f %%h in ('git -C "%~dp0..\.." rev-parse --short HEAD') do set "SRC=%%h"
git -C "%WIKI_DIR%" commit -q -m "Update design documents (source %SRC%)"
if errorlevel 1 (
  echo Wiki commit failed.
  exit /b 1
)

:push
if "%PUSH%"=="0" (
  echo Committed in %WIKI_DIR% but not pushed. Push with: git -C "%WIKI_DIR%" push
  exit /b 0
)
git -C "%WIKI_DIR%" push -q
if errorlevel 1 (
  echo Wiki push failed.
  exit /b 1
)
echo Wiki updated: https://github.com/sr2200/AkTradeExpansionMod/wiki
endlocal
