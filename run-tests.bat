@echo off
REM ===================================================================
REM  Voice Duel - full test matrix
REM
REM  Runs everything: the backend suite, the audio engine checks, the
REM  scoring checks, and the localization coverage checks.
REM
REM  Like run-server.bat this script finds Python by *executing* candidates
REM  rather than trusting PATH, and it keeps its own copy of that logic on
REM  purpose: a bootstrap script must not fail because a sibling file moved.
REM ===================================================================

setlocal enabledelayedexpansion
title Voice Duel - tests

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "VENV=%BACKEND%\.venv"
set "VPY=%VENV%\Scripts\python.exe"
set "SYSPY="
set "SYSPYARG="
set "FAILED="

echo.
echo  ==============================================
echo    VOICE DUEL - tests
echo  ==============================================
echo.

if not exist "%BACKEND%\app\main.py" goto :no_game_files

REM ---------------------------------------------------------------
REM  Reuse the environment run-server.bat already built, if it is there.
REM ---------------------------------------------------------------
if exist "%VPY%" goto :ensure_requirements

echo  Looking for Python...

call :probe "py" "-3"
if not defined SYSPY call :probe "py" ""
if not defined SYSPY call :probe "python" ""
if not defined SYSPY call :probe "python3" ""
if not defined SYSPY call :probe "%LOCALAPPDATA%\Python\bin\python.exe" ""
if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\Python313\python.exe" ""
if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" ""
if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\Python311\python.exe" ""
if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\Python310\python.exe" ""
if not defined SYSPY (
  for /f "delims=" %%D in ('dir /b /ad /o-n "%LOCALAPPDATA%\Programs\Python\Python3*" 2^>nul') do (
    if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\%%D\python.exe" ""
  )
)
if not defined SYSPY (
  for /f "delims=" %%D in ('dir /b /ad /o-n "%ProgramFiles%\Python3*" 2^>nul') do (
    if not defined SYSPY call :probe "%ProgramFiles%\%%D\python.exe" ""
  )
)
if not defined SYSPY call :probe "C:\Python313\python.exe" ""
if not defined SYSPY call :probe "C:\Python312\python.exe" ""
if not defined SYSPY call :probe "C:\Python311\python.exe" ""
if not defined SYSPY call :probe "C:\Python310\python.exe" ""

if not defined SYSPY goto :no_python

echo        Using: !SYSPY! !SYSPYARG!
"!SYSPY!" !SYSPYARG! -m venv "%VENV%"
if not exist "%VPY%" goto :venv_failed

:ensure_requirements
"%VPY%" -c "import pytest, fastapi" >nul 2>nul
if not errorlevel 1 goto :run

echo  Installing test dependencies...
"%VPY%" -m pip install --disable-pip-version-check --quiet --upgrade pip >nul 2>nul
"%VPY%" -m pip install --disable-pip-version-check --quiet -r "%BACKEND%\requirements-dev.txt"
"%VPY%" -c "import pytest, fastapi" >nul 2>nul
if errorlevel 1 goto :pip_failed

:run
pushd "%BACKEND%"

echo.
echo  ---- backend ----------------------------------
"%VPY%" -m pytest -q
if errorlevel 1 set "FAILED=1"

REM The engine suites run on Node. It is optional: without it the backend
REM tests still run, and the script says what was skipped rather than
REM pretending everything passed.
where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo  ---- audio / scoring / localization ----------
  echo  SKIPPED - Node.js is not installed.
  echo  These suites check the sound engine and the translations.
  echo  Install Node.js from https://nodejs.org to run them.
) else (
  echo.
  echo  ---- audio engine -----------------------------
  node tests\js\audio.test.mjs
  if errorlevel 1 set "FAILED=1"

  echo.
  echo  ---- scoring engine ---------------------------
  node tests\js\dsp.test.mjs
  if errorlevel 1 set "FAILED=1"

  echo.
  echo  ---- localization -----------------------------
  node tests\js\i18n.test.mjs
  if errorlevel 1 set "FAILED=1"
)

popd

echo.
echo  ----------------------------------------------
if defined FAILED (
  echo    SOME TESTS FAILED - see the output above.
) else (
  echo    ALL TESTS PASSED.
)
echo  ----------------------------------------------
goto :done

REM ===================================================================
REM  Helper: prove a candidate Python actually runs and is new enough.
REM ===================================================================
:probe
set "CAND=%~1"
set "CARG=%~2"
if "%CAND%"=="" goto :eof
"%CAND%" %CARG% -c "import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)" >nul 2>nul
if errorlevel 1 goto :eof
set "SYSPY=%CAND%"
set "SYSPYARG=%CARG%"
goto :eof

REM ===================================================================
REM  Failure paths
REM ===================================================================
:no_game_files
echo  [X] The game files are not next to this script.
echo.
echo      Looked for: %BACKEND%\app\main.py
goto :done

:no_python
echo.
echo  [X] No working Python 3.10 or newer was found.
echo.
echo      Checked: py, python, python3, and the usual install
echo      folders under %LOCALAPPDATA% and %ProgramFiles%.
echo.
echo      Install Python from https://www.python.org/downloads/
echo      and tick "Add python.exe to PATH" in the installer.
goto :done

:venv_failed
echo.
echo  [X] Python was found, but creating the environment failed.
echo      Python used: !SYSPY! !SYSPYARG!
echo.
echo      Usually no permission to write here, or antivirus. Try
echo      moving the game folder to your Desktop.
goto :done

:pip_failed
echo.
echo  [X] The environment exists, but the test dependencies did not install.
echo.
echo      Usually no internet, a proxy, or a firewall blocking pip.
echo      To see the real error, run:
echo          "%VPY%" -m pip install -r "%BACKEND%\requirements-dev.txt"
goto :done

:done
echo.
pause
endlocal
