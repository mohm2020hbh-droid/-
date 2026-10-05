# MAP_VISUAL_V2_REPORT.md

Second task on Map System V2: turn the data/runtime system into a **visual map system that really runs in the game** — new renderer, materials, themes, environment, LOD, culling, chunk rendering, lighting, VFX, audio, camera, a showcase map, measurements and an Android build.

| Document | Role |
|---|---|
| `MAP_SYSTEM_V2_SPEC.md` §17 / §20 | What the visual system is (data shapes, render modes, bridges, budgets, authoring) |
| `MAP_SYSTEM_V2_REPORT.md` | First task (data, runtime, validator, packages) |
| `DECISIONS.md` DEC-054 … DEC-062 | Decisions taken in this task |
| this file | What was built, how it was verified, what is **not** done |

## 0. Rules and done-criteria — status

| Rule / criterion | Status | Evidence |
|---|---|---|
| Physics Core unchanged | **Kept** | `git diff e204a8c -- game/src/sim` shows only `routeBot.ts` (a dev tool that plays routes; two exports added, no physics code). `src/sim/core/*`, `PhysicsConfig.ts`, `Pogostuck_Physics_LOCKED_SPEC.md` untouched; `tests/map/boundary.test.ts` still passes |
| `MapRuntime` semantics unchanged | **Kept, one proven bug fixed** | Restarting after finishing far from the spawn threw "startPosition is not on any solid surface". Fix = new `MapRuntime.prepareSpawn()` (loads/activates the spawn chunk before the pogo is re-created) + regression test in `tests/map/runtime.test.ts`. No existing behaviour changed |
| No Map V2 redesign | **Kept** | the first-task modules are extended additively (`schema.ts`, `MapPrefab`, `MapTheme`, `MapValidator`, `builtinPrefabs`) |
| `showcase_v2` runs in the game | **Done** | Game Mode → *Map Showcase* → one of four themes; also `?map=showcase_v2`; headless Chromium runs, no browser errors; the route is completable (§12) |
| New visual renderer works | **Done** | `src/render/map/*` (scene, meshes, materials, textures, lights, backdrop, presentation); 59 tests in `tests/render`, 10 in `tests/map/showcase.test.ts` |
| Collision separate from visual | **Done** | decor/background entities add no colliders (test); entities without a visual get a *derived* stand-in, never the collision mesh |
| LOD works | **Done** | per-instance LOD0/1/2 by view depth, quality-scaled; tests + `renderer.info` |
| Chunk streaming works | **Done** | scene builds/disposes exactly what the runtime loads/unloads; `showcase_v2` streams its whole length with ≤ 16 resident chunks |
| Theme works | **Done** | four themes, same map, different sky/fog/lights/materials/backdrop/particles/ambience |
| VFX work | **Done** | pooled single `InstancedMesh`, event-driven; budget bug found and fixed (§9) |
| Audio events work | **Done (logic tested; sound not heard)** | `MapAudioCore` (pure) tested with a fake backend; WebAudio backend is procedural. Nobody listened to it on a device |
| Camera profile works | **Done** | `CameraRig` driven by `CameraProfile` + zones, tests |
| Validator passes | **Done** | `showcase_v2`: 0 errors, 0 warnings under `android-mid` **and** `android-low` |
| All old tests still pass | **Done** | full run: 36 files / 482 tests pass; `tsc --noEmit` clean; production bundle builds (973 KB) |
| Android: no claim without a device | **`DEVICE_TEST = NOT_AVAILABLE`** | APK built and signed; no `adb`, device or emulator exists in this environment (§14) |

## 1. Renderer architecture

```
MapDocument ──► MapRuntime (physics + streaming, unchanged) ──► loaded chunks
                  │                                                │
   pure planning (src/map, no THREE)                  MapScene (src/render/map/scene.ts)
   MapVisual · MapMaterial · MapAssets                  ├ merged batches   (chunk × material × LOD)
   MapScatter · MapRenderPlan · MapCamera               ├ instanced pools  (mesh variant × material × LOD, shared)
   MapWarm · themeKit · themesV2                        ├ single entities  (moving / gated / breakable)
                  │                                      ├ Backdrop         (far field, theme driven)
                  ▼                                      └ markers          (start / checkpoints / finish)
   validator budgets · CLI stats · scene                MapPresentation  (events → camera, lights, VFX)
                                                        MapAudio         (events → sound, in src/audio)
```

* **Four things per object**: collision ≠ visual mesh ≠ decoration ≠ background. `resolveVisual()` is the one function the renderer, the validator and `stats` share, so budgets describe what is really built.
* **Layers**: `foreground / gameplay / midground / background`, each with its own LOD thresholds, cull distance, render order and aerial perspective (`LAYER_HAZE`: midground 24 %, background 40 % toward the fog colour, so the gameplay plane stays readable).
* **Meshes** are generated (no external models): 18 builtin generators, each with LOD0/1/2, seeded variants, vertex ambient occlusion and metre-scale UVs: `platform, slab, poly_slab, rock, cliff, tree, bush, grass, mountain, cloud, water, waterfall, crystal_cluster, ancient_structure, spikes, blade, island, marker`. The triangle counts the validator uses are checked equal to what the generators build (`tests/render/meshes.test.ts`). glTF asset meshes are *declared* in the schema but not implemented (§15).
* **Scripts that stay out of the hot path**: no per-frame allocation in culling; builds are rate-limited (1 chunk per frame after the first).
* **Program warm-up** (DEC-058): `MapWarm` lists the (material × geometry kind) combinations the map uses; the renderer draws one invisible mesh per combination at load. Before this, the first walk through `showcase_v2` produced five single-frame stalls of 1.5–2.8 s (software GL) each time a new shader program appeared; after it the program count is fixed at 32 from the first frame and the same walk shows frames of 2–23 ms and no new program.

## 2. Materials

`MaterialDef`: `baseColor, baseColorMap, normalMap, normalScale, roughness, metalness, emissive(+Intensity/Map), opacity, tiling, uvScale, uvMode, surfaceType, aoStrength, flow, doubleSided` (old `shader/color/albedo/normal` still load). Rendering = `MeshStandardMaterial` + normal maps + baked AO + a small rim term + a foreground dither; materials are cached per (id × variant) and shared. Textures are generated deterministically at load (`proc:rock|grass|snow|ash|moss|wood|brick|cloud|…` and their normal maps, 128²/64²) and shared: **0.8–1.0 MB** of GPU texture memory for the whole showcase (Low tier: 0.37 MB, normal maps off). Surface types: `NORMAL ICE SLIPPERY BOUNCE HAZARD WATER LAVA GOAL`.

**Physics bridge** (DEC-057): `surfaceType` only supplies defaults to `collision.surface/hazard` when the entity states none; the validator warns on a mismatch (`MATERIAL_SURFACE_MISMATCH`). No physics constant was added; the sim never reads a render value.

## 3. Themes

| Theme | Sky / mood | Ground & rock | Particles | Ambience |
|---|---|---|---|---|
| `world_meadow` | blue day, warm horizon | grass caps on cracked brown rock | falling leaves | wind, birds, water |
| `world_ice` | pale blue, bright snow haze | frozen blue-white slabs, faceted normals | snow | strong wind |
| `world_volcanic` | plum → red → orange | basalt + glowing lava slot | embers, ash | wind + low drone, lava rumble |
| `world_mystic` | dusk violet → pink, day/night cycle | violet stone, glowing crystal | motes | chimes + drone |

Each theme carries: sky, fog, `lightingProfile`, 13 kit materials, `backdrop[]` (mountains, clouds, silhouettes, landmarks, haze bands), `particles[]`, `ambientAudio`. Switching theme changes every `@slot` material, the lights, the far field, the particles and the sound bed without touching an entity (`tests/map/showcase.test.ts` loads, validates and builds the scene under all four; `tests/render/support.test.ts` checks the four rock materials differ). Pre-V2 themes still load: they get kit-derived materials, a derived lighting profile and a derived backdrop.

## 4. Environment

Prefabs (procedural, no collision unless an author adds one): `rock_cluster, cliff, tree_cluster, bush_cluster, grass_patch, mountain, background_mountain, cloud, water, waterfall, crystal_cluster, ancient_structure`, plus V2 ledge families (`rock/wood/ice/ruin/crystal/moving/timed/breakable/hidden_ledge`), `crystal_spikes`, `rotating_blade`, `vfx_emitter`, `audio_emitter`. Variety comes from data: scale, yaw/roll, spacing, depth jitter, mesh variants, per-instance tint, theme materials and layers (`scatter` blocks); every scatter is a pure function of (seed, entity), so a map dresses identically on every device. Waterfalls and water bring their own particle and sound emitters; lava does too.

## 5. LOD

Three levels per mesh. Default view-depth thresholds (metres): gameplay 60 / 120, foreground 30 / 60, midground 34 / 70, background 80 / 160, scaled by the quality tier (`lodBias` 1 / 0.85 / 0.6) and overridable per entity (`visual.lod`, explicit LOD meshes). Instanced decoration chooses LOD **per instance**; merged batches switch per chunk batch with the nearest depth. Tests: near camera → LOD0, far camera → cheaper levels, lower `lodBias` switches earlier. Measured in the showcase: all LOD0 at gameplay depth, background/midground at LOD1–2.

## 6. Culling

Per-instance frustum and distance culling for instanced decoration; chunk-box frustum culling for merged batches; per-layer cull distances. Test: with a camera at one end of a loaded stretch the scene reports `culledFrustum > 0`, `instancesVisible < instancesTotal`, `chunksVisible < chunksLoaded`. Quality `density` removes decoration instances without changing gameplay geometry (strict subsets, no popping when quality changes).

## 7. Chunk rendering

The scene never decides what is loaded: it builds exactly the chunks `MapRuntime` reports as loaded (nearest first) and disposes what it unloads, so collision and visuals stream together. Instanced pools are shared across chunks (their number does not grow with the number of loaded chunks). On `showcase_v2` the streaming test walks the whole map: more chunks built than ever resident (≤ 16), disposed chunks are released. Measured build time per chunk: **≈ 2–35 ms** typical, 34–61 ms worst (software run, CPU geometry generation; on a phone the generator is the same code but a slower CPU, unmeasured).

## 8. Lighting

`LightRig`: **one directional sun + one hemisphere light** (no per-object lights). Fed by `lightingProfile` (sun direction/intensity/colour, ambient, fog, shadow quality, exposure). Shadow quality is capped by the device tier (`off|blob|low|medium|high` → 0 / 0 / 512 / 1024 / 2048 maps; the Low tier turns shadows off) and never raised. `lighting` / `fog` regions push overrides blended over 0.22 s; leaving pops them; day/night samples retarget the base (mystic theme). Tests: profile applied, device cap, zone push/pop with smooth blending, exactly two lights.

## 9. VFX

One pooled `InstancedMesh` (capacity 320, one draw call). Map events → effects: checkpoint, finish, break/restore, teleport, boost zone, kill, water splash (enter/exit), `vfx` effects, slide spray; theme ambient particles around the camera; emitter entities (waterfall splash, lava pop, sparks, ice) near the camera only. New kinds: `splash, lava, checkpoint, break`, plus `boost` and `speed` bursts. The map's `vfx.maxParticles × quality` is the live budget (`Vfx.setBudget`).

**Two bugs found by the new tests and fixed:** (1) the budget was never enforced (over budget the pool recycled the cursor slot whatever its state, so the live count grew up to capacity); it now recycles the oldest *live* particle. (2) `boost` and `speed` produced no particles in `burst()` although the presentation requests them for boost zones.

## 10. Audio

`MapAudioCore` (pure, unit-tested with a fake backend) + `WebAudioMapBackend` (procedural loops, no sound files): one-shots for checkpoint, break, restore, teleport, boost zone, water splash, `audio` effects — positional when the event has a position (**distance attenuation** `(1 − d/r)²` and stereo pan), per-sound **cooldowns**, a per-frame one-shot **budget**; zone **ambience layers** (wind, water, waterfall, lava, chimes, cave) that cross-fade in on `audio` effects and out when the zone is left; positional **emitters** (`audio_emitter`, waterfalls, water, lava) with at most `maxVoices` voices — the nearest win and a freed voice slot is reused; theme **bed** (chime pads for mystic, drone, lava rumble). New SFX: splash, checkpoint, break, teleport, chime, lava pop, boost zone. Surface-specific landings, ice slide, bounce, boost, goal and hazard sounds are the game's existing sound events (driven by the sim and the collider material), unchanged. 13 tests. **Not verified by ear.**

## 11. Camera

`CameraProfile` = `{ followDistance, height, lookAhead, lookAheadGain, smoothing, smoothingY, verticalBias, fov, zoom }`; defaults reproduce the old camera exactly (distance 19, FOV 30). Map base profile + `camera` regions (profile, blend seconds, priority) + `camera` effects, blended exponentially; the pogo always stays inside the safe frame. `showcase_v2` has three camera zones (climb: farther and higher with more look-ahead, hazards: closer and tighter, final: wider look-ahead). Tests: framing changes with distance/zoom/FOV/height/look-ahead/smoothing, reset restores defaults, safe frame kept.

## 12. Showcase map (`showcase_v2`)

Pure JSON (`src/map/maps/showcase_v2.json`, 106 entities, generated by a script but loaded as data), not a copy of any original map. Sections: **A** start + tutorial hops · **B** pond + moving log + waterfall · **C** gentle ramp · **D** ice crossing (frozen slabs, crystals, fog zone) · **E** vertical climb (zig-zag between cliff walls, glowing ruin, camera/audio zones) · **F** hazards (crystal spikes, rotating blade, timed ledge) · **G** secret route (seal ledge sets a flag, hidden ledges appear, crystal alcove, optional checkpoint) · **H** final challenge (moving, timed and breakable ledges) · finish gate. Four checkpoints (one optional), hints in English and Arabic, water / fog / lighting / camera / audio zones, particle and audio emitters. 3 camera zones, 3 lighting/fog zones, 2 audio zones, 4 hint zones.

* **Looks**: stylised grass-capped rock with normal maps, ice, crystals, trees, bushes, foreground grass that dithers away near the player, midground cliff islands *below* the route, hazy mountains, cloud banks, ruins, waterfall. Readability rule used: the gameplay layer is never hazed; midground/background are.
* **Calibration** (DEC-061): the first layout (gaps of 6–8 m) gave 0–4 landing plans per hop in the physics grid search. Positions are now computed from edge-to-edge gaps ≤ 4.5 m, rises ≤ 4 m, widths ≥ 6 m, and every other object (decor, zones, cameras, checkpoints, secret chain) is anchored to those platforms by the generator.
* **Per-hop solvability** (`tools/map-hops.ts`, real physics, standing at the edge facing the target, coarse grid of 390 plans/hop): most hops land 9–17 plans with a 2–3.7 m landing margin; moving-target hops (`b1 → b_mv`) are rarer (2–3 % of plans land) but exist.
* **Route verification** (machine-checked, `tests/map/showcase_route.test.ts`): the route bot found a complete plan on the static collision of the compiled level (54 hops, 65 jumps, 81 s of play, 133 search expansions, about 9 minutes of search). Replaying that plan on the **real `MapRuntime`** (chunk streaming, the moving ledges, timed ledges `f3`/`h3`, breakable `h5`, checkpoints) follows it hop by hop (largest divergence < 0.05 m) and **finishes at tick 9749 (81.2 s) with 0 deaths**, cp0–cp2 reached; `h5` crumbles right after the last jump. The fixture is `tests/fixtures/showcase_v2.route.txt`.
* **What the replay changed**: with the first timed settings (period 4 s, 65 % solid) and a 0.8 s crumbling plank, the same plan fell at `f3` and at `h5` (the plan stands on them for 1.4 s hops). They are now period 5 s / 80 % solid and a 2 s plank, with phase 0. A player can wait on the previous ledge to synchronise, so this is a comfort setting, not a proof of the hardest line.
* **Secret route**: not played end to end. Its five hops (`g1 → seal → s1 → s2 → s3 → alcove`) were probed on the static collision: 6 – 9 plans each, margins 1.5 – 2.2 m. The hidden ledges' appearance after the seal is covered by a runtime test.
* **Not verified**: the optional checkpoint `cp_secret`, any human play-through, and difficulty as felt on a touch screen.

## 13. Performance (measured)

Method: headless Chromium, **software GL (SwiftShader)**, 844×390 / 1280×600, quality *default* unless stated, `renderer.info` read on the real frame; the shadow pass is counted separately because three.js resets `info` after it (`__pogo.perf().wholeFrame`). **Draw calls, triangles, memory and texture bytes are exact; FPS/ms are not representative of a phone.**

| Metric | Result |
|---|---|
| Draw calls, whole frame incl. shadow pass (2 sampled points: spawn, climb) | **92 – 111** (default quality), 92 – 115 (High), 70 – 84 (Low: no shadows). Fixed part (pogo, sky, VFX): 38 – 39 of them |
| Triangles, whole frame incl. shadow pass (same two points) | **56k – 60k** (default), 58k – 62k (High), Low 38k – 42k. Fixed part: 27k (pogo model, sky, VFX) |
| Map share of the frame (whole frame minus the same frame without the map scene) | ≈ 53 – 73 draw calls, ≈ 29 – 33k triangles |
| Sweep over 26 points along the route (main pass only, shadow pass not counted) | 59 – 94 calls, 37k – 45k triangles |
| Validator estimate (worst window, map only) | 82 calls (59 + 9 shadow + 14 backdrop), 27.9k triangles incl. shadow pass → within ~15 % of the measurement |
| Budget (`android-mid` / `android-low`) | 150 / 90 calls, 250k / 120k triangles → the map alone fits both; the whole frame fits mid (150) and exceeds low's 90 only with shadows on |
| GPU texture memory | 0.81 – 0.98 MB (Low 0.37 MB); budget 96 / 48 MB |
| JS heap | 42 – 49 MB over the whole map |
| Shader programs | 32, all built at load; none new during play |
| Chunks resident | 6 – 15 loaded, 5 – 10 visible |
| Chunk build (CPU geometry) | median ≈ 5 – 10 ms, worst 34 – 61 ms |
| Frame time (software GL, measured while another CPU-heavy process was running; not a phone) | 1 – 78 ms depending on the frame; first frame after a far warp: 5 – 25 ms (was up to 8 s before the warm-up) |
| Previous (legacy) renderer for comparison | 101 – 124 calls, 103k – 142k triangles with shadow pass (earlier visual task) |

## 14. Android test status

* APK **built and signed**: `tools/build-apk.sh` → `android/build-legacy/PogoSummit-0.1.0-debug.apk`, 538 KB, package `app.pogosummit.game`, minSdk 24, targetSdk 34, only `VIBRATE`, v2 + v3 signature verified (debug key, not for release). It contains the current bundle, including the *Map Showcase* entry.
* **`DEVICE_TEST = NOT_AVAILABLE`.** There is no `adb`, no attached device and no emulator in this environment, so **nothing was run on Android**: no on-device FPS, frame time, memory, texture memory or chunk-load time exists, and no claim of a device pass is made. The numbers in §13 come from desktop software rendering.
* What would settle it: install the APK, Game Mode → Map Showcase → each theme, and read `window.__pogo.perf()` through a debug build or Chrome remote debugging.
* Gradle/AAB is still blocked in this environment (`dl.google.com` is not reachable), as recorded earlier.

## 15. Remaining limitations (stated plainly)

1. **No real-device test** (§14). FPS on phones is unknown; the budgets are design values, now backed by exact counters but not by hardware.
2. **Audio is untested by ear**; levels, pan and loop timbre were never listened to.
3. **glTF mesh assets** are declared in the schema and validated, but the renderer draws a placeholder rock for them (`stats.fallbackMeshes`); every shipped map uses procedural builtin meshes.
4. **Bounce / boost surfaces and boost zones** stay declared-not-applied (DEC-051): the LOCKED SPEC has no entity impulses; the visuals and sounds exist.
5. **`validate --deep`** (grid analysis) ignores moving platforms and treats ice landings conservatively; on the first `showcase_v2` layout it reported `FINISH_UNREACHABLE` and `HOP_UNREACHABLE` for most hops although the route bot later completed the map. It is slow on this map (several minutes) and is not used as evidence here; the per-hop probe, the route bot and the real-runtime replay are (§12).
6. Timed / breakable ledges are verified only by the one replay in §12 (one fixed timeline), not exhaustively; the secret route is probed per hop, not played end to end.
7. Software-GL frame times in §13 say nothing about a phone's GPU; shadows are the biggest unmeasured cost on weak devices (the Low tier switches them off).
8. The route bot is slow on maps with several moving platforms (about a minute per moving hop).

## 16. How to use

```
npm run map -- validate  <map.json> [--budget android-low] [--deep]
npm run map -- stats     <map.json>        # entity/layer/collision breakdown, materials, worst-window cost vs budgets
npm run map -- preview   <map.json> [--theme world_ice] [--at x,y;x,y] [--perf]   # PNGs from the real game (headless)
npm run map -- build     <map.json> -o out.pogomap
npm run map -- inspect   <map.json|pkg>
npm run map -- bot       <map.json>        # route bot on the real physics
npx tsx tools/map-hops.ts   <map.json>     # per-hop solvability probe
npx tsx tools/map-replay.ts <map.json> <bot-output.txt>   # replay a bot plan on the real runtime
```

A new map needs **no TypeScript**: write `map.json` (prefab or mesh id, position, rotation, scale, material, behaviour, collision, theme), `validate`, `preview`. In the game: Game Mode → Map Showcase, or `?map=<builtin id>` / `?mapUrl=<url of a map.json>` (+ `&theme=world_ice`).
