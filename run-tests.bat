@echo off
REM Runs the backend test suite on Windows.

cd /d "%~dp0backend"

if not exist ".venv\Scripts\python.exe" (
  python -m venv .venv || goto :error
  ".venv\Scripts\python.exe" -m pip install --upgrade pip || goto :error
)
".venv\Scripts\python.exe" -m pip install -r requirements-dev.txt || goto :error
".venv\Scripts\python.exe" -m pytest
pause
goto :eof

:error
echo Setup failed. Make sure Python 3.10 or newer is installed and on PATH.
pause
