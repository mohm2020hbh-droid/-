# Drop-in target recordings

Every target sound in Voice Duel is synthesised on the device, so the game
works offline with nothing to download. If you would rather use a real
recording for a sound, put the file here:

    backend/app/static/audio/<sound_id>.mp3      (.ogg and .wav also work)

Use the sound's id exactly as it appears in `app/static/js/sounds.js` — the
same id the game uses for scoring and for the Arabic/English names, e.g.

    donkey.mp3      lion.wav       train.ogg
    cat.mp3         rooster.mp3    doorbell.ogg

That is all. The next time the sound is played the file is picked up
automatically: no code change, no list to edit, no restart beyond reloading
the page. The same file is then used both for playback and for scoring, so a
player is always scored against exactly the audio they heard.

Notes:

* The id must match exactly, including case. `Donkey.mp3` will not be found.
* Any sample rate and channel count is fine; the file is converted to mono at
  the engine's analysis rate when it loads.
* Keep clips short — roughly 0.5 to 3 seconds, like the built-in targets. A
  long clip makes the imitation round drag.
* Trim the silence at the start and end, or the player will hear a pause
  before the sound.
* A missing, corrupt, or unsupported file is ignored and the synthesised
  target is used instead, so a bad file can never stop the game from running.

The full list of ids is in `app/static/js/sounds.js`.

## Importing a real recording (content pipeline)

If you have a downloaded recording and its exact license, run:

    python tools/import_audio.py \
        --id lion --input /path/to/downloaded.wav \
        --name-ar "زئير أسد" --name-en "Lion" \
        --source freesound --creator "exact uploader name" \
        --license "CC0 1.0" \
        --source-url "https://freesound.org/people/.../sounds/12345/"

from `backend/`. It standardises the sample rate and channel count, trims
leading/trailing silence, applies a moderate loudness normalisation
(ffmpeg's `loudnorm`, EBU R128 — deliberately gentle, not squashed), writes
`<sound_id>.ogg` here, and records the required attribution fields in
`sources.json`. It never marks a sound reviewed — that only happens once a
person has actually listened to the finished file and confirmed it is
immediately recognizable, per `sources.json`'s own schema note.

Requires `ffmpeg`/`ffprobe` on PATH. This is a one-time content tool, not
part of the shipped game — `run-server.bat` never touches it.

## Status — 2026-09-12

**No real recording has been imported for any sound.** Both of the sources
this pipeline is meant to fetch from — freesound.org and opengameart.org —
are blocked by this sandbox's outbound network policy (confirmed with two
independent tools: a direct HTTPS CONNECT returns "403, organization
policy", and the `WebFetch` tool reports `EGRESS_BLOCKED` for both domains).
This is a policy decision on the environment, not a licensing problem with
any individual sound, so every Single Player target is reported unresolved
for the same reason rather than one-by-one:

    ambulance, bee, cat, snake, cow, dog, rooster, bird, train, lion,
    alarm, police, elephant, frog, owl, motorcycle, cricket, sheep,
    helicopter

(the 19 sound ids actually reachable from the 20 Single Player stages;
`sounds.js` has 25 further ids used elsewhere in the game that would
benefit from the same treatment once sourcing is possible).

Every synthesised sound stays exactly as it is — none was silently kept in
place while claiming otherwise, and none was replaced with a fake "real"
recording to make this report look better.

Two ways to unblock this, independent of each other:

1. **Change the sandbox's network policy** for a Claude Code on the web
   environment (see https://code.claude.com/docs/en/claude-code-on-the-web)
   to permit freesound.org and opengameart.org, then have a session in that
   environment run the search → license-check → `import_audio.py` steps
   for the list above.
2. **Download the files yourself** (from any machine with normal internet
   access) and either drop the processed files straight into this folder
   with an entry in `sources.json`, or hand them to a session here to run
   through `tools/import_audio.py`. This path needs no policy change at
   all.
