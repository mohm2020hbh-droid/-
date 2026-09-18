# FLIP ERROR

One-touch geometric auto-runner. Spec: [`../docs/FLIP_ERROR_GDD.md`](../docs/FLIP_ERROR_GDD.md).

This is a standalone project. It shares nothing with the ClipFlow app in the
repository root and does not participate in that build.

## Layout

| Module | What it is |
|---|---|
| `core/` | The whole game: physics, level data, collision, state. Pure Kotlin, no platform types. Targets JVM (tests) and JS (web build), and is what the Android app will render. |
| `web/`  | Browser playtest shell: canvas renderer, touch input, WebAudio. Lets the slice be played and automatically tested today. |
| `tools/`| Automated playthroughs in a real browser: `playtest.mjs` (LEVEL 1 end to end), `feeltest.mjs` (the second jump and the trail), `progresstest.mjs` (the meta game), `deserttest.mjs` and `abysstest.mjs` (worlds 2 and 3), `orientation.mjs` (the gate), `runall.mjs` (all eighteen levels). |

`core` is the single source of truth. No gameplay rule is implemented twice.

## Current state

Eighteen levels across three worlds, every one of them proved beatable by the
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

**WORLD 3 — THE ABYSS** (levels 13-18). A third question, not a third palette.
World 1 asked WHEN to jump; world 2 asked WHERE THE GROUND WOULD BE; world 3
asks WHETHER TO JUMP AT ALL. Bubbles that chase and bubbles that split the
moment you commit, orbs that hunt the lane, walls that rise out of the floor and
drop from the ceiling, currents that shove, tentacles that reach in and
withdraw, floors made of bubbles that burst, drifting mines, and corridors of
light that say the only safe thing here is the ground. Built from one shared
kit, `core/.../Abyss.kt`, and every one of its obstacles is assembled from the
three verbs the solver already understands — move, blink, push — so nothing in
this world is outside what can be proved fair.

Around them: star coins that bank the instant they are touched, a shop that
sells appearance and nothing else, English and Arabic with real RTL, and a
level select broken by world.

## There is no music, and there is no ambience

This is a final design decision, and it is enforced rather than intended.

Every sound the game makes has a **cause**: the player did something, the
interface did something, or an obstacle did something. There is no background
track, no bed, no drone, no room tone, no wind, no ocean, no sandstorm, no
tension layer, and no loop of any kind. The audio engine has no clock of its
own to fire anything from.

The room that used to be here was **deleted, not muted**. The five layers per
world, the streamed `<audio>` elements, the ambience bus, the five tension
bands, the equal-power crossfades and the scheduler that fired events on its own
timer are gone from the source. Left in place and switched off, that machinery
would still fetch, decode and loop audio nobody asked for, and a silent system
that is still running is not the same thing as a system that is not there.

What that costs and what it buys: **20 of the pack's 42 recordings are
untouched on disk and referenced nowhere** — the eleven sixty-second
environments, the four tension risers, the three unease beds, the two world-1
atmosphere hits and the menu hum. `web/.../AudioMap.kt` lists all twenty by
name so the decision stays visible instead of looking like an oversight. The
live library is the other 22, and it weighs **198 KB** in both containers
together, against 10.6 MB before. Nothing loads at boot but those 22 cues.

Settings has **two** volumes, MASTER and SFX, and no music control and no
ambience control, because a switch for a thing that does not exist tells the
player they failed to hear it.

`AudioCues.kt` still gives every world-2 obstacle its own voice, fired on the
transition rather than the state — a beam's charge and its strike are two
different sounds — and only within earshot. That is a gameplay cue, not
ambience: it is caused by an obstacle the player is running at. World 3's
obstacles map to silence, because the pack contains no abyss recordings and
inventing one is exactly what the rule below forbids.

### The pack is the only source

There is no oscillator in this project, no noise buffer, no filter sweep, no
synthesised voice of any kind. The engine that generated all of that was
deleted rather than demoted: a fallback that quietly makes its own sounds is
exactly what a "these files only" rule exists to stop, and one nobody can hear
firing is worse than none at all.

If the pack cannot be fetched, the game is silent and completely playable.
That is the honest consequence of the rule.

`tools/audioaudit.mjs` holds the line. It reads the sources and the library off
disk and answers six questions: does any file still generate a sound, does
anything stream or loop audio, does every sound the code names exist in the
library, does everything in the library come from the pack, did all 42
recordings survive encoding in both containers, and are the unreferenced files
exactly the environmental set. `tools/soundtest.mjs` asks the last question
again of the running game, in the browser: no media element exists, and no
environmental recording is even in memory.

Two sounds the pack's README lists are not in it — `06_ui_cancel` and
`17_trail_spark` — so a refused purchase makes no sound at all and the trail
borrows the pack's own `18_speed_whoosh`.

Deliberately not built: gravity flip, dash, reverse, low gravity, shape shift,
worlds 4-10, ads, accounts, level editor. Nothing in the shop affects play.

## The difficulty curve

Two different things, measured separately, because conflating them is what
makes an eighteen-level ladder impossible.

**Reflex** is the tightest take-off window on the verified line. It has a floor
of **0.075s** — about four and a half frames at 60Hz — and the game never goes
under it, in any world. A world tightens toward that floor and stops.

**Reading** is everything the player has to see rather than hit: movers, pulses,
floors that leave, beams, wind, currents, splits. It has no ceiling, and it is
what each new world escalates instead. World 1 has 41 moving parts across its
six levels; world 2 has 90; world 3 has 153.

| | L1 | L2 | L3 | L4 | L5 | L6 | opens at | moving parts |
|---|---|---|---|---|---|---|---|---|
| **world 1** | 0.129s | 0.108s | 0.100s | 0.096s | 0.079s | 0.075s | 0.129s | 41 |
| | L7 | L8 | L9 | L10 | L11 | L12 | | |
| **world 2** | 0.092s | 0.088s | 0.083s | 0.079s | 0.075s | 0.075s | 0.092s | 90 |
| | L13 | L14 | L15 | L16 | L17 | L18 | | |
| **world 3** | 0.088s | 0.083s | 0.079s | 0.079s | 0.075s | 0.075s | 0.088s | 153 |

Each world opens tighter than the last one did and ends on the floor, and
inside a world the number never goes back up. That is the whole shape of the
ladder, and `DifficultyLadderTest` asserts every cell of it.

Every level's own gate (`LevelGate`) asserts that it is beatable, that its
tightest moment is in its last tenth, that no second tap is frame perfect, that
it does not open on its hardest moment, that nothing standing on moving ground
disagrees with it, that every beam can be run under, and that none of its three
star coins sit on the line a perfect player already flies.

### The rule that measuring width missed

A boost fired late travels further than one fired early — measured, 7.68u at the
apex against 8.63u at the last legal frame — because the first jump's height is
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
node tools/abysstest.mjs       # world 3: its vocabulary, its do-not-tap wall, its six levels
node tools/orientation.mjs     # the portrait gate, on real viewports
node tools/runall.mjs          # all eighteen, flown at 60fps in a real browser
node tools/blindtest.mjs       # no gap is committed to off the edge of the screen
node tools/soundtest.mjs       # cues only: nothing streams, nothing loops, no ambience in memory
node tools/audioaudit.mjs      # nothing synthesised, nothing outside the pack
```

The browser harnesses replay the exact lines the solver exported to
`core/build/level*-plan.json`, so `gradle :core:jvmTest` has to run first.

## Verifying a level

`core/src/jvmTest/.../LevelVerifier.kt` proves a level is beatable instead of
assuming it. Because x advances at a constant speed, the frame index
determines x, so the level is a DAG over ground frames where the only choice is
wait or jump. Solving it backwards gives the take-off window for every jump —
the number that decides whether a death was the player's fault.

It extends to moving and blinking geometry with no change to the search, for
one reason: the runner never stops, so `x = RUN_SPEED * time` and the whole
world stays a function of where the player is. That invariant is why wind here
is vertical only and why a horizontal platform never carries the runner — a
conveyor would make x depend on the player's history, and the solver could no
longer say whether a level was possible at all. It is also why every phase in
worlds 2 and 3 is *derived* from the x a hazard sits at rather than hand-picked:
`blinkPhaseFor`, `Desert.phaseAt` and `Abyss.phaseAt` all answer the same
question — what does this obstacle have to be doing when the runner arrives —
and a hand-set phase is how an impossible crossing gets written by accident.

LEVEL 1 measures at: beatable, 31.7s, 26 taps, windows 0.238s at the open,
0.20-0.26s through the middle, 0.129s at 95-96%. It has not moved since.

Places where the GDD contradicts itself or its own physics are listed in
[`../docs/FLIP_ERROR_LEVEL1_NOTES.md`](../docs/FLIP_ERROR_LEVEL1_NOTES.md).
