# Test plan

Everything below is automated unless marked **manual**. Run it all with:

```bash
GODOT=/path/to/godot ./run_tests.sh
```

Current status: **4417 automated checks, 0 failures**, plus 21 end-to-end smoke
assertions.

## Suites

| Suite | Checks | Covers |
|---|---:|---|
| `content_validation` | 2552 | Every puzzle in both languages: schema, target resolution, chrome arming, tuning ranges, and character-index correctness against real shaped text |
| `session_rules` | 45 | All four solution kinds, windows, attempt and time limits, scheduled reveals, tap side effects, hints, skip, star grading |
| `localization` | 399 | Every UI string in both languages, writing direction, BiDi character positions, cursive shaping, font glyph coverage |
| `save_system` | 27 | Round trip, checksum corruption, backup recovery, total loss, progression bookkeeping, streak arithmetic, daily idempotence, reset |
| `rendering` | 666 | Every puzzle built into live controls in both languages; all screens instantiate; daily determinism; board width measurement |
| `playthrough` | 592 | Every puzzle solved end-to-end using only its declared solution, in both languages, asserting the answer is visible when it must be tapped |
| `responsive` | 136 | Every screen and ten sample stages at 16:9, 20:9, 600px and tablet, in both languages, asserting no horizontal overflow |
| `smoke` (needs a display) | 21 | Cold first run → language choice → every screen → solve stage 1 → save → reload |

## What each requirement maps to

### Navigation
Automated: every screen instantiates and builds content (`rendering`), and the
smoke test opens all five hub screens in sequence. **Manual:** Android hardware
back from each screen.

### Save / load
Automated end to end in `save_system`, including three corruption modes:
tampered checksum (recovers from backup), unparseable JSON with no backup
(resets to defaults), and a verified-write failure (keeps the previous save).
Reload-after-solve is re-checked in `smoke`.

### Language switching
Automated: `localization` asserts every key resolves in both languages and that
direction, alignment and BiDi positions flip. Settings rebuilds in place and
`save_system` asserts a reset keeps the chosen language. **Manual:** switch
language mid-session and confirm progress and stars are untouched.

### RTL
Automated: writing direction, start/end alignment, cursive shaping (a joined
word must measure narrower than its letters shaped separately), glyph coverage
for Arabic letters, Latin, both digit sets and Arabic punctuation, and the
character-position table in `docs/DESIGN.md`. Visual confirmation is in
`docs/screenshots/`. **Manual:** Arabic on a physical device, checking for
clipping at the largest system font scale.

### Puzzle completion, wrong answers, retry, hints
`playthrough` solves all 120 puzzle-language combinations and asserts a clean run
costs zero wrong attempts and earns three stars. `session_rules` covers wrong
taps, sequence resets, attempt exhaustion, timeouts, both hint levels and skip.

### Daily puzzle
`rendering` asserts the same date always selects the same puzzle, that a month of
dates reaches at least five distinct puzzles, and that the daily resolves in both
languages. `save_system` asserts a solved daily cannot be overwritten by a later
worse attempt and pays out once.

### Audio
Sounds are synthesised at boot, so there is no missing-asset failure mode. The
bus is muted rather than skipped when sound is off, and volume changes apply
immediately. **Manual:** listen on a device; confirm the game is silent with
sound disabled and that vibration follows its own setting.

### Android touch
Automated: every interactive element is at least 88×88 on the design viewport,
character targets snap to the nearest letter, and no screen overflows
horizontally at any tested size. **Manual:** double-tap protection, taps during
the solve animation, and the gesture-navigation bar on a device with a notch.

### Screen sizes
`responsive` covers 720×1280, 720×1600, 600×1024 and 1200×1920 across every
screen and ten stages in both languages. **Manual:** landscape is not supported
and the app is orientation-locked to portrait.

## Error-prevention checklist

| Risk | Handling |
|---|---|
| Double taps | The session ignores every input once finished; chrome is disabled on solve |
| Wrong touch detection | Minimum 88×88 targets; character taps snap to the nearest grapheme |
| Screen resize | Fluid board width, verified at four aspect ratios |
| Arabic text clipping | Auto-fitting font size, measured label widths, no fixed-width text |
| Font fallback | One family covering both scripts; a test asserts zero missing glyphs |
| Save corruption | Checksum, verified write, rolling backup, default fallback |
| Duplicate daily | Results keyed by date; a solved day is never overwritten |
| Puzzle state bugs | Session logic is node-free and covered by 45 direct rule checks |

## Known limits

- The APK itself was not built here: Godot's Android export templates and an SDK
  are not present in this environment. The export preset is committed and
  `--export-pack` succeeds, producing a 441 KB resource pack, so the resource
  pipeline is verified; the final `--export-release` step needs a machine with
  the templates installed.
- No physical-device pass. Everything above ran on Linux under Godot 4.3 with a
  virtual display.
