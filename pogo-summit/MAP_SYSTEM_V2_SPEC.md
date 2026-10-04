# MAP_SYSTEM_V2_SPEC.md

Specification of **Map System V2** for Pogo Summit (Android · landscape · touch-only).
Input: `MAP_SYSTEM_V2_ANALYSIS.md`. Implementation: `game/src/map/`. Tests: `game/tests/map/`.

## 0. Ground rules

1. **Physics is not part of the map system.** Map V2 never changes the Physics Core (`src/sim/core/*`, `PogoState`, `PhysicsConfig`) or the constants of `Pogostuck_Physics_LOCKED_SPEC.md`. A map may only supply *where things are* (collision shapes, surface kinds, moving objects, zones). Numbers that the locked spec fixes (slide, gravity, launch…) are **not exposed** as map parameters.
2. **Boundary.** The map layer talks to physics through exactly these seams: (a) it builds the collider set (`MapWorld extends PhysicsWorld`, in `src/map/`, sim untouched); (b) it toggles which solid colliders are *active* and where animated colliders are *at tick t* — as pure functions of `(tick, player counters, flags)`; (c) it reads `PogoState` after each step (position, counters, tick); (d) its **only state write** is the respawn anchor (`safeGround/safeLx/safeLy/safeNx/safeNy`, class DESIGN — not locked physics) and teleports implemented through the core's own `respawn()`. Anything else the legacy kit could do to the player (boost state, double jump, push) is *declared* in data, *emitted as a command*, and **not applied** (§19, "Integration conflicts").
3. **Originality.** No legacy code, model, texture, shader source or map layout is used. The legacy importer (§19.2) is a developer tool that reads a user's own file and emits data.
4. **Determinism.** Every map behaviour is a pure function of `(tick, counters, flags, chunk-activation)`; the same inputs give bit-identical states (tested, including *streamed vs fully-loaded*).
5. **Honesty about measurements.** Budgets in §18 are *design budgets*; they were not measured on a device in this task.

---

## 1. What we found in CustomMaps (summary → ANALYSIS §1–§4)

174 files / 39 MiB: 2 maps + editor kit. Source of truth = binary `.wmp` (+ text twin `.$$M`), compiled `.wmb` embeds textures (68 % of its size). **`customMap.c` contains no behaviour** — only action names and editor annotations; behaviour is inside the game. 14 behaviours decoded: moving (sine, per-axis), rotating (continuous / sine / damped free spin), toggle blocks (jump/boost-count parity), timed blocks (sin > 0), slippery (author flag on collision entity), boost slime, mushrooms (entity bounce), thorns/kill region, POI, background parallax (cap 64), water, candles, spawn/start/finish markers, map mode switches. Regions are **name-coded** (`kill`, `CP_n`, `reg_finish`), progress = nearest point on a polyline (`path_progress`), splits = lines of `splitSetup.txt`. Visual ≠ collision exists only by convention (PASSABLE visual entity + INVISIBLE solid entity; 13 : 1 triangle ratio).

## 2. How the old system works (→ ANALYSIS §2)

WED → `.wmp` (source) → compiler → `.wmb` (+ `$$w`) → game loads WMB, resolves models/materials *by name*, runs one action coroutine per entity (parameters `skill1..20` + 32-bit flags), per-frame region/path/split logic.

## 3. What we keep / what we do not copy

**Keep (as ideas):** behaviours as pure functions of a global clock · parameter schemas with defaults · visual/collision split · typed gameplay roles · progress path + checkpoint splits + PB delta · palette-based theming · parallax layers with anchors · toggle/timed puzzle blocks · live reload.
**Do not copy:** binary tool-locked formats · positional `skill`/flag parameters · name-coded regions · block-sketch geometry as final look · by-name asset lookup without validation · executable content in packages · hard caps (64 backgrounds, 9 checkpoints) · whole-map loading · nearest-point progress · player-rule switches (double jump, puzzle).

---

## 4. Architecture of Map System V2

```
                         ┌──────────────── authoring ────────────────┐
  CLI / external tools ─►│ MapEditor (commands, undo/redo, preview)  │
  JSON text editor  ────►│ MapLoader (parse · migrate · normalize)   │──► MapDocument (map.json)
  LevelData / legacy ───►│ MapCompile.importers                      │          │
                         └────────────────────────────────────────────┘          │ validate (MapValidator)
                                                                                 ▼
                                   build ──► MapPackage (.pogomap: manifest + core + chunks + assets, CRC32, data-only)
                                                                                 │
   ┌─────────────────────────────── runtime ─────────────────────────────────────▼──────────────────────────────────┐
   │ MapRuntime                                                                                                     │
   │   ChunkManager ── ChunkSource (document | package) ── load / activate / unload / cull / LOD / instancing        │
   │   MapWorld (extends PhysicsWorld) ◄── MapCollision (shapes → convex polygons) ◄── resolved MapEntity (+prefabs)│
   │   MapBehavior (move · rotate · toggle · timed · breakable · conditional)  ──► collider offset / angle / active │
   │   MapProgress (route projection) · MapCheckpoint · RunRecorder (splits, PB, medals)                            │
   │   Regions + Effects ──► MapEvents ──► host (camera, VFX, audio, HUD, teleport, kill)                           │
   │   MapTheme (palette · fog · lighting · day/night · weather) ──► WorldTheme for the renderer                    │
   └────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
                                  │ pre-step / post-step hooks
                                  ▼
                       Game loop → PogoPhysicsController (Physics Core, unchanged)
```

Module list (all in `game/src/map/`): `schema` (all types incl. `MapManifest`) · `MapIssue` · `MapLoader` · `MapExpr` · `MapPrefab` (+`builtinPrefabs`) · `MapEntity` · `MapCollision` · `MapBehavior` · `MapRegion` · `MapTheme` · `MapProgress` · `MapCheckpoint` · `MapChunk` · `MapWorld` · `MapRuntime` · `MapCompile` · `MapValidator` · `MapEditor` · `MapPackage` · `legacyWmp` · `builtinMaps` · `index` (barrel). Dev tool: `game/tools/map-cli.ts`.

Coordinate system: **metres**, +x right, +y up, `z` = depth layer (visual only; negative = farther), rotation in degrees CCW, 1 m = 52 Q (physics quants, `WORLD_Q_PER_M`). Time: ticks of the 120 Hz physics loop; authoring uses **seconds** (`tick / 120`).

---

## 5. Map data format (`map.json`)

`format: "pogo-summit.map"`, `formatVersion: 2`. Canonical serialisation: UTF-8 JSON, 2-space indent, **fixed key order** (schema order), arrays preserved, no `undefined`/`NaN` (loader rejects), numbers with ≤ 6 decimals when written by tools. Unknown top-level keys are kept under `extensions` (forward compatibility).

### 5.1 Top level (the 22 blocks of the brief)

| Block | Field | Notes |
|---|---|---|
| MapManifest | `manifest` | id, name, author, description, version, thumbnail, difficulty, estimatedTimeSec, theme, mapSize, checkpointCount, tags, requirements |
| WorldSettings | `world` | bounds, killY, chunk defaults, `modes` (reserved, must be false) |
| Theme | `theme` | `{ref}` to a built-in/package theme or an inline `ThemeDef` |
| Spawn | `spawn` | position (on a surface), facing |
| Finish | `finish` | one or more zones (compiled to goal triggers) |
| Checkpoints | `checkpoints[]` | ordered, region + respawn point (+ optional progress anchor) |
| ProgressPath | `progress` | routes (main / optional / branch / secret) with percent anchors |
| Geometry · Collision · Platforms · Hazards · InteractiveObjects · MovingObjects | `entities[]` | **one list**, discriminated by `type`; every entity may carry `visual`, `collision`, `behavior` |
| Regions | `regions[]` | typed zones with enter/exit/stay effects |
| Background | `background` | sky, layers (parallax factors, z), props are `type:"background"` entities |
| Lighting | `lighting` | sun, ambient, shadows, light volumes |
| Materials | `materials` | map-local material definitions (+ theme slots) |
| VFX · Audio | `vfx`, `audio` | budgets + emitter *entities* (`type:"vfx"/"audio"`) and zone effects |
| Camera | `camera` | default framing; camera *zones* are regions |
| Splits | `splits` | split list, par, medal targets |
| Metadata | `metadata` | localised name/description, credits, license, changelog |
| (support) | `assets[]`, `prefabs{}`, `paths[]`, `chunks` | declared assets, map-local prefabs, motion paths, chunk layout |

### 5.2 Common element contract

Every `MapEntity`:

```jsonc
{
  "id": "plat_07",                 // unique in the map, [A-Za-z0-9_.-]{1,64}
  "type": "platform",              // platform|wall|slope|ceiling|hazard|interactive|moving|decor|background|light|vfx|audio|marker|trigger
  "name": "Mossy ledge",           // optional, editor label
  "prefab": "stone_platform",      // optional: defaults come from the prefab
  "position": { "x": 12.5, "y": 3.0, "z": 0 },
  "rotation": 0,                   // degrees CCW
  "scale": { "x": 1, "y": 1 },
  "tags": ["tier1", "mossy"],
  "properties": { "width": 6, "thickness": 1.2 },   // prefab parameters
  "visual":   { … },               // §6.2
  "collision":{ … },               // §9   (object or array; omitted/null = no collision)
  "behavior": { … },               // §8   (object or array)
  "chunk": "c_00",                 // optional explicit chunk (else auto by position)
  "enabled": true
}
```

Precedence when resolving an entity: **prefab defaults < entity fields < `properties` expansion inside templates**. Anything not stated falls back to the schema default in §5.4.

### 5.3 Example (small map, abridged)

```jsonc
{
  "format": "pogo-summit.map", "formatVersion": 2,
  "manifest": { "id": "demo_small", "name": "Demo Small", "author": "Pogo Summit", "version": "1.0.0",
                "difficulty": 2, "estimatedTimeSec": 40, "theme": "autumn_hills",
                "mapSize": { "width": 40, "height": 20 }, "checkpointCount": 1, "tags": ["demo"],
                "requirements": { "minFormatVersion": 2, "capabilities": ["move"], "physics": "locked-spec-1" } },
  "world": { "bounds": { "minX": -8, "maxX": 48, "minY": -12, "maxY": 30 }, "killY": -20 },
  "theme": { "ref": "autumn_hills" },
  "spawn": { "id": "spawn", "position": { "x": 0, "y": 0 } },
  "finish": { "zones": [ { "id": "finish", "shape": { "kind": "box", "w": 2, "h": 4 }, "position": { "x": 38, "y": 8 } } ] },
  "checkpoints": [ { "id": "cp0", "order": 0, "name": "Half way", "region": { "kind": "box", "x": 20, "y": 4, "w": 6, "h": 8 }, "respawn": { "x": 20, "y": 3 } } ],
  "progress": { "routes": [ { "id": "main", "kind": "main", "points": [ {"x":0,"y":0}, {"x":20,"y":3}, {"x":38,"y":8} ] } ] },
  "splits": { "splits": [ { "id": "s0", "name": "Half way", "checkpoint": "cp0", "parSec": 18 } ], "targets": { "gold": 30, "silver": 40, "bronze": 55 } },
  "entities": [
    { "id": "start", "type": "platform", "prefab": "stone_platform", "position": { "x": 0, "y": 0 }, "properties": { "width": 8, "thickness": 6 } },
    { "id": "mover", "type": "moving", "prefab": "moving_platform", "position": { "x": 20, "y": 3 },
      "behavior": { "type": "move", "mode": "sine", "x": { "amplitude": 4, "period": 6 } } }
  ]
}
```

### 5.4 Defaults (selected)

`rotation 0 · scale {1,1} · tags [] · enabled true · collision.surface "normal" · collision.safe = (no move behaviour) · move.phase 0 · rotate.mode "continuous" · toggle.modulus 2 · timed.duty 0.5 · region.once false · chunk size 32 × 32 m · difficulty 1`.

---

## 6. Object system

### 6.1 Entity types and roles

| `type` | Role | Typical blocks |
|---|---|---|
| `platform`, `wall`, `slope`, `ceiling` | static gameplay geometry | `collision`, `visual` |
| `hazard` | kills on touch | `collision.hazard = true` |
| `moving` | platform with a `move` behaviour | `collision`, `behavior` |
| `interactive` | toggle/timed/breakable/conditional objects | `collision`, `behavior` |
| `decor` | visual only (trees, rocks, signs) | `visual` |
| `background` | parallax layer prop | `visual.layer`, `visual.parallax` |
| `light`, `vfx`, `audio` | emitters (budgeted) | `properties` |
| `marker` | start/finish/checkpoint/hint markers | `visual` |
| `trigger` | invisible zone object (alternative to `regions[]`) | `collision.trigger` |

`type` is a *semantic category* used by tools, validator and budgets; the runtime acts on `collision` + `behavior`.

### 6.2 Visual block

```ts
interface VisualDef {
  kind: 'procedural' | 'mesh' | 'sprite' | 'none';
  style?: string;          // procedural style understood by the renderer (rock|wood|ice|bounce|…)
  mesh?: string;           // asset id (glTF/GLB) or builtin id — NOT the collision shape
  material?: string;       // material id, or "@ground" / "@secondary" / "@water" / "@lava" theme slots
  tint?: string;           // #rrggbb multiplier
  lod?: { distance: number; mesh?: string; tris: number }[];   // sorted ascending
  instancing?: boolean | string;    // true = auto group by mesh+material, string = explicit group id
  tris?: number;           // declared triangle count (budgeting) when mesh info is not in assets[]
  layer?: string;          // background layer id
  parallax?: { x: number; y: number };
  castShadow?: boolean; receiveShadow?: boolean;
  emissive?: number;       // 0..1
  visibleWhen?: Condition; // see §8.7
  seed?: number; decor?: string;   // renderer hints
}
```

**Visual mesh ≠ collision shape** (hard requirement): a rock may use a 6 000-triangle mesh and a 5-point convex collider. The two blocks share the entity's transform and behaviours, so they can never drift apart (the legacy two-entity convention is gone).

### 6.3 Entity lifecycle (runtime)

`parsed → resolved (prefab+defaults) → chunk-assigned → [chunk load] instantiated (colliders added inactive) → [chunk activate] colliders active, behaviours ticking → [chunk deactivate] colliders inactive, behaviours frozen at their analytic state → [chunk unload] instance dropped`. Because behaviours are analytic, re-activation needs no stored state (except counters/flags kept by `MapRuntime`).

---

## 7. Prefab system

A prefab is a **parameterised entity template**, data only:

```jsonc
{ "id": "stone_platform", "category": "platform", "label": "Stone platform",
  "params": { "width":  { "type": "number", "default": 6, "min": 0.5, "max": 60, "unit": "m" },
              "thickness": { "type": "number", "default": 3, "min": 0.2, "max": 40 },
              "taper": { "type": "number", "default": 0.72, "min": 0.2, "max": 1 } },
  "entity": { "type": "platform",
              "visual": { "kind": "procedural", "style": "rock", "material": "@ground" },
              "collision": { "shape": { "kind": "box", "w": "=width", "h": "=thickness", "taper": "=taper", "anchor": "topCenter" }, "surface": "normal", "material": "grass" },
              "tags": ["stone"] } }
```

* A string starting with `=` is an **expression** over the prefab params (`+ - * / ( )`, `min max abs clamp`, numbers; no variables beyond params, no calls to the outside — a hand-written parser, never `eval`).
* `extends` lets a prefab inherit another (cycle detection in the validator).
* Resolution: `resolveEntity(doc, e, registry)` merges `prefab.entity` ← `e` (entity fields win; `properties` override param defaults), expands expressions, validates against `ParamDef` ranges.
* **Built-in prefabs (data)**: `stone_platform`, `ice_platform`, `wood_platform`, `moving_platform`, `bounce_platform`, `spike`, `wall`, `slope`, `tree_cluster`, `rock_cluster`, `boost`, `checkpoint`, `start`, `finish`, `decoration`, `background_object`, `hazard`, `trigger`, plus puzzle/interactive prefabs `toggle_block`, `timed_block`, `rotating_blade`, `breakable_platform`, `one_way_platform`. Maps may add or override prefabs in `prefabs{}`; **no code change is needed to create a new moving platform** — place the prefab and set `behavior`.
* Capability honesty: `bounce_platform` (entity push) and `boost` (power-jump slime) are *declarative* (§19) and carry `requires: ["bouncePush"]` / `["boostSurface"]`; the validator reports them as WARNING with the capability status.

---

## 8. Behaviour system (data-driven)

A behaviour is a typed block in `entity.behavior` (object or array; at most one *motion* behaviour, at most one *rotation*, at most one *gating* behaviour — toggle/timed/conditional/breakable — per entity). Evaluation is `state = f(tick, runtime view)`; no hidden state except `breakable` timers kept by the runtime.

Time conversions: `t_s = tick / 120`; legacy-style game time `T = t_s · 16 · 0.9501953125`.

### 8.1 `move` — moving platform (legacy: `moveSine_act`)

```ts
type MoveBehavior = { type: 'move' } & (
  | { mode: 'sine';  x?: Osc; y?: Osc; z?: Osc }          // z = depth, visual only
  | { mode: 'linear'; points: Vec2[]; speed: number; pingPong?: boolean; pause?: number; ease?: 'linear'|'smooth'; phase?: number }
  | { mode: 'path';  path: string; speed?: number; duration?: number; loop?: 'loop'|'pingpong'|'once'; phase?: number; pause?: number } );
interface Osc { amplitude: number; period: number /* s */; phase?: number /* cycles 0..1 */ }
```

* `sine`: `offset_axis = amplitude · sin(2π (t_s / period + phase))`, axes independent (Lissajous). With equal periods this is **bit-identical to the existing LevelData motion** (`MoveDef`), so migrated levels behave exactly as before.
* `linear`: waypoints are *offsets* from the entity origin; constant speed (m/s), optional dwell `pause` at the ends, `smooth` easing = smoothstep per leg.
* `path`: follows `paths[id]` (`polyline`, `bezier`, `spline`); position = arc-length parametrisation (precomputed table, 64 samples per segment); `loop: once` stops at the end. **The offset is relative to the path's first point** (the entity's own `position` is the rest pose, so a path that starts at its object's position moves the object along the path).
* Collision follows the offset exactly (the core already derives the platform carry from `offset(t) − offset(t−1)`).
* Chunk bounds use the **swept AABB** of the motion (computed analytically per mode).

### 8.2 `rotate` — rotating object (legacy: `map3Wheel_act`)

```ts
interface RotateBehavior { type: 'rotate'; mode: 'continuous' | 'sine' | 'free';
  speed?: number;      // deg/s (continuous), also the sine angular speed factor
  base?: number;       // deg, centre angle (sine) / start angle
  amplitude?: number;  // deg (sine)
  period?: number;     // s (sine)
  phase?: number;      // cycles
  damping?: number;    // 1/s (free): ω ← ω·exp(−damping·t)
  initialSpeed?: number;
  pivot?: Vec2;        // local pivot (default origin)
  axis?: 'z' | 'x' | 'y';   // z = in the gameplay plane (collision rotates); x/y = visual-only tumble
}
```

`continuous: angle = base + speed·t_s`; `sine: angle = base + amplitude·sin(2π(t_s/period + phase))`; `free: angle = base + (initialSpeed/damping)·(1 − exp(−damping·t_s))` (closed form of the legacy decaying flywheel, deterministic). Collision polygons are re-posed each tick (only for entities with a rotating collider; a few per chunk). **Rotating colliders do not transfer tangential velocity to a rider** (the core carries translation only) → the validator warns if a rotating collider is `safe`.

### 8.3 `toggle` — counter-gated block (legacy: `toggleBlock_act`)

```ts
interface ToggleBehavior { type: 'toggle'; channel: 'jumps' | 'boosts' | 'checkpoints' | 'flag'; flag?: string;
  modulus?: number /*2*/; active: number[];   // solid when (counter mod modulus) ∈ active
  inactive?: { collision?: false; visual?: 'ghost' | 'hidden' | 'visible' };
  tint?: { active?: string; inactive?: string }; }
```

Red/blue blocks of the legacy template = two entities with `active:[0]` and `active:[1]` on the `jumps` channel. `modulus: 3` gives three alternating groups. Counter source: `PogoState.jumps / boosts`, runtime `checkpointIndex`, or runtime flags. Evaluated in the pre-step hook for the *next* tick, so a launch flips the group on the following tick.

### 8.4 `timed` — clock-gated block (legacy: `toggleSine_act`)

`{ type:'timed', period, phase?, duty?: 0.5, inactive?, warn?: seconds }` → `on = ((t_s/period + phase) mod 1) < duty`. (Legacy `sin > 0` equals `duty = 0.5`.) `warn` makes the visual blink for the last `warn` seconds before switching off.

### 8.5 `breakable` — hit-gated object

`{ type:'breakable', trigger:'land'|'touch', delay?: 0.4, respawn?: 3 }`: first `land` event on the collider (event carries `collider` index) arms a timer; after `delay` the collider deactivates; after `respawn` seconds (if > 0) it re-arms. Timer state is part of `MapRuntime.snapshot()`.

### 8.6 Others

* `conditional`: `{ type:'conditional', when: Condition, inactive? }` — active while the condition holds.
* `boostZone`: `{ type:'boostZone', kind:'powerJump', once?: boolean }` → emits `boost_zone` command; **not applied** (§19).
* `squash` / `poi`: presentation events (`squash`, `poi`) for the renderer; never affect collision.

### 8.7 Conditions

`Condition = { flag: string } | { counter: 'jumps'|'boosts'|'deaths'|'checkpoints', mod?: number, in: number[] } | { checkpoint: string, reached: boolean } | { all: Condition[] } | { any: Condition[] } | { not: Condition }`. Flags are set by region effects (`setFlag`) — this is how **secret areas, conditional objects and optional routes** are built (`reveal` by tag is sugar for a flag on tagged entities).

### 8.8 Region and effect system

```ts
interface RegionDef { id: string; type: 'kill'|'checkpoint'|'finish'|'teleport'|'camera'|'vfx'|'audio'|'lighting'|'fog'|'ambient'|'hint'|'water'|'secret'|'trigger'|'hazard';
  shape: RegionShape;  // box {x,y,w,h} | circle {x,y,r} | polygon {points}
  params?: Record<string, unknown>; enter?: Effect[]; exit?: Effect[]; stay?: Effect[]; once?: boolean; enabled?: boolean; tags?: string[]; priority?: number }
type Effect = { op:'kill' } | { op:'teleport'; to: Vec2 | {spawn:true} } | { op:'setCheckpoint'; id: string }
  | { op:'setFlag'; name: string; value: boolean|number } | { op:'incCounter'; name: string; by?: number }
  | { op:'camera'; zoom?: number; offsetX?: number; offsetY?: number; lockY?: number|null; duration?: number }
  | { op:'vfx'; id: string; burst?: number } | { op:'audio'; id: string; volume?: number }
  | { op:'lighting'; ambient?: number; sun?: number; color?: string } | { op:'fog'; density?: number; color?: string }
  | { op:'hint'; textKey: string } | { op:'reveal'; tag: string } | { op:'emit'; event: string; data?: unknown };
```

Zones of type `kill`/`hazard` replace the legacy `kill` region; `checkpoint`/`finish` regions are generated from `checkpoints[]`/`finish` (the typed lists are authoritative; a hand-written region of those types is a validator error). Enter/exit detection uses the player position after each step (point-in-shape), `once` zones disable themselves.

---

## 9. Collision system

### 9.1 Visual mesh / collision / trigger / background / decoration

| Concept | Where | Participates in physics |
|---|---|---|
| Visual mesh | `entity.visual` | no |
| Collision shape | `entity.collision` | yes (solid or hazard or goal) |
| Trigger volume | `regions[]`, `collision.trigger` | no collision; events/effects only |
| Background mesh | `type:"background"` + `visual.layer` | no |
| Decoration | `type:"decor"` | no |

### 9.2 Shapes

```ts
type ShapeDef =
  | { kind:'box';     w: number; h: number; taper?: number; anchor?: 'center'|'topCenter'; offset?: Vec2 }
  | { kind:'sphere';  r: number; segments?: number /*16*/; offset?: Vec2 }
  | { kind:'capsule'; r: number; length: number; axis?: 'x'|'y'; segments?: number /*6 per cap*/; offset?: Vec2 }
  | { kind:'convex';  points: Vec2[] }                       // CCW; auto-fixed if CW, error if non-convex
  | { kind:'mesh';    outline?: Vec2[]; polygons?: Vec2[][] } // concave outline → convex pieces (ear clip + Hertel–Mehlhorn merge)
  | { kind:'slope';   w: number; h: number; mirror?: boolean };  // right-triangle wedge, rising to the right unless mirrored
```

`box.taper` (bottom width / top width, default 1) and `anchor:'topCenter'` reproduce the existing platform silhouette. Sphere/capsule become regular polygons (the core collides convex polygons), so their visual should be a *finer* mesh than the collider (by design).

```ts
interface CollisionDef { shape: ShapeDef; surface?: 'normal'|'slippery'|'bounce'|'sticky'|'boost'; material?: string;
  hazard?: boolean; trigger?: boolean; oneWay?: 'up'|'down'|'left'|'right'; safe?: boolean; enabled?: boolean }
```

### 9.3 Surfaces and what physics does with them (locked spec)

| `surface` | Compiled to | Physics today |
|---|---|---|
| `normal` | solid | ordinary solid |
| `slippery` | solid with `slippery` surface | **E15 slide entry** (the only surface rule in the locked spec) |
| `bounce`, `sticky`, `boost` | solid with that tag | treated as `normal` (spec has no such rule); validator **WARNING** *capability unavailable* |
| `hazard: true` | hazard trigger | respawn on touch (core) |
| `trigger: true` | trigger collider | emits `zone` events only |
| `oneWay` | solid, **gated by the map layer** | see §9.4 |

Slide acceleration/damping/stop threshold are **not** map parameters (fixed by E15).

### 9.4 One-way surfaces

The core has no one-way concept. The map layer provides it **without touching the core**: a one-way collider is *active* for a tick iff the player's tip is on the allowed side of the surface at the previous tick (`above` its top edge for `up`) with a 0.15 m hysteresis; when inactive the player passes through; activation is part of the pre-step hook, deterministic, and cannot flip while the player overlaps the collider.

### 9.5 Collision budgets and rules

* Max **vertices per convex piece 16**; mesh outlines are decomposed to ≤ 16-gon convex pieces; ids `entityId#0…`.
* Polygons must be CCW, area ≥ 1e-4 m², no NaN. Overlap between *static solids* is a WARNING above 0.05 m depth (existing project rule), gaps between nearly-touching solids smaller than 0.03 m are a WARNING (leak).
* Active-collider budget in §18.

---

## 10. Chunk system

### 10.1 Layout

`chunks: { mode:'auto-grid'|'explicit', cell:{w,h}, defs: ChunkDef[], activateRadius, loadRadius, unloadRadius, lodDistances[], maxActive }`; `ChunkDef = { id, bounds:{minX,maxX,minY,maxY}, tags?, pinned?: boolean }`. Naming: `c_<ix>_<iy>` (auto-grid) or `Chunk_00…` (explicit, usually cut along the progress path). An entity belongs to the chunk containing its **swept AABB centre**; its chunk `extent` is the union of member swept AABBs (so a wide platform keeps its neighbours loaded). Each chunk holds the **entities** assigned to it: visual, collision, behaviours, VFX/audio emitters, background props, trigger objects. **Regions, checkpoints, finish zones and the progress route are *resident core data*** (small, independent of map size; they live in `core.json` of a package and are never streamed) — only entities are chunked.

### 10.2 States and streaming policy

`UNLOADED → LOADED (parsed, colliders created inactive, instance batches built) → ACTIVE (colliders in the solid set, behaviours evaluated) → VISIBLE (inside the camera rect, submitted to the renderer)`; unload when farther than `unloadRadius` (hysteresis `> loadRadius`) and not **pinned**.
Distance = distance from the player (and optionally the camera) to the chunk `extent` rectangle. Defaults: `activateRadius 40 m`, `loadRadius 64 m`, `unloadRadius 96 m`.
**Physics safety invariant:** `activateRadius ≥ vMax·lookahead + margin` where `vMax = 0.7335 m/tick` (300 Q/T · Δt), `lookahead 30 ticks` ⇒ 22 m + margin; the validator enforces ≥ 32 m. Respawn anchors (the chunk containing `safeGround`, the last checkpoint chunk, spawn chunk) are **pinned**.
Collider slots are **stable**: each chunk owns fixed collider indices (a tombstone collider occupies the slot while unloaded), so `groundId`/`safeGround` references never dangle and the `colliders` array does not grow with load/unload cycles.

### 10.3 Culling, LOD, instancing

* **Visibility culling:** chunks and entities intersecting `cameraRect + margin` are *visible*; others are skipped by the renderer.
* **LOD:** per entity, `lod[]` ascending by distance from the camera; the manager returns the selected level index each frame (hysteresis 10 %).
* **Instancing:** on chunk load, entities sharing `(mesh, material)` are grouped into `InstanceBatch { key, mesh, material, count, transforms }`; one draw call per batch. Budgets count batches, not entities.

### 10.4 Not loading the whole world

The runtime reads a `ChunkSource`: `listChunks()`, `readChunk(id)` — a `MapDocument` (in-memory, authoring), or a `MapPackage` reader (each chunk file parsed **lazily on first use**, bytes kept compressed-free but un-parsed). Peak memory ∝ active chunks, not the map.

---

## 11. Progress system

```ts
interface ProgressDef { routes: RouteDef[]; window?: { backPercent: number /*15*/; forwardPercent: number /*30*/; relocateDistance: number /*12 m*/ } }
interface RouteDef { id: string; kind: 'main' | 'optional' | 'branch' | 'secret';
  points: { x: number; y: number; percent?: number; name?: string }[];
  from?: { route: string; point: number }; to?: { route: string; point: number } }   // branches/optionals attach to the main route
```

* **Percent = 0…100** by *anchors*: the first main point is 0, the last is 100, any point may pin an explicit `percent`; others are interpolated by path length between anchors. A branch maps onto the percent interval between its `from` and `to` points proportional to its own length.
* **Projection** is tied to the actual route, not to a nearest-point guess: candidates are segments whose percent interval intersects `[current − back, current + forward]` (window), best = smallest lateral distance (ties prefer forward); if the best distance exceeds `relocateDistance` (e.g. after a teleport/respawn) a global search resets the window. `progress.current` is the projected percent; `progress.max` is the highest reached this run; a **checkpoint can pin a minimum percent** (`CheckpointDef.progress`).
* Looping routes and branches no longer snap to a far segment because the window follows the player along the route.
* Output: `{ percent, max, route, distanceAlong, lateral }`. HUD reads `percent`.

### 11.1 Speedrun statistics (`RunRecorder`)

Per run: `timeSec` (first launch → finish, from `PogoState.startedTick/finishedTick`), `jumps`, `deaths` (hazards + falls + kill zones), `splits[]` (`{id, tick, timeSec, segmentSec, deltaToPB, deltaToPar}`), `completed`. Personal best = best completed run (and best per split); **medals** from `splits.targets {gold, silver, bronze}` (seconds); `par` = gold by default. Persistence through `RunStore` (JSON-serialisable, `version` field, per-map key `manifest.id@manifest.version`).

## 12. Checkpoint system

```ts
interface CheckpointDef { id: string; order: number; name?: string; region: RegionShape; respawn: Vec2; progress?: number;
  optional?: boolean; requires?: string[]; orderMode?: 'monotonic' | 'strict' | 'any' }
```

* IDs are explicit (`cp0…`), **no single-digit limit**; `checkpointCount` in the manifest must equal `checkpoints.length` (validator).
* Activation: first step on which the player position is inside `region` **and** the order rule passes: `monotonic` (default, legacy-compatible: `order > last`), `strict` (`order == last+1`, otherwise `skipped` event), `any` (optional side checkpoints).
* Effects: record split time; compare with PB/par (`checkpoint` event carries `deltaToPB`); set the **respawn anchor** (§0.2) to the nearest ground below `respawn` (`world.findGround`); raise `progress.max` to `CheckpointDef.progress`.
* `respawn` must be on/above a solid (validator).

## 13. Theme system

```ts
interface ThemeDef { id: string; name: string; base?: 'autumn_hills'|'snow_peaks'|'ancient_ruins'|'volcanic_depths'; // existing renderer themes
  palette: { primary, secondary, accent, background: string };
  sky: { top, mid, horizon, sun: string; sunDir: [number,number,number]; sunIntensity: number };
  fog: { color: string; density: number };
  lighting: { hemiSky, hemiGround: string; hemiIntensity: number; exposure: number };
  slots: { ground, secondary, water, lava?: string };           // material ids the "@slot" references resolve to
  vegetation: { density: number; kinds: string[] };
  vfx: { ambient: string[]; intensity: number };
  audio: { ambient: string; wind: number; birds: number; water: number };
  backgroundProps: string[];
  weather?: { type: 'none'|'rain'|'snow'|'ash'|'leaves'; intensity: number; windX?: number };
  dayNight?: { cycleSec: number; keyframes: { t: number /*0..1*/; sky?: Partial<Sky>; fog?: Partial<Fog>; sunIntensity?: number; hemiIntensity?: number }[] };
  overrides?: Record<string, unknown> }   // deep-merged over `base` WorldTheme
```

* A theme is **independent of the map layout**: change `theme.ref`/inline theme and every `@slot` material, fog, lighting, sky, background props and ambience change without touching entities.
* `toWorldTheme(def)` merges `overrides` over the base `WorldTheme` of `src/data/worlds.ts`, so the existing renderer accepts V2 themes unchanged.
* `sampleTheme(def, t_s)` interpolates day/night keyframes (linear in sRGB, closed loop); weather and ambient zones are driven by `regions` (`lighting`, `fog`, `ambient`, `audio`, `vfx` types) which *override* the sampled values while the player is inside (priority, then declaration order; blended over 0.5 s by the host).
* Four built-in themes mirror the four existing worlds (ids `autumn_hills`, `snow_peaks`, `ancient_ruins`, `volcanic_depths`).

### 13.1 Materials

`MaterialDef { id, shader: 'stylized-lit'|'unlit'|'palette'|'emissive'|'water'|'ice'|'foliage', color, palette?: assetId, albedo?: assetId, normal?: assetId, roughness?, emissive?, uvScale?, wobble?: {amplitude, speed}, fallback?: materialId }` — **parameter sets of built-in shaders only** (no shader source in packages). `palette` shader = the legacy lesson: colour by UV into a ≤ 64×64 palette texture (3 KB) so a theme swap = palette swap.

## 14. Validation (`MapValidator`)

`validateMap(doc, opts) → ValidationReport { ok, counts:{error,warning,info}, issues[] }`, `Issue = { severity:'ERROR'|'WARNING'|'INFO', code, message, path, entityId?, hint? }`. `ok` ⇔ no ERROR. Checks (code prefix):

| Group | Checks |
|---|---|
| Structure | `STRUCT_*` schema/type errors, duplicate ids, unknown `type`, bad numbers |
| Spawn/finish | `SPAWN_MISSING`, `SPAWN_NOT_ON_GROUND` (needs a solid within 0.3 m; the core's `createPogoState` itself accepts up to 0.6 m, the validator is deliberately stricter), `FINISH_MISSING`, `FINISH_UNREACHABLE` (deep) |
| Collision | `COLLISION_INVALID` (non-convex / zero area / NaN / > 16 vertices), `COLLISION_HOLE` (gaps < 0.03 m between touching solids), `COLLISION_OVERLAP` (> 0.05 m), `COLLISION_OUT_OF_BOUNDS` |
| Bounds | `OBJECT_OUT_OF_BOUNDS` (WARNING for gameplay entities, INFO for decoration) |
| References | `PREFAB_MISSING`, `PREFAB_CYCLE`, `PREFAB_EXPR`, `PARAM_TYPE`, `PARAM_RANGE`, `THEME_MISSING`, `MATERIAL_MISSING`, `TEXTURE_MISSING`, `MODEL_MISSING`, `AUDIO_MISSING`, `PATH_MISSING`, `ASSET_UNUSED` (info) |
| Behaviour | `BEHAVIOR_INVALID` (period ≤ 0, empty path, bad modulus…), `BEHAVIOR_CONFLICT` (two motions), `BEHAVIOR_UNSUPPORTED` (modes), `CAPABILITY_UNAVAILABLE` (bounce/boost/one-way notes) |
| Progress | `PROGRESS_INVALID` (< 2 points, zero length, NaN, no main route), `PROGRESS_ENDPOINTS` (start far from spawn / end far from finish), `PROGRESS_ANCHORS` (non-monotonic percents) |
| Checkpoints | `CHECKPOINT_ORDER` (duplicate/gap/non-increasing progress), `CHECKPOINT_REGION`, `CHECKPOINT_COUNT`, `CHECKPOINT_RESPAWN` |
| Chunks | `CHUNK_OVERLAP`, `CHUNK_UNASSIGNED` (gameplay entities only), `CHUNK_RADIUS` (activation < safe radius), `CHUNK_TOO_MANY` (a package would exceed 4 096 files — e.g. a 90 000-entity map at the default cell size needs 5 697) |
| Budgets | `BUDGET_DRAWCALLS`, `BUDGET_TRIANGLES`, `BUDGET_TEXTURE_MEMORY`, `BUDGET_VFX`, `BUDGET_AUDIO`, `BUDGET_COLLIDERS`, `BUDGET_ENTITIES` — computed for the **worst window of `maxActive` adjacent chunks** against a preset (`android-mid` default, `android-low`) |
| Reachability (opt-in `deep`) | brute-force (angle × load) reachability using the real physics on the static collision subset (`analyzeLevel`), conservative landing window; reports `FINISH_UNREACHABLE` / `PLATFORM_UNREACHABLE` / `HOP_UNREACHABLE` / `HOP_TIGHT` (info) |

Validation runs: on editor **Validate**, on **Build**, and on **load** of any package (errors block loading, warnings do not).

## 15. Editor / authoring (`MapEditor` + CLI)

Headless, deterministic, scriptable — it is the engine under any UI and under `tools/map-cli.ts`.

| Operation | API |
|---|---|
| Create / open / save | `MapEditor.create(opts)`, `MapEditor.open(doc|json)`, `serialize()` |
| Place object | `place(prefabId, position, overrides?) → id` |
| Move / rotate / scale | `move(ids, dx, dy)`, `rotate(ids, deg, pivot?)`, `scale(ids, sx, sy)` |
| Duplicate / delete | `duplicate(ids, dx, dy)`, `remove(ids)` |
| Collision / material / behaviour / trigger | `setCollision(id, def)`, `setMaterial(id, mat)`, `setBehavior(id, def|null)`, `addRegion(def)` |
| Progress / checkpoints / start / finish | `setProgressRoute(route)`, `setCheckpoint(def)`, `setSpawn(pos)`, `setFinish(zones)` |
| Preview | `preview({ticks, input})` → compile + run the real physics, returns `{finished, ticks, deaths, progress, events}`; `previewBot()` runs the route bot when a `route` is declared |
| Validate / build / export | `validate(opts)`, `build() → MapPackage`, `exportJson()`, `exportPackage()` |
| History | every mutation is a command with an inverse → `undo()`, `redo()`, `history` (bounded 500) |

All properties are data: **no code is edited to make a moving platform** — `place('moving_platform', …)` or `setBehavior(id, {type:'move', …})`.
CLI (`npm run map -- <cmd>`): `validate <file>`, `build <file> -o out.pogomap`, `inspect <file|pkg>`, `migrate <levelId|file>` (LevelData → map.json), `import-legacy <file.$$M>`, `chunks <file>` (report).
A visual editor UI is a *client* of this API and is **out of scope for this task** (recorded in §19).

## 16. Packaging

`map_manifest.json` (= `manifest` + package block) and a **map package** `*.pogomap` (single file, little-endian):

```
magic "PGMP" · u16 containerVersion(1) · u16 flags(0) · u32 indexLength · u32 indexCrc32 · index JSON · payload
index = { files: [{ path, offset, length, crc32, mime }] }       (files sorted by path ⇒ deterministic build)
files: map_manifest.json ({ manifest, package:{containerVersion, files, bytes, chunks, createdAt, contentCrc32} })
       core.json (the whole document except entities: manifest, world, theme, spawn, finish, checkpoints, progress,
                  regions, paths, prefabs, materials, assets, chunking …) · chunks/index.json (chunk infos) ·
       chunks/chunk_<id>.json ({ id, entities }) · declared assets by their own `path` (meshes .glb/.gltf/.bin,
       textures .png/.jpg/.webp, audio .ogg/.mp3/.wav, materials/collision .json) · metadata/description.<lang>.txt
```

* `manifest` fields: `id, name, author, description, version(semver), thumbnail, difficulty, estimatedTimeSec, theme, mapSize, checkpointCount, tags, requirements{minFormatVersion, capabilities[], physics}`; the package adds `{ files, bytes, chunks, createdAt }`. Capability list is computed from the map (so the host can refuse maps that need unavailable features).
* **Data-only**: allowed extensions `.json .png .jpg .jpeg .webp .glb .gltf .ogg .mp3 .wav .txt .bin`; anything executable or shader-like (`.js .mjs .html .exe .dll .so .fx .glsl .hlsl .c .py .sh .bat`) is **rejected on read and on build**. Paths are normalised, no `..`, no absolute paths, ≤ 128 chars, ≤ 4 096 files, ≤ 128 MiB total.
* Every file has CRC32 (verified on each read); the index has its own CRC32 in the header and `contentCrc32` in the manifest covers every `(path, crc)` pair; a mismatch is a load error. Deterministic build: files sorted by path, stable JSON, fixed `createdAt` unless supplied.
* Description replaces `levelDescription.txt` (UTF-8, localised: `metadata.localized.<lang>`); the thumbnail replaces `workshopPreview.png` and is an ordinary declared asset (`manifest.thumbnail` = asset id; guideline 16:9, ≤ 1024 px, ≤ 150 KB).

## 17. Visual System V2 (workflow, not a renderer rewrite)

`Gameplay geometry` (collision, authored first, drives solvability) **+** `high-quality visual mesh` (rounded stylised glTF, separate from collision) **+** `materials` (theme slots, palette shader, optional normal map on hero props only) **+** `lighting` (hemispheric + sun, soft ambient occlusion baked into vertex colours, optional blob/PCF shadow by quality tier) **+** `background` (≥ 3 parallax layers, depth fog, haze) **+** `atmosphere` (fog, weather, day/night) **+** `VFX` (zone/entity emitters, budgeted). Guidance: clean silhouettes, ≤ 2 materials per prop, ≤ 128² textures except hero props (≤ 512²), instancing for repeats, LOD for anything > 1 500 tris. This task delivers the **data, budgets, chunk/LOD/instancing outputs and compile path to the existing renderer**; new mesh builders are the next step (§19).

## 18. Android performance

Design budgets (DESIGN class — to be measured on device):

| Budget | `android-mid` | `android-low` |
|---|---|---|
| Frame target | 60 FPS (16.6 ms), simulation 120 Hz fixed | 60 / 30 fallback |
| Draw calls (visible) | ≤ 150 | ≤ 90 |
| Triangles (visible) | ≤ 250 000 | ≤ 120 000 |
| Texture memory (resident) | ≤ 96 MB | ≤ 48 MB |
| Active chunks | ≤ 9 | ≤ 6 |
| Active colliders | ≤ 600 | ≤ 300 |
| Active entities (behaviours ticking) | ≤ 2 500 | ≤ 1 200 |
| VFX particles | ≤ 600 | ≤ 300 |
| Concurrent audio voices | ≤ 16 | ≤ 8 |

Techniques: chunk streaming with hysteresis · frustum/chunk culling · instancing + batching by `(mesh, material)` · LOD with hysteresis · palette textures (KBs) · analytic behaviours (no per-object physics bodies) · zero per-tick allocation in hot paths (collider list rebuilt only when the active set changes) · lazy chunk parsing · web-only stack (no desktop engine), same code path as the shipped Android WebView.

## 19. Migration strategy

### 19.1 From `LevelData` (current levels)
`levelDataToMap(level)` converts platforms/moving/special/obstacles/hazards/goal/progress/landmarks/hints into a `MapDocument` **preserving order and ids**. Acceptance test: `compileMap(levelDataToMap(LEVEL_01))` yields a world whose colliders are geometrically identical to `new PhysicsWorld(LEVEL_01)` and the recorded route script finishes with the **same final state** (bit-identical trace). Levels may then be re-authored natively in V2.

### 19.2 From the legacy kit (`.$$M` text export — developer tool)
`importLegacyWmp(text)` → `MapDocument` (geometry/behaviour/zone data only; **models, textures, shaders are not converted**): blocks → convex collision (XZ convex hull per brush, Y discarded), `//start`/`spawn_act` → spawn, `reg_finish` → finish, `CP_n` → checkpoints, `kill` → kill zone, `path_progress` → main route, `splitSetup.txt` names → splits, entity `action` + skills + flags → behaviours (`moveSine_act`→`move/sine`, `map3Wheel_act`→`rotate`, `toggleBlock_act`→`toggle`, `toggleSine_act`→`timed`, slippery FLAG7 → `slippery`, COL entities → collision-only, PASSABLE entities → visual-only placeholders). Units: 1 unit = 1 Q ⇒ metres = units / 52 (INFER, medium confidence); legacy (x, y=depth, z=up) → V2 (x, y=z/52). Unknown/unsupported actions become `tags` + INFO issues. Tests use synthetic text samples, not the original maps.

### 19.3 Format versioning
`formatVersion` integer; `migrateMap(doc)` runs a chain `n → n+1` (v1 = pre-release draft is recognised and upgraded: `platforms[]` → `entities[]`); unknown fields are preserved in `extensions`; `manifest.requirements.minFormatVersion` lets old hosts refuse new maps.

### 19.4 Rollout
Phase A (this task): data model, runtime, validator, editor API, package, importer, tests; integration via compile to `LevelData`+`MapWorld` (existing renderer). Phase B: renderer builders for `visual.mesh`/materials/instancing/LOD, camera & audio/VFX zone consumers, in-game map browser. Phase C: visual editor UI client, workshop-style sharing.

### 19.4a As built — integration with the game

* `Game.loadMap(doc, themeOverride?)` builds a `MapRuntime` (`MapWorld` + `PogoPhysicsController` on the same core config) and swaps the running world for it (packages are opened with `PackageReader` and handed to `createMapRuntime`/`MapRuntime` as a `ChunkSource`); per fixed tick the loop calls `mapRuntime.beforeStep(state)` (stream chunks, evaluate behaviours for tick+1, flush colliders) → `PogoPhysicsController.step()` (unchanged) → `mapRuntime.afterStep(state, simEvents)` (breakables, progress, checkpoints, regions, zones, finish). `reset()` calls `resetRun()`.
* `?map=<id>` dev entry in `App.ts` plays a built-in map (`builtinMaps.ts`: `first_steps_v2` — LEVEL_01 migrated through `levelDataToMap`, the same collider set (ids, geometry, kinds, surfaces, order) and bit-identical replay of the recorded route) with a `devMap` flag that skips campaign progression; the progress bar reads `mapRuntime.progress.max / 100`.
* `npm run map -- validate|build|inspect|chunks|migrate|import-legacy …` (`tools/map-cli.ts`).
* Shell: the existing renderer receives a best-effort `LevelData` (`compileRenderLevel`) for the expressible subset; V2-only visuals (meshes, materials, instancing batches, LOD) are *outputs of the runtime* (`MapRuntime.visualState`, `buildBatches`, `lodLevel`) that the renderer builders of Phase B will consume.
* Design decisions taken during implementation: finish zones are **box colliders centred on `position`**, installed as resident goal triggers; kill/teleport use the core's `respawn()` and then re-sync the presentation state; teleport targets force-load and activate their chunks first (`ensureActiveAround`); `ChunkManager` has an infinite-radius *full-load* mode used for small maps and for the bit-identical comparison against streaming.

### 19.5 Integration conflicts / capabilities not applied (recorded, no physics change made)

| # | Conflict | Decision |
|---|---|---|
| I1 | `boostZone` would write `PogoState.boost (p)`; the locked spec sets p only by E13 | declared, emitted as command `boost_zone`, **not applied**; WARNING `CAPABILITY_UNAVAILABLE(boostSurface)` |
| I2 | Entity bounce push (legacy E01/E02) is excluded by the locked spec (§8) | `bounce` surface = `normal` in physics; WARNING `CAPABILITY_UNAVAILABLE(bouncePush)` |
| I3 | Rotating colliders do not carry riders (core carries translation only) | rotators are obstacles/hazards; `safe:true` on a rotator = WARNING |
| I4 | One-way surfaces not in the core | implemented in the map layer (§9.4) |
| I5 | Map mode switches (`doubleJump`, `puzzle`, `grapple`) change player rules | rejected: `WorldSettings.modes` must be all `false` (ERROR otherwise) |
| I6 | Teleport/kill/respawn-anchor need to move the player | done through the core's own `respawn()` and the DESIGN-class safe-point fields only |
| I7 | `LevelData` cannot express arbitrary hazards/convex pieces for the existing renderer | `MapWorld` owns *all* colliders; the renderer receives a best-effort `LevelData` for the expressible subset (platform/obstacle/hazard/goal) |
