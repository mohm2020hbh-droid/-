# FLIP ERROR

One-touch geometric auto-runner. Spec: [`../docs/FLIP_ERROR_GDD.md`](../docs/FLIP_ERROR_GDD.md).

This is a standalone project. It shares nothing with the ClipFlow app in the
repository root and does not participate in that build.

## Layout

| Module | What it is |
|---|---|
| `core/` | The whole game: physics, level data, collision, state. Pure Kotlin, no platform types. Targets JVM (tests) and JS (web build), and is what the Android app will render. |
| `web/`  | Browser playtest shell: canvas renderer, touch input, WebAudio. Lets the slice be played and automatically tested today. |
| `tools/`| `playtest.mjs`, an automated playthrough in a real browser. |

`core` is the single source of truth. No gameplay rule is implemented twice.

## Current state — vertical slice

Playable end to end: LEVEL 1 "FIRST STEPS HURT", auto-run, one-tap jump,
spikes, gaps, platforms, a ceiling corridor, death with a stated cause,
instant retry and a level-complete screen.

Deliberately not built yet: gravity flip, dash, reverse, low gravity, shape
shift, other shapes, worlds 2-10, shop, ads, accounts, level editor.

## Build and test

```bash
# every gameplay rule, plus the level solver
gradle :core:jvmTest

# the playable build -> web/build/dist/js/productionExecutable
gradle :web:jsBrowserDistribution

# drive the real game in a real browser and screenshot it
cd tools && npm install && cd ..
node tools/playtest.mjs
```

## Verifying a level

`core/src/jvmTest/.../LevelVerifier.kt` proves a level is beatable instead of
assuming it. Because x advances at a constant speed, the frame index
determines x, so the level is a DAG over ground frames where the only choice is
wait or jump. Solving it backwards gives the take-off window for every jump -
the number that decides whether a death was the player's fault.

LEVEL 1 measures at: beatable, 31.7s, 26 taps, windows 0.238s at the open,
0.20-0.26s through the middle, 0.129s at 95-96%.

Places where the GDD contradicts itself or its own physics are listed in
[`../docs/FLIP_ERROR_LEVEL1_NOTES.md`](../docs/FLIP_ERROR_LEVEL1_NOTES.md).
