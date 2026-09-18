# FLIP ERROR

One-touch geometric auto-runner. Spec: [`../docs/FLIP_ERROR_GDD.md`](../docs/FLIP_ERROR_GDD.md).

This is a standalone project. It shares nothing with the ClipFlow app in the
repository root and does not participate in that build.

## Layout

| Module | What it is |
|---|---|
| `core/` | The whole game: physics, level data, collision, state. Pure Kotlin, no platform types. Targets JVM (tests) and JS (web build), and is what the Android app will render. |
| `web/`  | Browser playtest shell: canvas renderer, touch input, WebAudio. Lets the slice be played and automatically tested today. |
| `tools/`| Automated playthroughs in a real browser: `playtest.mjs` (LEVEL 1 end to end), `feeltest.mjs` (the second jump and the trail), `progresstest.mjs` (the meta game), `deserttest.mjs` (world 2), `orientation.mjs` (the gate). |

`core` is the single source of truth. No gameplay rule is implemented twice.

## Current state

Twelve levels across two worlds, every one of them proved beatable by the
solver before it ships.

**WORLD 1 — NEON CITY** (levels 1-6). Spikes, gaps, ceiling corridors, sliding
and rising hazards, and platforms that blink out from under you. Ends on
LEVEL 6 "SYSTEM CRASH", whose three-spike finish measures the tightest window
the game is ever allowed to ask for.

**WORLD 2 — NEON DESERT** (levels 7-12). A different playground rather than a
reskin: sand that breathes under the runner, crests that roll at them, temple
blocks that slide and lift, masonry that drops, relics that orbit, geysers that
say JUMP and sun beams that say STAY DOWN, bridges that fade, mirages, and
columns of vertical air. Built from one shared kit, `core/.../Desert.kt`.

Around them: star coins that bank the instant they are touched, a shop that
sells appearance and nothing else, English and Arabic with real RTL, and a
level select broken by world.

## There is no music

Not a quiet track, not a loop at low volume - none. `web/.../Audio.kt` is an
environment rather than a soundtrack: wind, rumble, mains hum and air, plus
things happening somewhere out of sight, on a schedule with no grid in it.
Three rules hold it together - nothing lands at a fixed interval, because two
sounds a fixed distance apart are a beat and a beat is a song; the room's weight
is read from the runner's own progress, so it is heaviest where the level is
hardest; and crossing into a tenser stretch ducks everything to near-silence for
a beat first, because a build with no hole in front of it is a volume knob.

The city hums, crackles and echoes off machinery. The desert is windier, lower
and emptier, with sand moving, stone groaning and something older turning over
underneath it. The player's own cues - jump, double jump, land, death, coin,
near miss, finish - are short and bright and sit on their own bus above all of
it. Settings has three volumes (master, SFX, ambience) and no music control,
because a switch for a thing that does not exist tells the player they failed
to hear it.

Deliberately not built: gravity flip, dash, reverse, low gravity, shape shift,
worlds 3-10, ads, accounts, level editor. Nothing in the shop affects play.

## The difficulty curve

Two different things, measured separately, because conflating them is what
makes a twelve-level ladder impossible.

**Reflex** is the tightest take-off window on the verified line. It has a floor
of **0.075s** - about four and a half frames at 60Hz - and the game never goes
under it, in either world. A world tightens toward that floor and stops.

**Reading** is everything the player has to see rather than hit: movers, pulses,
floors that leave, beams, wind. It has no ceiling, and it is what world 2
escalates instead. World 1 has 41 moving parts across its six levels; world 2
has 90.

| | L1 | L2 | L3 | L4 | L5 | L6 |
|---|---|---|---|---|---|---|
| **world 1** | 0.129s | 0.108s | 0.100s | 0.096s | 0.079s | 0.075s |
| | L7 | L8 | L9 | L10 | L11 | L12 |
| **world 2** | 0.092s | 0.088s | 0.083s | 0.079s | 0.075s | 0.075s |

`DifficultyLadderTest` asserts the whole table, and every level's own gate
(`LevelGate`) asserts that it is beatable, that its tightest moment is in its
last tenth, that no second tap is frame perfect, that it does not open on its
hardest moment, and that none of its three star coins sit on the line a perfect
player already flies.

### The rule that measuring width missed

A boost fired late travels further than one fired early - measured, 7.68u at the
apex against 8.63u at the last legal frame - because the first jump's height is
kept longer before the second tap resets `vy`. So a crossing needing
near-maximum distance is survivable only in the last frames of its window, and
the tap at the top of the arc, the one a person actually makes, dies. The
verifier called those windows 0.096s wide and fair; from the player's chair they
were walls. `the second tap works when a person would actually make it` taps at
the apex from a spread of take-offs across each window and requires most of them
to live. It found the two levels that were reported as broken, and two more that
had not been.

## Build and test

```bash
# every gameplay rule, plus the level solver
gradle :core:jvmTest

# the playable build -> web/build/dist/js/productionExecutable
gradle :web:jsBrowserDistribution

# drive the real game in a real browser and screenshot it
cd tools && npm install && cd ..
node tools/playtest.mjs        # level 1, end to end
node tools/feeltest.mjs        # the second jump, the trail, the frame budget
node tools/progresstest.mjs    # coins, shop, settings, level select
node tools/deserttest.mjs      # world 2: all six levels, cleared on their lines
node tools/orientation.mjs     # the portrait gate, on real viewports
node tools/runall.mjs          # all twelve, flown at 60fps in a real browser
node tools/blindtest.mjs       # no gap is committed to off the edge of the screen
```

The browser harnesses replay the exact lines the solver exported to
`core/build/level*-plan.json`, so `gradle :core:jvmTest` has to run first.

## Verifying a level

`core/src/jvmTest/.../LevelVerifier.kt` proves a level is beatable instead of
assuming it. Because x advances at a constant speed, the frame index
determines x, so the level is a DAG over ground frames where the only choice is
wait or jump. Solving it backwards gives the take-off window for every jump -
the number that decides whether a death was the player's fault.

It extends to moving and blinking geometry with no change to the search, for
one reason: the runner never stops, so `x = RUN_SPEED * time` and the whole
world stays a function of where the player is. That invariant is why wind here
is vertical only and why a horizontal platform never carries the runner - a
conveyor would make x depend on the player's history, and the solver could no
longer say whether a level was possible at all.

LEVEL 1 measures at: beatable, 31.7s, 26 taps, windows 0.238s at the open,
0.20-0.26s through the middle, 0.129s at 95-96%. It has not moved since.

Places where the GDD contradicts itself or its own physics are listed in
[`../docs/FLIP_ERROR_LEVEL1_NOTES.md`](../docs/FLIP_ERROR_LEVEL1_NOTES.md).
