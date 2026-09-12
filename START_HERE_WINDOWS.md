# Voice Duel Online — start here (Windows)

A two-player voice-imitation duel. The server names a sound, one player
imitates it out loud while a countdown runs, the recording goes to the other
player, who listens and scores it 0–100. Roles swap every round.

There are two clients in this package, and **both talk to the same server**:

* a **browser client**, which works right now with no build step;
* an **Android app** (`android/`), which needs Android Studio to build.

---

## 1. Play it in your browser (2 minutes)

You need **Python 3.10 or newer** ([python.org](https://www.python.org/downloads/) —
tick *"Add Python to PATH"* during installation).

Double-click **`run-server.bat`**.

It creates the virtual environment, installs the dependencies, and starts the
server. First run takes a minute; later runs start instantly.

Then open this address in **two browser tabs**:

```
http://127.0.0.1:8000/play
```

* **Tab 1** → *إنشاء غرفة* → note the 4-digit code.
* **Tab 2** → type the code → *الانضمام لغرفة*.

The match starts by itself. Allow the microphone when the browser asks.

### Prefer to type the commands yourself

```cmd
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

### Important: use `127.0.0.1`, not your LAN address

Browsers only hand out the microphone on a **secure context** — that means
`127.0.0.1` / `localhost`, or `https`. On a plain `http://192.168.x.x` address
the browser blocks the mic; the page tells you so and sends a short generated
tone instead, so the match still plays through to the end.

To play between two real devices with real microphones, put the server behind
`https` (any reverse proxy with a certificate will do).

---

## 2. Build the Android app

Requires **Android Studio** (it installs the Android SDK for you).

1. *File → Open* → select this repository folder.
2. Let Gradle sync. Android Studio writes `local.properties` with your SDK path
   automatically. (If you ever need to write it by hand, copy
   `local.properties.example` to `local.properties`.)
3. Build the APK:

```cmd
gradlew.bat :android:assembleDebug
```

The APK lands at:

```
android\build\outputs\apk\debug\android-debug.apk
```

Install it on a phone, or run it straight from Android Studio.

On each phone, set the server address on the home screen:

```
ws://<your-PC-IP>:8000/ws
```

Find your PC's IP with `ipconfig` (the *IPv4 Address* of your Wi-Fi adapter).
Both phones and the PC must be on the same network, and Windows Firewall has to
allow Python on port 8000 — Windows asks the first time you start the server;
choose *Allow*.

> **If the Gradle build fails to download anything**, you are behind a network
> that blocks Google's Maven repository (`dl.google.com`). The Android build
> cannot work without it. The browser client above has no such requirement.

---

## 3. Run the tests

```cmd
run-tests.bat
```

58 backend tests: complete matches, audio relay integrity, wrong room codes,
disconnects, and the round timeouts.

To check a running server end to end, start it and then run:

```cmd
cd backend
.venv\Scripts\python.exe tools\simulate_match.py --url ws://127.0.0.1:8000/ws
```

This connects two independent clients and plays a full match, verifying that
roles alternate, scores accumulate, and each recording arrives byte-for-byte
intact.

Android unit tests (needs the SDK):

```cmd
gradlew.bat :android:testDebugUnitTest
```

---

## What is in this repository

```
run-server.bat            starts the server (creates the venv on first run)
run-tests.bat             runs the backend test suite
settings.gradle.kts       Gradle build: :android (this game) and :app (see below)
gradlew.bat / gradlew     Gradle wrapper (downloads Gradle itself)
gradle/libs.versions.toml dependency versions

backend/                  FastAPI + WebSockets server - no database
  app/models/             protocol, room state, the 30-sound library
  app/services/           connections, room bookkeeping, game rules
  app/routers/            the /ws endpoint, health, and /play
  app/static/index.html   the browser client
  tests/                  the test suite
  tools/simulate_match.py two-client end-to-end driver
  README.md               full protocol reference

android/                  Kotlin + Jetpack Compose client (the :android module)
  src/main/java/com/voiceduel/
    data/                 WebSocket transport and protocol
    audio/                recording and playback
    ui/                   state, ViewModel, the five screens
  README.md               build and configuration notes

VOICE_DUEL.md             architecture and design notes
```

> `app/` and the root `README.md` belong to **ClipFlow**, an unrelated app that
> was already in this repository. Voice Duel does not use it, and building
> `:android` does not build it.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `'python' is not recognized` | Install Python and tick *Add Python to PATH*, then reopen the terminal. |
| Browser never asks for the mic | You are not on `127.0.0.1` or `https`. See the note in section 1. |
| Phone cannot reach the server | Same Wi-Fi network; allow Python through Windows Firewall; use the `ipconfig` IPv4 address, not `127.0.0.1`. |
| `invalid_code` when joining | Codes are freed the moment a room empties or a match ends. Create a fresh room. |
| Port 8000 already in use | Start with `--port 8001` and use that port in the URLs. |
