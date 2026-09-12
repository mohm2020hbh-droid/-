@echo off
REM Starts the Voice Duel server on Windows.
REM Creates the virtual environment and installs dependencies on first run.

cd /d "%~dp0backend"

if not exist ".venv\Scripts\python.exe" (
  echo Creating the virtual environment...
  python -m venv .venv || goto :error
  echo Installing dependencies...
  ".venv\Scripts\python.exe" -m pip install --upgrade pip || goto :error
  ".venv\Scripts\python.exe" -m pip install -r requirements.txt || goto :error
)

echo.
echo ===============================================================
echo   Voice Duel server starting.
echo.
echo   Play in your browser:  http://127.0.0.1:8000/play
echo   Health check:          http://127.0.0.1:8000/health
echo.
echo   Open the /play page in TWO tabs: create a room in one,
echo   join with the code in the other. Press Ctrl+C to stop.
echo ===============================================================
echo.

".venv\Scripts\python.exe" -m uvicorn app.main:app --host 0.0.0.0 --port 8000
goto :eof

:error
echo.
echo Setup failed. Make sure Python 3.10 or newer is installed and on PATH.
echo Check with:  python --version
pause
