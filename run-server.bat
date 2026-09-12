@echo off
REM ===================================================================
REM  Voice Duel - Windows launcher
REM
REM  STABLE CONTRACT FILE. This launcher is deliberately self-contained:
REM  it reads nothing from the application code and imports no project
REM  modules, so a feature update can never break starting the game.
REM  If you change it, keep these guarantees:
REM
REM    1. Works by double-click (never assumes the current directory).
REM    2. Never depends only on "python" being on PATH.
REM    3. Reuses an existing backend\.venv; never deletes or rebuilds it.
REM    4. Never claims Python is missing when a usable one was found.
REM    5. Leaves the window open so errors stay readable.
REM ===================================================================

setlocal enabledelayedexpansion
title Voice Duel - server

REM Anchor every path to this file's own folder, not the caller's CWD.
set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "VENV=%BACKEND%\.venv"
set "VPY=%VENV%\Scripts\python.exe"
set "SYSPY="
set "SYSPYARG="

echo.
echo  ==============================================
echo    VOICE DUEL
echo  ==============================================
echo.

if not exist "%BACKEND%\app\main.py" goto :no_game_files

REM ---------------------------------------------------------------
REM  1. An existing environment is reused as-is.
REM ---------------------------------------------------------------
if exist "%VPY%" (
  echo  [1/3] Existing environment found - reusing it.
  goto :ensure_requirements
)

REM ---------------------------------------------------------------
REM  2. No environment yet: find a usable Python 3.10+.
REM     Each candidate is *executed* to prove it really works, which
REM     also rejects the Microsoft Store placeholder that exists on
REM     PATH but cannot run anything.
REM ---------------------------------------------------------------
echo  [1/3] Looking for Python...

call :probe "py" "-3"
if not defined SYSPY call :probe "py" ""
if not defined SYSPY call :probe "python" ""
if not defined SYSPY call :probe "python3" ""

REM Per-user installs (this is where the Windows installer puts it when
REM "install for all users" is not ticked).
if not defined SYSPY call :probe "%LOCALAPPDATA%\Python\bin\python.exe" ""
if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\Python313\python.exe" ""
if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" ""
if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\Python311\python.exe" ""
if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\Python310\python.exe" ""

REM Any other per-user version, newest first.
if not defined SYSPY (
  for /f "delims=" %%D in ('dir /b /ad /o-n "%LOCALAPPDATA%\Programs\Python\Python3*" 2^>nul') do (
    if not defined SYSPY call :probe "%LOCALAPPDATA%\Programs\Python\%%D\python.exe" ""
  )
)

REM Machine-wide installs.
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
echo.
echo  [2/3] Creating the environment - first run only, about a minute...
"!SYSPY!" !SYSPYARG! -m venv "%VENV%"
if not exist "%VPY%" goto :venv_failed

REM ---------------------------------------------------------------
REM  3. Install dependencies only when they are actually missing.
REM ---------------------------------------------------------------
:ensure_requirements
"%VPY%" -c "import fastapi, uvicorn" >nul 2>nul
if not errorlevel 1 goto :start

echo  [2/3] Installing dependencies...
"%VPY%" -m pip install --disable-pip-version-check --quiet --upgrade pip >nul 2>nul
"%VPY%" -m pip install --disable-pip-version-check --quiet -r "%BACKEND%\requirements.txt"
"%VPY%" -c "import fastapi, uvicorn" >nul 2>nul
if errorlevel 1 goto :pip_failed

REM ---------------------------------------------------------------
REM  4. Run the server.
REM ---------------------------------------------------------------
:start
echo  [3/3] Starting the server.
echo.
echo  ----------------------------------------------
echo    Play here:  http://127.0.0.1:8000/play
echo    Health:     http://127.0.0.1:8000/health
echo.
echo    Use 127.0.0.1 - browsers only allow the
echo    microphone on 127.0.0.1/localhost or https.
echo.
echo    Press Ctrl+C in this window to stop.
echo  ----------------------------------------------
echo.

pushd "%BACKEND%"
"%VPY%" -m uvicorn app.main:app --host 0.0.0.0 --port 8000
set "EXITCODE=%ERRORLEVEL%"
popd

echo.
if not "%EXITCODE%"=="0" (
  echo  The server stopped with code %EXITCODE%.
  echo  If the port is already in use, close the other window
  echo  and run this launcher again.
) else (
  echo  Server stopped.
)
goto :done

REM ===================================================================
REM  Helper: prove a candidate Python actually runs and is new enough.
REM    %1 = command or full path      %2 = extra argument ("" for none)
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
REM  Failure paths - each says what actually went wrong.
REM ===================================================================
:no_game_files
echo  [X] The game files are not next to this launcher.
echo.
echo      Looked for: %BACKEND%\app\main.py
echo.
echo      run-server.bat has to stay inside the extracted
echo      voice-duel folder, beside the "backend" folder.
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
echo.
echo      Already installed? Run this in a new window to see
echo      where it is, then reopen this launcher:
echo          where python
goto :done

:venv_failed
echo.
echo  [X] Python was found, but creating the environment failed.
echo      Python used: !SYSPY! !SYSPYARG!
echo.
echo      This is usually one of:
echo        - no permission to write in this folder
echo          ^(move the game folder to your Desktop and retry^)
echo        - antivirus blocking the environment
echo        - a half-created backend\.venv folder; delete it and retry
goto :done

:pip_failed
echo.
echo  [X] The environment exists, but the dependencies did not install.
echo.
echo      Most often this is no internet connection, a proxy, or a
echo      firewall blocking pip. To see the real error, run:
echo          "%VPY%" -m pip install -r "%BACKEND%\requirements.txt"
goto :done

:done
echo.
pause
endlocal
