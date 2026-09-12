# Voice Duel — Backend

FastAPI + WebSockets. Rooms live **in memory only**: there is no database, no
accounts, and no recording is ever written to disk (C1, C2). The server relays
each recording straight from the performer's socket to the rater's.

## Running it

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

uvicorn app.main:app --host 0.0.0.0 --port 8000
```

`--host 0.0.0.0` is what makes the server reachable from a phone. Check it from
the phone's browser at `http://<your-ip>:8000/health`.

## Browser client

The same server also serves a browser version of the game at **`/play`**. It
speaks the identical protocol (JSON envelopes plus the binary audio frames), so
it is a real client, not a mock — useful for playing a full match without
building the Android app.

```
http://127.0.0.1:8000/play
```

The page opens on a menu with two modes:

* **لاعب واحد (single player)** — 20 stages, played entirely on the device. The
  target sound is synthesised locally and the score comes from the acoustic
  engine in `app/static/js/dsp.js`. No server round-trip, no network.
* **لعب جماعي أونلاين (online)** — the original room-code game, unchanged: open
  `/play` in two tabs, create a room in one and join with the code in the other.

### The scoring engine

`app/static/js/dsp.js` compares two recordings by what they sound like:

| Term | What it measures |
| --- | --- |
| timbre | log-mel spectral shape per frame, aligned with dynamic time warping |
| dynamics | how the loudness rises and falls, correlated along the DTW path |
| pitch | autocorrelation f0 contour shape, plus how far off the register is |
| voicing | whether periodic and noisy stretches line up |
| brightness | mean spectral centroid over the frames that carry sound |

There is **no speech recognition anywhere**: nothing is transcribed, and no word
or phoneme model exists. The input is raw PCM and the output is a number.

Targets are synthesised from recipes in `app/static/js/sounds.js` rather than
shipped as audio files, so the bank needs no assets, works with no network, and
renders bit-identical samples every time — which is what makes scoring
repeatable.

Run the engine's tests (needs Node, optional):

```bash
node tests/js/dsp.test.mjs
```

They measure discrimination over the whole 30-sound bank: an imitation has to
rank its own target first, not merely score well against it.

> The browser only grants microphone access in a secure context, which means
> `127.0.0.1`/`localhost` or `https`. On a plain `http://192.168.x.x` address
> the mic is blocked by the browser; the page says so and sends a short
> generated tone instead, so the match can still be played through.

## Tests

```bash
pip install -r requirements-dev.txt
pytest
```

The suite plays complete matches over real WebSocket connections, including
audio relay, bad codes, disconnects, and the round timeouts.

There is also a standalone two-client simulator that plays a full match against
a *running* server — useful for checking a deployment before touching a phone:

```bash
uvicorn app.main:app --port 8000 &
python tools/simulate_match.py --url ws://127.0.0.1:8000/ws
```

## Configuration

Every setting is an environment variable; the defaults are in `app/config.py`.

| Variable | Default | Meaning |
| --- | --- | --- |
| `VD_TOTAL_ROUNDS` | `6` | Rounds per match. An even number gives both players the same number of turns. |
| `VD_COUNTDOWN_SECONDS` | `8` | Recording time announced to the performer. |
| `VD_PERFORMANCE_GRACE_SECONDS` | `25` | Extra wait for the recording before the round is scored 0. |
| `VD_RATING_TIMEOUT_SECONDS` | `120` | How long the rater has before the round is scored 0. |
| `VD_ROOM_TTL_SECONDS` | `600` | An unjoined room is dropped after this long. |
| `VD_JANITOR_INTERVAL_SECONDS` | `30` | How often expired rooms are swept. |
| `VD_MAX_AUDIO_BYTES` | `5242880` | Hard cap on one recording. |

## Layout

```
app/
  config.py              settings, all env-overridable
  main.py                application factory + lifespan (starts the janitor)
  models/
    messages.py          message types, envelope parsing, binary audio framing
    room.py              RoomState / Player / RoomPhase
    sounds.py            the fixed 30-sound library
  routers/
    health.py            GET / and GET /health
    ws.py                the single /ws endpoint; dispatch only
  services/
    connection.py        one client socket, tolerant of send failures
    room_manager.py      code allocation, seating, expiry
    game_service.py      all the game rules
tools/
  simulate_match.py      two-client end-to-end driver
```

## Protocol

One WebSocket per client at `/ws`. Text frames are JSON envelopes
`{"type": ..., "payload": {...}}`; binary frames carry one recording.

### Client → Server

| Type | Payload | Notes |
| --- | --- | --- |
| `create_room` | — | Answered with `room_created`. |
| `join_room` | `{code}` | Answered with `players_ready`, or `error`. |
| `rating_submitted` | `{round_number, score}` | `score` is an integer 0–100. |
| `leave_room` | — | Leaves the room but keeps the socket open. |
| `ping` | — | Answered with `pong`. |

### Server → Client

| Type | Payload |
| --- | --- |
| `connected` | `{player_id}` — this socket's own id, sent on connect |
| `room_created` | `{code}` |
| `players_ready` | `{player_a_id, player_b_id}` |
| `round_start` | `{round_number, total_rounds, sound_id, sound_name, sound_emoji, performer_id, countdown_seconds}` |
| `audio_ready` | `{round_number}` — sent to the rater, immediately followed by the binary frame |
| `round_result` | `{round_number, performer_id, score, total_scores, timed_out}` |
| `game_over` | `{winner_id, final_scores}` — `winner_id` is `null` on a draw |
| `opponent_disconnected` | `{reason}` — `"disconnected"` or `"left"` |
| `error` | `{reason}` |
| `pong` | — |

### Error reasons

`invalid_code`, `room_full`, `already_in_room`, `not_in_room`, `not_your_turn`,
`wrong_phase`, `stale_round`, `invalid_score`, `invalid_message`,
`audio_too_large`.

### Binary audio frame

Sent by the performer and relayed to the rater:

```
[4 bytes big-endian header length][UTF-8 JSON header][raw audio bytes]
```

The header always carries `round_number` and `mime`; the relayed copy also
carries `performer_id`. A frame naming a round that is no longer open is
refused with `stale_round`, so a late recording can never be scored against the
wrong round.

## Round lifecycle

```
WAITING ──join──> RECORDING ──audio──> RATING ──score──> (next round | FINISHED)
```

Both phases are watched: if the performer never sends a recording, or the rater
never scores, the round closes itself with a score of 0 and
`round_result.timed_out = true`. A match can therefore never hang on a silent
client. A disconnect ends the match immediately and the room is discarded.
