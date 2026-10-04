# MAP_SYSTEM_V2_REPORT.md

Implementation report for **Map System V2** (Pogo Summit). Order followed, as required: **ZIP analysis → specification → implementation → tests.**

| Document | Role |
|---|---|
| `MAP_SYSTEM_V2_ANALYSIS.md` | Evidence-based study of `CustomMaps.zip` (174 files, 39 MiB) — phases 1–4 |
| `MAP_SYSTEM_V2_SPEC.md` | The V2 specification (19 sections) — phases 5–16; reconciled with the final code |
| this file | What was built, how it was verified, what is *not* done, conflicts recorded |

## 1. Hard rules — status

| Rule | Status | Evidence |
|---|---|---|
| Do not modify the Physics Core | **Kept** | `git status` shows no change under `game/src/sim/`; `tests/map/boundary.test.ts` (4 tests): `src/map` has no DOM / Three.js / clock / randomness, imports only an allow-listed part of the simulation, never assigns to physics state or constants (only the DESIGN-class respawn anchor), and the core, constants and LOCKED SPEC are byte-identical to the commit that implemented them (checked with git) |
| Do not change `Pogostuck_Physics_LOCKED_SPEC.md` | **Kept** | file untouched |
| No Pogostuck code / maps / layouts / assets | **Kept** | no legacy file is shipped; built-in maps are our own (`first_steps_v2` is `LEVEL_01` migrated); the legacy importer reads a user's own file and emits data only; tests use synthetic text samples. The licence-key file in the archive was never opened |
| Extract architecture / behaviour / workflow / data structures only | **Kept** | ANALYSIS §1–§4 state facts about the format and describe behaviour as equations in our own words |
| Integration error with physics ⇒ stop and record | **Done** | §5 below (I1–I7); none was worked around by touching the core |

## 2. What the study found (one paragraph per question of the brief)

* **Pipeline.** `.wmp` (binary, what the editor saves) / `.$$M` (text twin) → compiler → `.wmb` (+ `$$w`) → game loads WMB, resolves models and materials **by name**, starts one action coroutine per entity (20 positional `skill` values + a 32-bit flag word). Source of truth = `.wmp`; compiled = `.wmb`; editable = `.wmp`/`.$$M`.
* **`customMap.c` contains no behaviour** — only declarations and editor annotations; behaviour is inside the game program. 14 behaviours were decoded (sine mover, rotator incl. damped free spin, toggle by jump/boost parity, timed by `sin>0`, slippery flag, boost slime, mushrooms, thorns/kill, POI, parallax background, water, candles, markers, mode switches).
* **Regions are name-coded** (`kill`, `CP_<digit>`, `reg_finish`); progress = nearest point on a polyline; splits = lines of a text file; visual ≠ collision exists only by convention (two entities).
* **Limits that V2 removes:** binary tool-locked formats, positional parameters, name-coded semantics, single-digit checkpoints, 64-background cap, by-name asset lookup without validation, whole-map loading, nearest-point progress that jumps on loops, player-rule switches.

## 3. What was built

`game/src/map/` — 23 files, 4 421 lines. `game/tests/map/` — 17 files, 2 257 lines.

| Phase | Module(s) | Result |
|---|---|---|
| 5 format | `schema`, `MapLoader`, `MapIssue` | `map.json` v2: manifest, world, theme, spawn, finish, checkpoints, progress, entities (geometry / collision / platforms / hazards / interactive / moving share one typed list), regions, background, lighting, materials, vfx, audio, camera, splits, metadata, assets, prefabs, paths, chunking. Canonical serialiser (fixed key order), v1 → v2 migration, lenient/strict parse, unknown keys preserved |
| 7 prefabs | `MapPrefab`, `MapExpr`, `builtinPrefabs` | parameterised templates with `=expr` expressions (hand-written parser, no `eval`), `extends` chains with cycle detection, param type/range validation; 23 built-in prefabs |
| 3 behaviours | `MapBehavior` | `move` (sine / linear / path: polyline, Bézier, spline), `rotate` (continuous / sine / free), `toggle`, `timed`, `breakable`, `conditional`, conditions (flag / counter / checkpoint / all / any / not). Sine arithmetic is bit-identical to the existing `PhysicsWorld.offsetAt` |
| 4 collision | `MapCollision`, `MapEntity` | shapes box (+taper, anchor) / sphere / capsule / convex / mesh (ear clipping + Hertel–Mehlhorn) / slope → convex CCW polygons (≤ 16 vertices); surfaces normal / slippery (+ declared bounce / sticky / boost); hazard; trigger; one-way (map-layer gating). **Visual and collision are two blocks of one entity**, sharing transform and behaviours |
| 6 chunks | `MapChunk`, `MapWorld`, `MapRuntime` | auto-grid or explicit chunks; `UNLOADED → LOADED → ACTIVE → VISIBLE`; hysteresis; pinning of respawn/checkpoint/spawn chunks; stable collider slots with tombstones; visibility culling, LOD level selection with hysteresis, instancing batches, load estimator; document source and package source (lazy chunk parsing) |
| 10 progress | `MapProgress`, `MapCheckpoint` | 0…100 % tied to the actual route (percent anchors, windowed projection, relocation after respawn/teleport, branches/optional/secret routes); checkpoints with explicit ids and order modes (monotonic / strict / any); `RunRecorder` (splits, segment times, Δ to PB and par, deaths, jumps, medals), `MemoryRunStore` |
| 9 advanced | `MapRegion`, `MapTheme` | typed regions + effects (kill, teleport, setCheckpoint, setFlag, incCounter, camera, vfx, audio, lighting, fog, hint, reveal, emit); flags enable secrets / conditional objects / optional routes; themes (palette, sky, fog, lighting, slots, weather, day/night keyframes), 4 built-in themes mirroring the four worlds, `toWorldTheme` for the existing renderer |
| 11 validator | `MapValidator` | ERROR / WARNING / INFO; every check of the brief (missing spawn/finish, unreachable finish via the real physics, invalid collision, holes, overlap, out of bounds, missing prefab / material / texture / model / audio / path, broken behaviour, invalid progress, checkpoint order, chunk overlap) + Android budgets (draw calls, triangles, texture memory, VFX, audio, colliders, entities) for the worst window of adjacent chunks, presets `android-mid` / `android-low` |
| 14 packaging | `MapPackage` | `PGMP` container, `map_manifest.json`, CRC32 per file and for the index, deterministic build, data-only extension whitelist, path normalisation, limits 4 096 files / 128 MiB |
| 8 authoring | `MapEditor`, `tools/map-cli.ts` | headless editor engine: place / move / rotate / scale / duplicate / delete, set collision / material / behaviour / property / tags / visual, regions, triggers, progress, checkpoints, spawn, finish, theme, chunking, validate / preview / bot-preview / build / export, undo-redo (500). CLI: `validate`, `build`, `inspect`, `chunks`, `migrate`, `import-legacy` (`npm run map -- …`) |
| 19 migration | `MapCompile`, `legacyWmp`, `builtinMaps` | `levelDataToMap` (existing levels → V2), `importLegacyWmp` (user's own `.$$M` → geometry / behaviour / zone data, units ÷ 52, models and textures **not** converted), `first_steps_v2` |
| integration | `Game.ts`, `App.ts`, `package.json` | `Game.loadMap`, per-tick `beforeStep` / `afterStep`, `?map=<id>` dev entry (skips campaign progression), progress bar from `mapRuntime.progress` |

## 4. Verification

Commands run in this session (all green on the final tree):

| Check | Result |
|---|---|
| `npx vitest run` | **25 files, 350 tests passed** (182 of them in `tests/map/`; 168 pre-existing physics / route / determinism / architecture / docs tests, none modified in this phase) |
| `npx tsc --noEmit` | clean |
| `npm run build` | `dist/game.js` ≈ 819 KB |
| Chromium smoke (`?map=first_steps_v2`, 844×390) | runtime present, 1 chunk / 22 active colliders, state `CHARGING`, 2 jumps, screenshot rendered through the existing renderer |

Test coverage against the brief's phase-18 list: load · save · deserialize (`loader`, `package`) · chunk loading / unloading (`chunks`) · spawn · finish · checkpoint · progress (`runtime`, `progress`) · moving / rotating / toggle platforms · slippery · hazard · trigger region (`behavior`, `runtime`) · missing asset · invalid map (`validator`, `loader`, `package`) · small-map load (`runtime`, `fixtures`) · large-map (`large`).

Strongest guarantees tested:

1. **Migration fidelity** — `levelDataToMap(LEVEL_01)` produces the same collider set (ids, geometry, kinds, surfaces, order) and the recorded route replays with a **bit-identical state trace** — both fully loaded and with real chunk streaming at small cell sizes.
2. **Streaming is invisible to physics** — a corridor with moving / toggle / timed / rotating / breakable / spike objects, driven by 6 000 ticks of hop-cycle input, gives bit-identical states streamed vs fully loaded.
3. **Determinism and bounds** — same inputs ⇒ same state; boundary test forbids any use of non-deterministic time, randomness or core writes in `src/map`.
4. **Package safety** — bad magic, truncated file, corrupt index/file CRC, `..` and absolute paths, executable/shader extensions, > 4 096 files all rejected.

### Large-map measurements (headless Node on the development container; CPU time, not a device)

| Map | Boot | Traversal | Peak resident entities | Other |
|---|---:|---:|---:|---|
| 30 002 entities | ≈ 154 ms | ≈ 260 ms | ≈ 300 (< 2 %) | validate ≈ 405 ms; package ≈ 780 ms, 5.2 MB |
| 90 002 entities | ≈ 347 ms | ≈ 1.2 s | few hundred | streaming-only; a *package* of this map needs 5 697 files at the default cell size ⇒ rejected with `CHUNK_TOO_MANY` (use a larger cell) |

These numbers say the *algorithms* are O(active window), not O(map). **Nothing here was measured on an Android device** — the budgets in SPEC §18 remain design budgets until a device run.

## 5. Integration conflicts and capabilities not applied

Recorded, **no physics change made** (SPEC §19.5):

| # | Conflict | Decision |
|---|---|---|
| I1 | `boostZone` / `surface: boost` would write `PogoState.boost`; the locked spec sets it only by E13 | declared, emitted as a command, **not applied**; validator WARNING `CAPABILITY_UNAVAILABLE` |
| I2 | entity bounce push (`bounce`, `sticky`) is excluded by the locked spec | physics treats them as `normal`; same WARNING |
| I3 | rotating colliders do not carry the rider (the core carries translation only) | rotators are obstacles; `safe:true` on one is a WARNING |
| I4 | one-way surfaces do not exist in the core | implemented in the map layer for direction `up` only (activity gating with 0.15 m hysteresis) |
| I5 | map-mode switches (double jump, puzzle, grapple) change player rules | rejected: `world.modes` must all be `false` |
| I6 | kill / teleport / checkpoint respawn must move the player | only through the core's own `respawn()` and the DESIGN-class respawn-anchor fields |
| I7 | `LevelData` cannot express arbitrary convex pieces / hazards for the existing renderer | `MapWorld` owns all colliders; the renderer gets a best-effort `LevelData` for the expressible subset |

Smaller findings: `createPogoState` accepts a spawn up to 0.6 m above ground while the validator requires 0.3 m (validator is stricter on purpose); `path`-mode motion offsets are relative to the path's first point; a decorative object outside `world.bounds` is INFO, a gameplay object WARNING.

## 6. Not done (stated plainly)

* **Visual map editor UI.** The authoring engine and CLI exist and are tested; there is no on-screen editor.
* **Renderer builders for V2 visuals.** The runtime outputs (instancing batches, LOD levels, visibility, material/theme data) exist and are tested, but the existing renderer still draws the best-effort `LevelData`; `visual.mesh` / glTF loading, V2 materials and shadows are Phase B.
* **Consumers for camera / audio / VFX / lighting / fog zone effects.** They are emitted as `MapEvent`s; no host listener acts on them yet (HUD hints and the progress bar are wired).
* **On-device Android testing** and budget calibration — not performed.
* Only the first-steps map is shipped as a V2 built-in; no new hand-authored V2 levels were produced.
* One-way surfaces for `down/left/right`, rider-carry for rotators, and bounce/boost application are intentionally absent (§5).

## 7. How to use

```bash
cd game
npm run map -- validate path/to/map.json            # ERROR / WARNING / INFO report (--deep reachability, --budget android-low)
npm run map -- build path/to/map.json -o out.pogomap
npm run map -- inspect out.pogomap                  # verifies every CRC; prints manifest + file index
npm run map -- chunks path/to/map.json              # chunk layout report
npm run map -- migrate level_01 -o map.json         # LevelData -> map.json
npm run map -- import-legacy my_map.$$M -o map.json # your own legacy text export (geometry/behaviour only)
# play a built-in V2 map in the dev build:
#   index.html?map=first_steps_v2&debug=1
npx vitest run tests/map                            # the 182 map tests
```

Creating a moving platform needs **no code**: `place('moving_platform', {x, y})` or `setBehavior(id, { type: 'move', mode: 'sine', x: { amplitude: 4, period: 6 } })`, then `validate()` and `build()`.
