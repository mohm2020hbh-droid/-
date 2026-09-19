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

Twenty-four levels across four worlds, every one of them proved beatable by the
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

**WORLD 3 — THE ABYSS** (levels 13-18). Water, and there is not one spike in
it. Bubbles that come at you, bubbles that split the moment you commit, chains
of them to find a rhythm in, and walls of them with a single drawn opening at
head height; swells rolling down the lane; orbs the size of the screen on a
circle, a column or a diagonal; crystals swinging down out of the dark; jellies
that open on the bar and shut again; rings of pressure; arms coming up through
the floor; and drift hanging where a second tap would reach it. Built from one
shared kit, `core/.../Abyss.kt`, and assembled from the same three verbs the
solver already understands — move, blink, and where the floor is — so nothing in
this world is outside what can be proved fair.

The bubble wall is why the world exists. Every obstacle in worlds 1 and 2 is
answered by being in the air or not being in the air; a wall with a gap at head
height is answered by being at a PARTICULAR HEIGHT, so the jump has to start in
the right place and not merely at the right moment. It is also the first
obstacle in the game where the second tap is what kills you.

**WORLD 4 — CLOCKWORK** (levels 19-24). The inside of something enormous, and
the first world whose obstacles are MECHANISMS rather than things that happen to
you. Gears whose teeth come round, rams that drop on a stroke, gates that shut
across the lane, chains on a swing, vents in the deck, drums with arms, presses
that take the floor and the ceiling in turn, live rails, dropping bolts, plates
on shafts and belts that carry the deck instead of the runner. A mechanism tells
you what it is going to do next if you know what it is, which is why this world
can be the densest in the game and still be learnable — and why nothing in it is
allowed to be random. Built from one shared kit, `core/.../Clockwork.kt`, and
still only Motion and Blink underneath, so the solver proves it without being
extended.

Around them: star coins that bank the instant they are touched, a shop that
sells appearance and nothing else, English and Arabic with real RTL, and a
level select broken by world whose card names are taken from the levels
themselves — they used to be typed alongside and had quietly drifted.

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

`AudioCues.kt` gives every obstacle in worlds 2 and 3 its own voice, fired on
the transition rather than the state — a beam's charge and its strike are two
different sounds — and only within earshot. That is a gameplay cue, not
ambience: it is caused by an obstacle the player is running at.

The pack predates the abyss and contains no recording made for it, and the rule
is that the pack is the only source. What it does contain is seven short,
abstract hazard SFX named for where they were first USED rather than for what
they sound like, so world 3 speaks through those: the swell, the arm coming out
of the floor, the bubble coming apart, the jelly's warning and its open, the
ring, the crystal. The alias table is written down in `AudioMap.kt` rather than
left to be discovered, and nothing on disk is renamed or re-encoded.

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
floors that leave, beams, wind, splits, openings. It has no ceiling, and it is
what each new world escalates instead. World 1 has 41 moving parts across its
six levels; world 2 has 90; world 3 has 144; world 4 has 198.

| | L1 | L2 | L3 | L4 | L5 | L6 | opens at | moving parts |
|---|---|---|---|---|---|---|---|---|
| **world 1** | 0.129s | 0.108s | 0.100s | 0.096s | 0.079s | 0.075s | 0.129s | 41 |
| | L7 | L8 | L9 | L10 | L11 | L12 | | |
| **world 2** | 0.092s | 0.088s | 0.083s | 0.079s | 0.075s | 0.075s | 0.092s | 90 |
| | L13 | L14 | L15 | L16 | L17 | L18 | | |
| **world 3** | 0.088s | 0.083s | 0.079s | 0.079s | 0.075s | 0.075s | 0.088s | 144 |
| | L19 | L20 | L21 | L22 | L23 | L24 | | |
| **world 4** | 0.083s | 0.079s | 0.079s | 0.075s | 0.075s | 0.075s | 0.083s | 198 |

Each world opens tighter than the last one did and ends on the floor, and
inside a world the number never goes back up. That is the whole shape of the
ladder, and `DifficultyLadderTest` asserts every cell of it.

**And a third number, which is what actually makes world 3 harder.** Reflex has
a floor at 0.075s and every world reaches it, so by world 3 "tighter" has
nowhere left to go. What the abyss spends instead is the player's REST: the
longest stretch of a level that asks for nothing. The first draft of world 3
measured windows barely tighter than world 2's while leaving the player alone
for 6.11 seconds at a time, which is a level that is difficult in a report and
idle in the hand. `LevelGate.maxRest` holds it now, and the six levels measure
1.98s, 2.71s, 2.00s, 2.73s, 2.30s and 2.14s — against 3.47s at world 1's
quietest and 3.48s at world 2's. Those two are laid out with room in them on
purpose and are not held to it; the abyss is, and so is the machine (2.57s,
2.33s, 1.82s, 2.55s, 2.48s, 2.55s).

### Every size in world 3 is solved, not chosen

A jump apexes at 2.6u and covers 4.94u, of which the runner is 0.9u, so the
take-off window over a thing W wide and H tall is (the time the arc spends above
H) minus (W + 0.9) / 9.5. A 1.0 x 1.0 spike measures 0.239s that way and 0.238s
in the verifier, which is what makes the arithmetic worth trusting — and what
caught the first draft of the abyss kit, where an arm 1.4 wide and 2.0 tall came
out at **0.068s**, an inch off frame perfect, and looked perfectly reasonable
written down. Nothing in this world is wide AND tall any more: arm 1.0 x 1.5 and
0.177s, crystal 1.0 x 1.3 and 0.203s, jelly 0.204s, ring 0.216s. The same sum
sets the clearance the orbs and the drift keep over a running player's head.

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
node tools/abysstest.mjs       # world 3: its vocabulary, its bubble wall, its six levels
node tools/clockworktest.mjs   # world 4: its parts, its gate, its gauntlet, its six levels
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
