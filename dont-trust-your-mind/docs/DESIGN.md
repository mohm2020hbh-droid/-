# DON'T TRUST YOUR MIND — Design

> لا تثق بعقلك

## A. Game design summary

A short-form deception puzzle game. One puzzle per stage, five to sixty seconds
each. The difficulty never comes from having more pieces on screen; it comes
from the player's own first interpretation being wrong.

The contract with the player is stated in the intro and never broken:

> Every puzzle has a logical answer. It is not the first one you think of.

Every stage therefore ends with an explanation. A trick the player cannot
reconstruct afterwards is a bad trick, not a hard one.

## B. Core gameplay loop

```
Read instruction  →  Form an obvious interpretation  →  Act on it  →  Fail
        →  Re-read  →  See the second interpretation  →  Solve  →  "Oh."
                            →  Stars, next stage
```

Failure is cheap on purpose: wrong taps cost stars, never progress. Retry is one
button. The loop only works if experimenting is free, so almost every puzzle has
unlimited attempts and no clock.

What keeps it moving:

- **Immediate legibility.** The board is two to five objects. Nothing to parse.
- **Cheap failure.** A wrong tap shakes, flashes red, and often says *why*.
- **Guaranteed payoff.** The explanation always lands, solved or skipped.
- **Escalation of kind, not size.** Each chapter attacks a different faculty.

## C. Puzzle taxonomy

Ten trick families, all expressible as data over one renderer:

| Type | Idea | Example stage |
|---|---|---|
| Visual | Perception contradicts the literal question | 2 — a huge `9` beside a tiny `11` |
| Semantic | The sentence means something other than assumed | 24 — "do not tap the red one" |
| Language | The writing system is part of the answer | 4, 11, 19 — first/last/leftmost letter |
| Position | Order and counting direction | 5 — "the third square", counted from the reading side |
| Timing | *When* matters as much as *what* | 33, 34, 39 — wait, hurry, or hit a window |
| Memory | Information shown then withdrawn | 21, 25, 26, 30 |
| UI | The interface itself is a target | 31, 35, 38, 40, 48 |
| Reverse | The answer is the negation | 3, 27, 29 |
| Multi-step | Two or three ordered actions | 9, 15, 47 |
| Meta | The game's own rules are the puzzle | 10, 22, 28, 41, 43, 46, 50 |

### The meta arc

The game teaches a rule, then tests whether the player learned *the rule* or
merely *a pattern*. This is the spine of the design:

- Stage 1 — "tap the red one" with no red shape. The word is the only red thing.
- Stage 22 — same instruction, and now a shape *is* red. The rule never changed;
  players who memorised "the word is always the answer" get it wrong.
- Stage 10 — "every instruction here is false." Invert it.
- Stage 28 — "every instruction here is true." Players who learned to invert lose.
- Stage 2 — size is a lie; value decides.
- Stage 43 — every value is identical, so size is all that remains.
- Stage 3 — "don't tap anything" is literal.
- Stage 50 — "there is no solution" is also literal, and after forty-nine stages
  of suspicion, believing it is the hardest move in the game.

None of these punish the player for a rule they were never shown. Each callback
names its own earlier stage in the explanation.

## D. Technical architecture

Godot 4.3, GL Compatibility renderer, portrait, fully offline.

```
Content (JSON)  →  Model (typed)  →  Session (pure logic)  →  Board (rendering)
                                            ↑                       ↓
                                       PuzzleScreen  ←──────  taps, chrome
```

Four layers, deliberately separated:

1. **Content** — `content/puzzles/*.json`. No code. Adding a puzzle is a data edit.
2. **Model** — `PuzzleDefinition`, `PuzzleElement`, `SolutionRule`, `PuzzleResult`.
   Parse JSON into typed objects once, at load.
3. **Runtime** — `PuzzleSession` holds every rule and owns no nodes, so the whole
   rule system is testable headlessly. `PuzzleBoard` renders elements and feeds
   it taps.
4. **Presentation** — screens, theme, widgets.

Five autoloads, each with one job: `SaveManager`, `Loc`, `Audio`, `Puzzles`,
`Game`.

### Why the renderer knows nothing about any puzzle

Fifty bespoke scenes would not survive reaching five hundred puzzles. Instead a
puzzle is a list of typed elements plus a solution rule, and every trick in the
game is a *composition* of six element kinds and four rule kinds:

- Element kinds: `label`, `chars`, `shape`, `button`, `zone`, `spacer`
- Rule kinds: `tap`, `sequence`, `no_tap`, `hold`
- Targets: an element id, `char:<id>:<n>`, `board`, or `ui:<chrome>`

`ui:` targets are what let the interface itself become an answer. A puzzle
*arms* chrome by listing it in `ui_targets`; armed chrome loses its normal job
for that stage and routes taps into the rule instead. Un-armed chrome never
feeds the session, so a player can always reach for Hint or Back without
risking a strike they did not sign up for.

## E. Folder structure

```
dont-trust-your-mind/
├── project.godot            5 autoloads, portrait, GL Compatibility
├── export_presets.cfg       Android, no INTERNET permission
├── run_tests.sh
├── assets/fonts/            Cairo (SIL OFL 1.1) — Arabic + Latin in one family
├── content/
│   ├── puzzles/             chapter_01..05.json (50), daily.json (10)
│   └── locale/ui_strings.json
├── src/
│   ├── core/                save, localization, audio, puzzle library, flow
│   ├── puzzles/
│   │   ├── model/           data classes
│   │   ├── runtime/         session (logic) + board (rendering)
│   │   └── widgets/         char_strip, shape_widget
│   ├── ui/{screens,components,theme}
│   └── audio/sfx_synth.gd   sounds generated at boot, no audio assets
├── tests/                   6 suites + smoke + screenshot harness
└── docs/
```

## F. Data model

```jsonc
{
  "id": "p004_first_letter",
  "type": "language",
  "par_time": 14,              // beat this cleanly for the third star
  "reward": 12,
  "time_limit": 0,             // 0 = untimed
  "max_attempts": 0,           // 0 = unlimited
  "locales": {
    "ar": {
      "instruction": "اضغط على أول حرف",
      "layout": "column",       // flow | row | column | grid2 | free
      "elements": [ { "id": "word", "kind": "chars", "text": "بداية" } ],
      "solution": { "kind": "tap", "target": "char:word:0", "expect": ["ب"] },
      "hints": ["…", "…"],
      "explanation": "…",
      "wrong": { "char:word:4": "That is the last letter." },
      "ui_targets": []
    },
    "en": { /* an independent spec — different board, different answer */ }
  }
}
```

The locale split is the important part. A puzzle is **not** one board with
translated strings: each language gets its own elements, its own solution and
its own explanation, because in this game the language is often the trick.
Nothing in the loader assumes the two agree.

`expect` is an authoring assertion, not gameplay: the content test shapes the
real text with the real font and fails the build if a `char:` index does not
land on the character the author named. That check caught three genuine
off-by-one bugs in this content set before anyone played it.

## G. UI structure

```
┌──────────────────────────────┐
│ ‹  Stage 12        12.4s  2/3│   chrome — and sometimes the answer
├──────────────────────────────┤
│        Instruction           │   the game's voice, always here
├──────────────────────────────┤
│                              │
│          Board               │   elements, centred, fluid width
│                              │
├──────────────────────────────┤
│  hint / wrong-answer note    │
│    [ Hint ]    [ Skip ]      │   thumb zone
└──────────────────────────────┘
```

One rule the player can rely on, established in stage 49: **the real instruction
is always at the top in its own type.** Text inside the board is a puzzle
element and may lie. Without that rule, "the instruction is fake" puzzles would
be unfair; with it, they are solvable.

Timer, attempts and stars appear only when a puzzle uses them.

## H. Localization strategy

Two languages, both first-class. Arabic is genuinely RTL — no screen flipping.

- **UI shell** — `content/locale/ui_strings.json`, keyed lookups via `Loc.t()`.
- **Puzzle content** — not translated; authored per locale (see F).
- **Direction** — `Control.layout_direction` on containers, so Godot mirrors
  rows, grids, alignment and the back arrow. `FREE` layouts opt out, because a
  memory puzzle about "where the red one was" must put it in the same physical
  place in both languages or its own explanation stops being true.
- **Font** — Cairo covers Arabic, Latin, Western and Arabic-Indic digits and
  Arabic punctuation in one family, so mixed text never falls back.
- **Numbers** — left to the BiDi algorithm, which embeds them LTR inside Arabic
  automatically. No manual reordering anywhere in the codebase.

### The per-character problem

Arabic is cursive: letters change shape according to their neighbours. Splitting
a word into one `Label` per letter destroys it. So `CharStrip` shapes the whole
string once through TextServer (HarfBuzz), then recovers per-character targets
from the shaped result:

- grapheme boundaries from `shaped_text_get_character_breaks()`, so a letter plus
  its diacritics is one target;
- each grapheme's visual box from `shaped_text_get_selection()`, which is
  BiDi-aware.

Measured, for the two words the game actually uses:

| | character 0 | last character |
|---|---|---|
| `بداية` (ar) | x 105–125 — **rightmost** | x 0–23 — leftmost |
| `START` (en) | x 0–39 — **leftmost** | x 75–116 — rightmost |

"Tap the first letter" is thus one rule that resolves to opposite sides of the
screen per language, with correct cursive shaping — and stage 19 ("the second
letter from the left") deliberately makes the visual and logical orders disagree.

The strip auto-fits its font to the width available and draws a slot guide under
each character, so the tap targets are visible rather than guessed at.

## I. Save system design

Plain JSON at `user://profile.json`, wrapped in `{sum, payload}`.

- **Atomic** — write to `profile.tmp.json`, read it back and compare, copy the
  previous file to `profile.backup.json`, then rename. A kill mid-write cannot
  destroy a profile.
- **Checksummed** — a payload whose hash does not match is treated as corrupt
  and the backup is loaded instead. If both are unreadable the profile resets to
  defaults rather than crashing.
- **Coalesced** — a solve touches several fields; writes are batched behind a
  short timer and forced on pause, back and close.
- **Migrating** — missing keys are filled from defaults on load, so an older
  profile survives a new build.

Stored: language, current stage, per-stage `{solved, stars, best_time,
best_attempts, plays}`, points, day streak, daily results by date, achievements,
settings. Bests only ever improve, so replaying a stage can never cost anything.

Changing language rewrites nothing but the language field.

## J. First ten puzzle designs

| # | Instruction | The obvious move | The answer | Why it is fair |
|---|---|---|---|---|
| 1 | Tap the red one | Hunt for a red shape | The word "RED", printed red | No shape on screen is red |
| 2 | Tap the biggest number | The giant `9` | `11` | "Biggest" is value; size is decoration |
| 3 | Don't tap anything | Tap the pulsing buttons | Wait five seconds | The sentence is literal |
| 4 | Tap the first letter | The leftmost glyph | Logical char 0 — rightmost in Arabic | Reading direction, stated in the hint |
| 5 | Tap the third square | Third object from the left | Third *square* from the reading side | A circle is not a square |
| 6 | Tap this sentence | One of three buttons reading "THIS SENTENCE" | The instruction itself | "This" points at the sentence saying it |
| 7 | Tap the word written in green | The word "GREEN" | The word "BLUE", printed green | Ink was asked for, not meaning |
| 8 | Tap the thing you cannot see | Guess | The empty fourth grid slot | The slot has a visible frame — not a pixel hunt |
| 9 | Tap blue, then yellow | Look for yellow | Blue first; yellow appears | The first step creates the second |
| 10 | Every instruction here is false. Tap the circle. | Tap the circle | The square | Exactly two shapes, so the inverse is unambiguous |

By stage 3 the player knows the game will lie. By stage 10 they know it will
tell them it is lying. The remaining forty stages are built on that.

## Anti-frustration rules

Enforced by the content tests, not by convention:

- Every puzzle declares two hints, an explanation, and per-answer wrong-notes
  where a specific misreading is likely.
- Every interactive element is at least 88×88 on the design viewport; character
  targets snap to the nearest letter rather than requiring precision.
- Stages unlock two ahead of the furthest solved one, so a single hard puzzle
  can never wall off the rest of the game.
- Skipping is always available except where skipping *is* the answer.
- A puzzle that arms the Hint button declares no hints, so nothing is silently
  taken away.
- Every declared solution is played end-to-end in both languages by the test
  suite, which also asserts the answer is on screen at the moment it must be
  tapped.
