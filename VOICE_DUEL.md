# Voice Duel Online

A voice-imitation game with two modes.

**Single player** is offline: the game plays a target sound, you imitate it, and
an acoustic similarity engine scores the recording 0–100 by comparing spectral
shape, loudness contour, pitch contour and voicing against the target. Twenty
stages ramp in difficulty across four challenge types — a plain imitation, a
timed one, a sequence of sounds, and a deliberately distorted target. Progress
is kept in the browser. Nothing is transcribed: there is no speech recognition
in the scoring path at all.

**Online multiplayer** is the original room-code duel across two devices. The
server names a sound; one player imitates it while a countdown runs; the
recording is relayed to the other player, who listens and scores it by hand.
Roles swap every round. Here the judgement is deliberately human — it is a party
game between two people.

> This repository already contained an unrelated Android app (ClipFlow, the
> `:app` module). That project is untouched. Voice Duel was added alongside it
> as `backend/` and `android/`, reusing the existing Gradle wrapper and version
> catalog. The root `README.md` still describes ClipFlow.

```
backend/     FastAPI + WebSockets game server (no database)
android/     Kotlin + Jetpack Compose client (Gradle module :android)
```

Each folder has its own README with the details; the backend one also carries
the full protocol reference.

## How a match runs

```
Player A                     Server                      Player B
   │  create_room              │                            │
   │ ─────────────────────────>│                            │
   │  room_created {code}      │                            │
   │ <─────────────────────────│      join_room {code}      │
   │                           │<───────────────────────────│
   │  players_ready            │      players_ready         │
   │ <─────────────────────────│───────────────────────────>│
   │  round_start (A performs) │      round_start           │
   │ <─────────────────────────│───────────────────────────>│
   │  ■ binary recording       │                            │
   │ ─────────────────────────>│  audio_ready + recording   │
   │                           │───────────────────────────>│
   │                           │   rating_submitted {score} │
   │  round_result             │<───────────────────────────│
   │ <─────────────────────────│───────────────────────────>│
   │            … next round, with the roles swapped …      │
   │  game_over {winner_id}    │      game_over             │
   │ <─────────────────────────│───────────────────────────>│
```

## Playing it in a browser right now

The server serves a browser client at `/play` that speaks the same protocol as
the Android app:

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Then open `http://127.0.0.1:8000/play` in two tabs — create a room in one, join
with the code in the other. Microphone capture needs `127.0.0.1`/`localhost` or
`https`; on a plain LAN `http://` address the browser blocks the mic and the
page falls back to a generated tone so the match still completes.

## Running it end to end

**1. Start the server** on a machine both phones can reach:

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

**2. Check it** from a phone browser: `http://<server-ip>:8000/health`.

**3. Build the app** and install it on both devices:

```bash
./gradlew :android:installDebug
```

**4. On each phone**, set the server address on the home screen
(`ws://<server-ip>:8000/ws`), then create a room on one and join with the code
on the other.

### Local network first, then the internet

Testing over Wi-Fi needs nothing but the steps above. To play over the
internet the server has to be publicly reachable — that means hosting it
somewhere with a public address and a TLS certificate, and using `wss://` in
the app. That is an ongoing operational requirement, not a one-off step, and
choosing a host is outside the scope of this build.

## Verification

Automated, and runnable right now:

```bash
cd backend && pytest                      # 58 tests: full matches, errors, timeouts
python tools/simulate_match.py --url ws://127.0.0.1:8000/ws   # against a live server
./gradlew :android:testDebugUnitTest      # protocol codec + event mapping
```

`tools/simulate_match.py` connects two independent clients over real sockets and
plays a complete match, asserting that roles alternate, that totals accumulate,
that the recording arrives byte-for-byte intact, that a wrong code is refused,
and that a disconnect notifies the opponent.

Still to be done on real hardware (nothing here can stand in for it):

| | Check |
| --- | --- |
| T1 | A complete match between two physical devices. |
| T2 | A deliberately wrong code shows a clear message. |
| T3 | Killing one device's network mid-round notifies the other. |
| T4 | The recording plays back cleanly on the other phone. |
| T5 | Scores accumulate and roles alternate over a full match. |

## Design decisions worth knowing

**Rooms are memory-only.** A 4-digit code is allocated per room and released
when the match ends, a player leaves, or the room sits unjoined for 10 minutes.
Nothing is persisted and no recording is ever written to the server's disk.

**Both players see the target sound.** The rater cannot judge an imitation
without knowing what was being imitated.

**The server drives the flow.** The client changes screen only in response to an
event, so the two phones cannot drift apart.

**Neither side can hang the other.** Every round phase is watched: a performer
who never records, or a rater who never scores, closes the round at 0 instead of
freezing the match. A disconnect ends the match and frees the room.

**The judgement is human.** There is no pitch detection, no MFCC, no audio
analysis of any kind — just a slider.
