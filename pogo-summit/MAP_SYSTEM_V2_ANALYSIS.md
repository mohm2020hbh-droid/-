# MAP_SYSTEM_V2_ANALYSIS.md

Engineering study of `CustomMaps.zip` (the legacy custom-map kit) as input for **Map System V2** of Pogo Summit.

> **What this document is.** An evidence-based description of how the legacy map pipeline works — architecture, behaviour, workflow, data structures.
> **What it is not.** A copy. No legacy code, model, texture, shader source or map layout is reproduced here or shipped in this repository. Numbers quoted from the files (counts, sizes, field layouts, default values written in the kit's own annotations) are facts about a *format*; behaviour is described in our own words/equations.
> **Out of scope / untouched:** Physics Core, physics constants, `Pogostuck_Physics_LOCKED_SPEC.md`.
> The archive contains one editor licence-key file (`wed2016B.key`). It was **not opened** for content and is not described beyond its existence.

Evidence labels used throughout:

| Label | Meaning |
|---|---|
| **FILE** | read directly from a file in the archive (text, header, parsed binary) |
| **CODE** | decoded from the compiled game program the kit was made for (the supplied `Pogostuck.exe`, already lifted during the earlier physics study) — behaviour only, as equations/rules |
| **INFER** | conclusion from ≥ 2 independent observations; confidence stated |
| **UNKNOWN** | not determinable from the supplied files |

---

## 1. Complete archive audit (Phase 1)

### 1.1 Totals

174 files, 41 037 166 bytes (39.1 MiB), two example maps plus the editor kit.

| Folder | What it is | Files |
|---|---|---|
| `README.txt` | top-level instructions (how to duplicate `BaseMap`, install editor config, build, reload in game with F1, teleport with T) | 1 |
| `BaseMap/` | a deliberately messy **template map** ("to be used as a base for new levels") — 8.7 MiB | 76 (incl. `Models/` 24 MDL + textures, `Textures/`) |
| `Spearstruck/` | a **complete real map** (ported from the author's earlier game-jam project) — 6.5 MiB | 75 |
| `.WED Files, not a map/` | the **authoring-tool kit**: editor config, hotkeys, screenshots with advice, Blender exporter, Blender sources — 24.0 MiB | 22 incl. 2 subfolders |

### 1.2 Classification of every file

Classes requested in the brief. Counts are computed by a script over the extracted archive (not by file name only: binary headers were parsed — `MDL7`, `WMPB`, `WMB7`, `WAD7`, TGA/PCX/PNG headers, text formats).

| Class | Files | Size | Contents / examples |
|---|---:|---:|---|
| **MAP SOURCE** | 11 | 482 KiB | `customMap.wmp` (binary `WMPB` — what the editor saves), `customMap.$$M` (**text export, format "wmpio 1.30 / version 711"**), `customMap.bak` (editor backup; text in `BaseMap`, binary copy of the `.wmp` in `Spearstruck`), `customMap.wed` (editor *view state* only), `customMap - Kopie.*` (a copy of the map made by the author) |
| **MAP BUILD** | 12 | 1.78 MiB | `customMap.wmb` (compiled level, binary `WMB7`), `customMap.$$w` (temporary texture archive `WAD7` used by the compiler), `customMap.raw`/`palette.pcx` (256-colour palette, 768 B / 15×19 image), `customMap.wdl` (3-line launcher script), `acklog.txt`/`ackerr.txt` (engine logs of a failed compile) |
| **MAP GEOMETRY** | – | – | *Not separate files.* Block geometry lives **inside** `customMap.wmp/.$$M/.wmb`. Pretty geometry is in `MODELS`/`BACKGROUND` |
| **COLLISION** | 12 | 599 KiB | `mapCOL0…7.mdl`, `mapCol_combo.mdl`, `appA_COL_slime3.mdl` (×2), `appA_COL_Middle.mdl`, plus the **block geometry** itself (collision when no COL model is used) |
| **MODELS** | 42 (+8 `.bak`) | 3.9 MiB (+0.8) | gameplay props and level geometry: `geo*.mdl`, `moveBlock*.mdl`, `m3Thorn*.mdl`, `mushroomFly*.mdl`, `dungeonWheel1.mdl`, `pencil.mdl`, `candle.mdl`, `cmBlade.mdl`, … The `.bak` files are model-editor backups (byte-identical in size to the `.mdl`) |
| **TEXTURES** | 26 | 4.8 MiB | 512² albedo/normal pairs (`CrystalOre_512_*`), `broBlock(_n)`, `metalshutter1(_n)`, 16×16 `pixelBricks`, `flyAgaric*`, … |
| **MATERIALS** | 3 palette/gradient textures (+ materials declared in `customMap.c`) | 774 KiB | `colorPalette.tga` **32×32 (3 KB)**, `gradientTest.tga` 512², `car.tga` 32×32 — colour is chosen by UV into a tiny palette |
| **SHADERS** | 4 | 7.5 KiB | `appAGeoDefault.fx`, `customMaterial1.fx`, `shadowIncl.fx` (×2 identical) |
| **SCRIPTS** | 2 | 10.6 KiB | `customMap.c` (×2): **action/material declarations — not the behaviour** (see §3) |
| **BEHAVIORS** | – | – | Declared by `customMap.c` action names + entity `skill`/`flag` fields in the map; **implemented inside the game program**, not in the kit |
| **REGIONS** | – | – | In the map file (`//region`), 4 in `BaseMap`, 6 in `Spearstruck` |
| **PATHS** | – | – | In the map file (`//path`), 2 in `BaseMap`, 1 in `Spearstruck` |
| **SPAWNS** | 4 markers | 3.8 KiB | `spawn.mdl` (24 tris), `startFinishM.mdl` (8 tris), `startingCurve.mdl`; plus `//start` entity (Spearstruck) and `spawn_act` entity |
| **FINISH** | (textures) 3 | 60 KiB | `startFinish.tga` (32×256 checker), `finish.tga`; finish = region `reg_finish` + `finishLine` entity |
| **SPLITS** | 2 | 0.1 KiB | `splitSetup.txt` (one split name per line) |
| **BACKGROUND** | 14 | 2.1 MiB | `BGeo*.mdl`, `*BG.mdl`, `mountain.mdl`, `cloud1/2.mdl`, `treesBG`, `leavesBG` (64 bones), `backgroundPlane` |
| **VFX** | 4 | 1.1 MiB | `particles.tga` (leaf/shape atlas 128²), `particlesFGquad.tga` (512² flame/glow), `particlesFG.mdl`, `particlesFGquad.mdl` |
| **AUDIO** | **0** | – | *The kit contains no audio at all* (ambient audio is chosen by the game, not the map) |
| **WORKSHOP/METADATA** | 4 | 220 KiB | `levelDescription.txt` (UTF-16 LE), `workshopPreview.png` (512×287 / 512×288) |
| **EDITOR CONFIG** | 4 | 25 KiB | `WED.CFG`, `MED.CFG` (binary editor preferences, contain absolute developer paths), `default.wad` (6 KiB, 2 textures: `#default`, `black`), `wed2016B.key` (licence — not read) |
| **BLENDER PIPELINE** | 4 | 22 MiB | `operator_mdl_export.py` (Blender→MDL exporter), two `.blend` sources (Blender 3.3 and 2.78), exporter "defaults" note |
| **DOCUMENTATION** | 15 | 362 KiB | `README.txt`, `readme.txt` (model licence), `workshopPreview advice.txt`, `med_tasten.txt` (model-editor hotkey list), 9 annotated screenshots (editor views, compiler options, "set textures to flat", "put regions at right y", "splits save location", "levelDescription encoding", preferences) |

### 1.3 Deep inspection of the five requested places

**`BaseMap/`** — a *template*, intentionally messy: 93 block brushes, 48 model entities, 4 regions, 2 paths, 1 sun. It demonstrates **every behaviour once** (mushrooms, wheels, moving blocks, toggle blocks, timed blocks, slippery ice, boost slime, thorn that follows a path, background clouds, normal-mapped sphere, custom shader material, etc.). Contains deliberate defects useful as a test of tooling: `cmCannon.bak` has **no matching `.mdl`**; 11 of its 30 model files are never referenced by the map; `acklog.txt`/`ackerr.txt` record a failed stock-engine compile (`'_type' is not a member of 'ENTITY'` — the map script uses a field that only exists in the game's own extended build), proving the script is only valid inside the host program.

**`Spearstruck/`** — a finished map. 60 model entities, 6 regions, 1 progress path of **32 points** (27 286 units long in XZ), 4 split names, 18 candles, 8 collision proxies (+ 1 slime collider), ~40 decoration/background models, 1 water plane. Its `customMap.c` is an **older revision** of the template script (it still contains a commented-out sine-mover snippet — the only place the author left behaviour in clear text, see §3.3). It references actions that do not exist in the host program (`geoModelSetup`) and materials only the host defines — i.e. *dangling references are tolerated at load time* (a weakness; V2 validates them).

**`.WED Files, not a map/`** — editor kit. The screenshots are the most information-dense documentation: (a) compiler dialog (Build Simple Map, *Create Meshes*, *Don't snap vertices*, *Merge across leaves*, *Use tessellation*, lightmap options), (b) "set textures and blocks to *flat*" (collision blocks must use texture type *Flat* with default texture), (c) "put regions and objects at right Y position" (regions are drawn in the **Top (XY)** view at specific depth), (d) `splits.txt` save location under the user's profile `…/CustomMaps/<MapName>/splits.txt`, (e) `levelDescription.txt` must be saved **UTF-16 LE**.

**`Blender 3.3 MDL Exporter/`** — `operator_mdl_export.py` (≈ 28 KB). FILE-verified behaviour: exports *selection or all* objects as **one MDL7 group**, triangulates, merges UV-split vertices, writes up to 4 bone weights per vertex and a bone tree (no animation), writes **texture file names** (not pixels), scale multiplier default **1000**, and **re-centres each model on a 32-unit grid with Y (depth) forced to 0** so instances snap in the editor. Limits: bone name ≤ 31 chars, no animation, no vertex colours in the output (read but unused).

**`Drakula's Castle Source/`** — Blender sources of a large map (v3.3 "A28" with modifiers applied; v2.78 "A19" before decimate). Object names reveal the **authoring conventions**: `COL_*` objects = collision meshes (`COL_Middle`, `COL_ICE`, `COL_TreeBig`…), `*BG`/`*FG` suffix = background/foreground layers (`BigTreeBG`, `BigTreeFG`, `GeoCellarBG`), plus 98 objects / 29 materials (`WEDGeoMat`, `BackgroundMat`, `WaterMat`, `BrickMat`) and modifiers (Edge Split, Decimate, Bevel, Subsurf, Mirror, Array, Weld). Textures: `colorPalette`, `gradientTest`, `appAFogX4`, `map3TwigsNM`, `monoPines`. **Not** part of any playable map; "Only to be used for Pogostuck custom maps" — therefore we study the *workflow* and do not use the assets.

### 1.4 Licensing / originality notes (from the kit itself)

* `Models/readme.txt`: model files and textures "shall not be used for any other purpose or distributed in any other way than for the purpose of sharing a Pogostuck custom map". → **We do not import, convert or ship any model/texture/map of the kit.** V2 is a new format with its own assets; the only legacy-touching code is an optional **importer tool** that reads a *user's own* legacy text export and emits geometry/behaviour data (never models/textures).
* `README.txt` warns about downloading untrusted maps (the legacy format can carry executable shader/script files) → V2 packages are **data-only** (see SPEC §17).

---

## 2. Reconstruction of the legacy pipeline (Phase 2)

```
 ┌────────── authoring ──────────┐   ┌────────── compile ─────────┐   ┌────────────── runtime (game) ──────────────┐
 Blender 3.x ─(operator_mdl_export.py)→ *.mdl  ┐
 model editor (MED) ───────────────→  *.mdl    ├──────────────────────────────────────────────┐
                                                │                                              ▼
 WED (editor) ──save──► customMap.wmp ──(Map Compiler: "Build Simple Map")──► customMap.wmb ──load──► level (blocks)
    │  ▲                     │  text export                                      ▲                      │
    │  └── customMap.wed     └──► customMap.$$M  (+ $$w texture archive,          │ names refer to       ▼
    │      (view state)           palette.pcx/.raw)                               │ files/materials   entity creation:
    ▼                                                                             │                   model + action(name)
 customMap.c (action/material                                                     │                   + skill1..20 + flags
 declarations + editor annotations) ── compiled by the engine at start ───────────┘                   + material name
                                                                                                       │
                    splitSetup.txt / levelDescription.txt / workshopPreview.png ──(read by the menu)──►│
                                                                                                       ▼
                                                           per-frame logic: behaviours (action functions), regions
                                                           (kill / CP_n / reg_finish), path_progress, splits, finish
```

### 2.1 Stage by stage

| Stage | File(s) | Function | Evidence |
|---|---|---|---|
| **WED** | `.wmp`, `.wed`, `WED.CFG`, `default.wad` | Level editor. 4 synchronised views: **Top (XY), Back (XZ), Side (YZ), 3D**. The *Back (XZ)* view is the gameplay plane. Grid 16 (`Grid Lo = 16`), snapping on, rotate-snap 5°. Holds blocks, entities, regions, paths, sun | FILE (screenshots, `.wed`) |
| **WMP** | `customMap.wmp` | **Editable source.** Binary container, magic `WMPB`, header `01 04 05 00 50 00 00 00 c6 02 00 00`, a free-text "file created: dd.mm.yyyy" stamp, then object records | FILE (header parsed) |
| **`$$M` text** | `customMap.$$M` (+ `.bak` in BaseMap) | **Human-readable twin of the WMP**: `// wmpio (1.30), dd.mm.yyyy`, `version 711`, brace-structured. This is the only form we can *read* reliably and is the basis of the importer | FILE (fully parsed for both maps) |
| **Compile** | WED "Map Compiler" dialog → `customMap.wmb`, `$$w` | Builds a *Simple Map* (no BSP/PVS needed: *Create PVS* is off), bakes block meshes, packs used textures into a `WAD7` archive (`$$w`, 660 KB in BaseMap) | FILE |
| **WMB** | `customMap.wmb` | **Compiled.** Binary `WMB7`: 784-byte header with a lump directory (offset,length pairs: textures, geometry, entity list, …). Embeds the texture archive (BaseMap: 659 572 of 974 548 bytes = **67.7 % is textures**). Entity records store *strings*: model file name, entity name, action name, material name | FILE (header, strings) |
| **WDL** | `customMap.wdl` | 3 lines: `path "models";` and `string level_str = <customMap.wmb>;` — the level-name hook the game loads | FILE |
| **Runtime load** | game | Menu reads `splitSetup.txt` (line count → checkpoint count, **clamped 0…9**), `levelDescription.txt` (≤ 7 998 bytes, UTF-16/UTF-8 detection), `workshopPreview.png` (shown 490×200); then loads the WMB; **F1 reloads the level on the fly** | CODE |
| **`customMap.c`** | `customMap.c` | Compiled by the engine at start. Declares the *action names* a map may use and the *materials*; its `//skillN: name default` / `//flagN: name default` comments are **editor annotations** that label the fields in WED's entity dialog. The function bodies are empty stubs (`return;`) — the real behaviour is in the game program (§3) | FILE + CODE |
| **Model/material loading** | `*.mdl`, `*.tga`, `*.fx` | Entities name a model; the engine resolves it in `path "models"` (README: *"Do NOT place your own models in the Models subfolder of your map, otherwise the engine won't find them on level load"* — a path-resolution wart). Materials are **looked up by name** in a table owned by the game (plus up to five author shaders `customMaterial1..5` bound to `customMaterialN.fx`, hot-reloaded in dev) | FILE + CODE |
| **Gameplay** | game | Per-frame: behaviours, region tests, path projection, split recording, finish | CODE |

### 2.2 Source of truth / editable / compiled

| Question | Answer |
|---|---|
| **Source of truth** | `customMap.wmp` (binary). `customMap.$$M` is its text export; if both exist they describe the same data. Models, textures, shaders, `customMap.c`, `splitSetup.txt`, `levelDescription.txt` are *separate sources* owned by the author |
| **Editable** | `.wmp` (WED), `.mdl` (Blender/MED), `.tga`, `.fx`, `.c`, `.txt` |
| **Compiled / derived (never edit)** | `.wmb`, `.$$w`, `.$$M`, `.raw`, `.bak`, `acklog.txt`, `ackerr.txt` |
| **Editor-only state** | `.wed`, `*.CFG`, `Kopie` copies |

Weaknesses: *two* near-identical truths (`.wmp` and `.$$M`), build artefacts shipped next to sources, no checksum/version relation between source and compiled output, no schema.

### 2.3 How each concept is represented (FILE, parsed from `$$M`)

**Block geometry.** `{ //level  <int>  customMap.$$w  palette.pcx  { // N  vertices V  faces F  <V×xyz>  <F face lines>  { 0000 ndef N } } … }`.
Each brush is a convex polyhedron: 85 of 93 `BaseMap` brushes are 8-vertex hexahedra, 7 are 6-vertex wedges, 1 has 80 vertices. Face line: `n  i0 i1 … : <texture> ( u-axis·offset ) ( v-axis·offset ) : <flagBits> <angle> <albedo> <material>`. In `BaseMap`, 485 of 665 faces use the `#default` texture of type *Flat* (= pure collision), 120 `broBlock` (sketch bricks), 36 a normal-mapped shutter texture, 12 `pixelBricks`, 12 `black`. In `Spearstruck` only **4 "black" brushes** remain (a masking placeholder) — all real collision comes from COL models.

**Entities.** `{ //model <code> x y z  pan tilt roll  sx sy sz  <file.mdl> <name> <action> <skill1..skill20 | skill1..8> <32-char flag string> <tail…> }`
* `code 7` = full entity (20 skills, `ambient`, `albedo`, two further numeric fields, then a **material name**); `code 3` = light entity with 8 skills and no material (used for COL proxies, markers, candles). FILE.
* Tail of a code-7 entity: `ambient (−100…100, e.g. 100 for the start line, −30 for background) · albedo (0…100, default 50) · field3 · field4 · material`. `field3/field4` take values {0,2} in the samples — **UNKNOWN** meaning.
* Other top-level records: `{ //sun 5 0 60 }` (sun direction values), `{ //start 1 x y z  pan tilt roll  name }` (player start; Spearstruck), `{ //region 8 min(xyz) max(xyz) name }`, `{ //path 6 name N flags  N×xyz  N×(6 numbers)  edges }`.

**Model instances.** An entity *is* the instance: it carries file + transform + action + parameters. Instancing is by file name — **no prefab concept**: a mushroom placed 5 times is 5 independent entity records repeating the same fields (BaseMap: 8 near-identical toggle blocks).

**Materials.** (1) per-face *texture* names on blocks, (2) per-entity *material name* (resolved by the game's material table; 36 names declared in `customMap.c`, e.g. `appAGeoDefault_mat`, `toggleBlock_mat`, `iceSnow_mat`, `cmNormalmapping`, `cmUnlit`, `cmPixelated`, `customMaterial1..5`), (3) per-entity `ambient`/`albedo`. Skills 1–8 are copied into the shader (`vecSkill41…48`) by `skillset_act` — **skills double as shader parameters**.

**Regions.** `{ //region 8 / x y z (min) / x y z (max) / name }` — axis-aligned boxes **identified by name convention**: `kill`, `reg_finish`, `CP_<digit>` (checkpoint index). Samples: `kill` slabs under the map, `CP_n` slabs 128 units thick centred at **y = 320**, `reg_finish` ±64 around y = 0 (CODE: the game queries named regions at different probe depths — `CP_` at y 320, kill/finish at y 0 — which is why the "put regions at the right Y position" screenshot exists). **Regions are an implicit, name-coded protocol.**

**Paths.** `{ //path 6 <name> <N> <flags> <N points> <N lines of 6 numbers> <N−1 edge lines "i j length 0 0 0"> }`. Edge length is the pre-computed Euclidean distance (verified: 951.4284 for (−3680,960)→(−2976,320)). The six per-point numbers are all 0 in the samples (tangents/bezier handles — **UNKNOWN**, unused). Flags observed: 28, 8, 158 — **UNKNOWN** semantics. Y component is engine noise (|y| < 0.002): paths are effectively 2D in (X,Z). Two paths in `BaseMap`: **`path_progress`** (6 pts, 9 622 u) and **`thornsTargetPath`** (2 pts, 280 u; the name suggests a thorn travels between the two points — the entity→path binding is not visible in the `$$M` entity records, **UNKNOWN**).

**Spawn.** Two mechanisms: `//start` entity (`pos_000`, Spearstruck) and a `spawn.mdl` entity with `spawn_act` named `spawn1` (BaseMap and Spearstruck). The *player* is created at an `initial spawn position`, and **a map without a `path_progress` path prints "No progress path found! Add one"** (CODE).

**Finish.** Name-coded: region `reg_finish` (touching it ends the run) + visual entities named `startLine`/`finishLine` using `startingCurve.mdl`/`startFinishM.mdl` (CODE: the game evaluates whether the player is within **235 units** (XZ) of the `startLine` entity — used by the run-start/reset logic; the exact use was not fully decoded).

**Object behaviour link.** Entity → `action` *name* (string) → function in the game program. The action runs once per entity (a loop with `wait(1)` — i.e. each behaviour is a coroutine that owns the entity). Parameters arrive through `skill1…20` (floats) and a **32-bit flag word**. Flag word in the text file is a 32-char binary string where **char *c* ↔ bit 31−c**; FLAG*n* of the editor = bit *n*−1 (verified on `toggleBlock_act`: `group_2` = FLAG4 = bit 3 matches the entities named `…_blue`). Engine bits seen: bit 8 (`256`) = **INVISIBLE**, bit 9 (`512`) = **PASSABLE**.

**Skills ↔ behaviour.** The `//skillN: name default` annotations (kit) name the parameters; the game reads them positionally (§3).

**Flag-word decode — validated against every entity of both maps (FILE).** `FLAGn = bit n−1`; the 32-char string is MSB first.

| Flag (bit) | Seen on | Meaning confirmed by |
|---|---|---|
| FLAG1 (0) | `pinkSapCol*`, `pinkSap201`, `ice`, `boostJuice`, `startLine`, `finishLine` | marks slime/goo volumes and start/finish markers (semantics inside the player controller — UNKNOWN beyond "goo") |
| FLAG3 (2) | wheel with continuous spin, `toggleSine069` (`turn_background`), `waterPlane`, `modeSetup` (`double jump`) | matches the annotations `continuousRotation` / `turn_background` / `double jump` |
| FLAG4 (3) | 5 mushrooms (`deformable id`), 5 blue toggle blocks (`group_2`), `blade*` (`sine_Rotation`) | matches annotations; the red toggle blocks have it clear |
| FLAG5 (4) | `toggleSine` | `turn_invisible` |
| FLAG6/7/8 (5/6/7) | `pencilPOI` (6 = `scary`), `pencilPOI023` (7+8 = `mega`+1), **`ice_COL` (7 = value 64 = slippery)** | `scary`/`mega` annotations; slippery per CSV P60 |
| bit 8 (256) INVISIBLE | all collision proxies, `startLine`, `modeSetup` | toggle code sets/clears 256 for `turn_invisible` |
| bit 9 (512) PASSABLE | all visual-only entities, markers | toggle code sets/clears 512 for solid/non-solid |
| bit 17 (131072), bit 26 (67108864) | set by every action on its entity | engine bits (shadow/polygon-collision class) — names not needed |

**Visual ≠ collision, in practice.** The kit already separates them *by entity flags*: a **visual model** is `PASSABLE` (bit 9 set; no collision) while a **collision model** is `INVISIBLE` and solid (bit 8 set, bit 9 clear). Measured in `Spearstruck`: 44 passable visual entities (**72 134 triangles**) vs 9 invisible collision entities (**5 444 triangles**) — a 13 : 1 ratio; `BaseMap`: 31 102 vs 956.

---

## 3. Behaviour system (Phase 3)

### 3.1 The key finding: `customMap.c` contains no behaviour

Both `customMap.c` files are **declarations only**: 14 action stubs (`return;`) with editor annotations, plus a material table (`flags = 0;`). The sole executable line (`my._type = TYPE_BOOSTJUICE;`) fails to compile in the stock engine (see `acklog.txt`) — proof the *real* implementations live in the game executable. To obtain actual semantics we therefore used two sources: the kit's annotations (**FILE**: parameter names and defaults) and the lifted game program (**CODE**: the equations below, described in our words).

Common mechanics of every action (CODE): the action function is started per entity; it tags the entity with a *type id* and a *response class* (two integers the player physics reads on contact), forces fixed collision/shadow bits, remembers the **initial position** (`skill 66..68`), and then loops once per frame. Time base is the **global run clock** × 15.2 — INFER: `T = runSeconds × 16 × 0.95`, the same game-time unit as the locked physics spec (1 s = 15.2 T). Behaviours are therefore **pure functions of the run clock** (hence replay/ghost friendly) rather than integrated state.

### 3.2 Behaviour catalogue

Legend: ★ = parameter is declared in the kit's annotation with that default. "V2" = how it becomes data in Map System V2.

#### MovingPlatform — `moveSine_act` (BaseMap: 5 instances)

Parameters (★ names/defaults, CODE semantics):

| skill | name | default | meaning |
|---|---|---|---|
| 1 | `x_dist` | 0 | amplitude along X |
| 2 | `z_dist` | 0 | amplitude along Z (vertical in the gameplay plane) |
| 3 | `x_speedfac` | 5 | angular speed (degrees per T) of the X oscillation |
| 4 | `z_speedfac` | 5 | angular speed of the Z oscillation |
| 5 | `x_AngOffset` | 0 | phase (degrees) X |
| 6 | `z_AngOffset` | 0 | phase Z |
| 7 | `y_dist` | 0 | amplitude along Y (depth — visual only) |
| 8 | `y_speedfac` | 5 | depth speed |
| 9 | `y_AngOffset` | 0 | depth phase |
| 11 | `y_ambientEffect` | 0.25 | brightness change driven by the depth swing |
| flag3 | `y_changesAmbient` | 0 | enable the above |

Rule (CODE): `pos_axis(t) = origin_axis + sin(t·speedfac_axis + offset_axis) · dist_axis` with `t = runClock·15.2`; each axis independent (⇒ Lissajous paths). If the object is within 128 units of the camera (or flagged) the object is **moved with collision (`c_move`)** so the player standing on it is carried; far from the camera it is *teleported* to the analytic position (cheap). Observed samples: `x_dist` 128/192, `z_dist` 128–256, speeds 1…8, phases 90/180.
Spearstruck's older annotation, in the author's own commented-out code: `x = x0 + sin(time·skill3 + skill5)·skill1` (same idea; confirms the formula).
**V2 mapping:** `behavior.type = "move"`, `mode: "sine"`, per-axis `{amplitude, speed, phase}`, speed expressed in °/T *and* convertible to period seconds; adds `path` (polyline/bezier/spline), `linear` ping-pong, `once`, `loop`.

#### RotatingObject — `map3Wheel_act` (BaseMap: 4 instances: 2 wheels, 2 blades)

| skill/flag | name | default | meaning (CODE) |
|---|---|---|---|
| skill1 | `rotateSpeed` | 1.5 (also applied when 0) | angular speed factor |
| skill2 | `sine_base` | 0 | centre angle for sine mode |
| skill3 | `sine_amount` | 45 | swing amplitude (degrees) |
| flag3 | `continuousRotation` | 0 | `angle = (t·speed) mod 360` — constant spin |
| flag4 | `sine_Rotation` | 0 | `angle = base + amount·sin(t·speed)` — oscillation |
| neither | – | – | **free spin**: angular velocity `ω` is kept, decays by **1.5 % per step** (`ω ← ω − 0.015·ω·Δ`) and `angle += ω·Δ` — i.e. a damped flywheel that can be kicked |

The rotation axis is the *tilt* axis — about the depth axis, i.e. rotation **in the gameplay plane**. On a run restart the angle/velocity are reset to the authored values. Blades (`cmBlade`, `pencil`) reuse the action → *rotating hazard*, selected by model + material, not by a different behaviour.
**V2 mapping:** `behavior.type = "rotate"`, `mode: "continuous" | "sine" | "free"`, `speed`, `base`, `amplitude`, `phase`, `damping`, `pivot`, `axis: "z"` (gameplay) / `"x"|"y"` (visual only).

#### ToggleBlock — `toggleBlock_act` (BaseMap: 8 instances, "red"/"blue")

| skill/flag | name | default | meaning (CODE) |
|---|---|---|---|
| skill1 | `redBlueTintFac` | 1 | tint strength (→ shader) |
| skill2 | `dither_scale` | 1 | dither pattern scale (→ shader) |
| skill3 | `dither_fac` | 0.65 | dither strength (→ shader) |
| flag3 | `boost_toggle` | 0 | counter used = *boost count*, else *jump count* |
| flag4 | `group_2` | 0 | this block belongs to group 2 (blue); else group 1 (red) |
| flag5 | `turn_invisible` | 0 | hide the block while inactive |

Rule (CODE): `solid = ((counter mod 2) == group)`, where `counter` = the player's **jump count** (or boost count when `boost_toggle`). Solid ⇒ collision on (`PASSABLE` cleared), visible; not solid ⇒ `PASSABLE` set (and `INVISIBLE` if `turn_invisible`). Shader state: `vecSkill41 = 1` for group 2, `vecSkill42 = solid`, `vecSkill43 = tintFac`. ⇒ **every jump flips which colour is solid** — a puzzle mechanic that couples level geometry to a player-counter.
**V2 mapping:** `behavior.type = "toggle"`, `channel: "jumps" | "boosts" | "flag:<name>" | "checkpoint"`, `modulus: 2`, `activeValues: [0]`, `inactive: { collision: false, visible: "ghost"|"hidden" }`, `materialState`. Generalises to N groups.

#### TimedObject — `toggleSine_act` (BaseMap: 2 instances)

| skill/flag | name | default | meaning (CODE) |
|---|---|---|---|
| skill1 | `speedFac` | 5 | angular speed (°/T) |
| skill2 | `offset` | 0 | phase (°) |
| skill3 | `ambient_shift` | −35 | brightness shift while *off* |
| flag3 | `turn_background` | 0 | move to the background layer while off |
| flag5 | `turn_invisible` | 0 | hide while off |

Rule: `on = sin(t·speedFac + offset) > 0`. On ⇒ solid + normal brightness; off ⇒ passable + `ambient += ambient_shift` (+ optional hide / push to background). ⇒ **blocks that blink on a clock**, deterministic.
**V2 mapping:** `behavior.type = "timed"`, `period`/`speed`, `phase`, `duty` (fraction on; legacy = 0.5), `inactive` state.

#### SlipperySurface — `pinkSap_act`, `coconutSlippery`, `slime_act`; ice model `appA_slime3` + `appA_COL_slime3` (BaseMap: `pinkSap`, `ice`)

FILE + CODE: whether a surface is slippery is an **author flag on the collision entity** — the ice collider `ice_COL` carries **FLAG7 (value 64)** in the map file and has *no action*; the locked-spec table (CSV row P60) states the landing test is `entity.flags & 64`. The `pinkSap_act`/`coconutSlippery` stubs only tag the entity (type id 36, response class 1032, FLAG8 bit) for the player controller. The *physics* of sliding is in the player controller; the locked spec (E15) already contains it: entry at landing (`s_x ← v_x`), slope target 48 Q/T, gain 0.25, clamp 1.35, decay 0.5/T, stop 0.25. **None of these numbers are map parameters in the legacy system, and they must not become map parameters in V2** (they are fixed physics). The visual pair `ice` (visible, `iceSnow_mat`) + `ice_COL` (invisible solid, FLAG7) shows the Visual≠Collision pattern for a *surface kind*.
**V2 mapping:** `collision.surface = "slippery"` (only). `slideAcceleration/slideDamping/stopThreshold` are **not exposed** (documented decision; see SPEC §9).

#### Boost — `boostjuice_act` (BaseMap: 1 instance `boostJuice` + `pinkSapCol037` flipped 180°)

CODE: tags the entity as `TYPE_BOOSTJUICE` (type id 43) — a gold slime volume. The *effect* (it feeds the player's boost state; the toggle blocks also count `playerNumberOfBoosts`) is in the player controller. In the locked physics the only boost concept is **p (power-jump flag, E13)** set by spinning > 285°; a *map object setting p* is **not specified** by the locked spec.
**V2 mapping:** `behavior.type = "boostZone"` with `kind: "powerJump"` (declares intent) — **emitted as a command; not applied to Physics State** (integration conflict recorded in SPEC §19 / report). Impulse-style boosts (force/duration/direction) are *not* in the legacy kit, not in the locked spec → not offered.

#### Mushroom / bounce entity — `mushroom_act` (BaseMap: 5; flags `bounce_light`, `FLAG4` = deformable)

CODE: type id 10, response class 36, up to **32 deformable mushrooms** per level (counter `mushroomID < 32`), material swap per game level, deformation through a bone-driven squash model (`mushroomFly*.mdl` has 3 bones). The *push* the player receives is an entity-bounce rule (CSV rows E01 `BOUNCE_ENTITY_PUSH 30`, E02 `…VZ_CAP 170`) that the **locked spec explicitly excludes** (§8: entity bounce pads).
**V2 mapping:** prefab `BouncePlatform` = visual squash behaviour (`behavior.type = "squash"`, visual only) + surface `bounce` which Physics treats as `normal` today. Flagged by the validator as *capability `bouncePush` unavailable*.

#### Hazard — `monolithThorn_act` (BaseMap: 3, Spearstruck: 4), region `kill`

CODE: thorn = type id 33, response class 1064 (hazard class — INFER from the name and the `kill` pairing), collision solid. A 2-point path named `thornsTargetPath` exists in `BaseMap` (INFER: a thorn travels along it; the binding is UNKNOWN) → V2 `move` over a named path covers this case. Region `kill` = full-width slab(s) below the level (CODE: the player dies when its position is inside region `kill`).
**V2 mapping:** `collision.hazard = true` (core already supports hazard colliders) and zone type `kill`.

#### POI — `POI_act`, flags `scary`, `mega` (pencils)

CODE: response class 32 (`|8192` if `scary`, `|2097152` if `mega`); skills 1–4 copied to shader (`vecsk41..44`). A **point-of-interest / easter-egg object** the character reacts to (face/pose). Not geometry-affecting.
**V2 mapping:** `behavior.type = "poi"` → emits a `poi` event with `{style}`; purely presentational.

#### Spawn / start / finish markers — `spawn_act`, `skillset_act`, `customMapSetup_act`

* `spawn_act` (code-3 entity): marks the player's initial position (flags INVISIBLE|PASSABLE).
* `skillset_act` on `startLine`/`finishLine`: copies skills to the shader; the entity doubles as the geometry reference for the run-start proximity test (235 units).
* `customMapSetup_act`: **map-wide switches** — `flag3 double jump` → enables a double-jump mode, `flag4 puzzle` → puzzle mode. Both change *player* rules (physics-adjacent) → **not supported in V2** (reserved in `WorldSettings.modes`, validator ERROR if enabled).

#### Background object — `bgObject_act` (clouds, mountains)

| skill/flag | name | default | meaning (CODE) |
|---|---|---|---|
| skill1 | `fac_x` | 1 | parallax factor X |
| skill2 | `fac_z` | 1 | parallax factor Z |
| skill3 | `greyScale` | 0 | shader parameter (greyscale amount) |
| skill4 | *(not annotated)* | 0 | selects the material family: 1 ⇒ palm-leaf material, any other non-zero ⇒ tree-deco material |
| skill5 | `skill43` | 0 | shader parameter |
| flag6 | `bottom_z` | 0 | anchor the object by its lowest point |
| flag7 | `fixed_x` | 0 | do not scroll horizontally |
| flag8 | `fixed_z` | 0 | do not scroll vertically |

Limit (CODE): **at most 64 background objects** are tracked per level (`backgroundObjectCount < 64`). Background entities are `PASSABLE`, ambient −30…+20 (darker, hazier), placed at large Y (depth 400–3000).
**V2 mapping:** `entity.type = "background"` with `visual.layer` (`z`/parallax), `parallax {x,y}`, `anchor`, `fixed`; instanced, no cap of 64.

#### Water — `water_act` (Spearstruck)

CODE: computes the water surface height from the model's bounding box, swaps to a water/refraction material; the game has *swim physics only for specific levels* (CSV W01–W04: excluded from locked spec).
**V2 mapping:** visual-only `waterPlane` prefab + `region` of type `water` (visual/audio; no swim physics).

#### Candle — `candleSetup` (Spearstruck: 18)

CODE: sets a candle material + a second additive "light" material layer, alpha/albedo 100 → **emissive decoration**. **V2:** `decor` prefab with `visual.emissive` + optional point-light in `lighting`.

#### Region protocol (CODE) and progress

| Region name | Meaning |
|---|---|
| `kill` | inside ⇒ the player dies/respawns |
| `reg_finish` | inside ⇒ run finish (`playerRunFinish`) |
| `CP_<d>` | checkpoint *d* (single digit ⇒ **max 10; effectively ≤ 9** by `splitSetup` clamp). Triggers only when `d > lastCheckpoint` (forward-only, monotonic) |
| `PP_*`, `skipless_*` | route/skip-detection regions used by the game's own maps; not part of the custom-map kit |

On a checkpoint: `checkpointTime[d] = runClock`; compared with the **personal-best split `PB[d]`** and shown as `−m:ss:mmm` (faster, green) or `+m:ss:mmm` (slower, blue) for 48 ticks (≈ 3 s). Split names come from `splitSetup.txt` (one per line, same order as `CP_0…`). Split times are saved to `splits.txt` in the user profile folder of the map.

#### Progress — `path_progress` (CODE, `pathProgressGet`, `hpf_WED_path_project`)

`progress% = 100 · distAlongPath(project(player.xz)) / pathLength`. `project` walks every segment, takes the **closest point** (clamped segment parameter, Y ignored), and returns the distance accumulated along the path up to that segment plus the offset. A variant with a *separation* argument exists to avoid snapping to a far segment of a path that doubles back. Weaknesses visible in `Spearstruck` (a 32-point path that loops around itself): nearest-point projection can jump between loops; progress cannot express branches; **progress is a geometric approximation, not tied to the route actually taken** — V2 fixes this (windowed monotonic projection, branch routes, explicit percent anchors).

### 3.3 Summary table — legacy behaviour → V2 behaviour type

| Legacy action | Instances (Base/Spear) | V2 `behavior.type` |
|---|---|---|
| `moveSine_act` | 5 / 0 | `move` (`sine`, `linear`, `path`, `bezier`, `spline`) |
| `map3Wheel_act` | 4 / 0 | `rotate` (`continuous`, `sine`, `free`) |
| `toggleBlock_act` | 8 / 0 | `toggle` |
| `toggleSine_act` | 2 / 0 | `timed` |
| `pinkSap_act`, `slime_act`, `coconutSlippery` | 1 / 1 | *collision surface* `slippery` |
| `boostjuice_act` | 1 / 0 | `boostZone` (declared, not applied) |
| `mushroom_act` | 5 / 0 | `squash` (visual) + surface `bounce` (declared) |
| `monolithThorn_act` | 3 / 4 | collision `hazard` (+ `move` over a path) |
| `POI_act` | 2 / 0 | `poi` (event) |
| `bgObject_act` | 2 / 2 | entity `background` (parallax) |
| `water_act` | 0 / 1 | prefab `waterPlane` + zone `water` |
| `candleSetup` | 0 / 18 | prefab `candle` (emissive) |
| `spawn_act`/`//start` | 1 / 2 | `spawn` |
| `skillset_act` (+`startLine`/`finishLine`) | 2 / 1 | `finish` + `startLine` marker |
| `customMapSetup_act` | 1 / 0 | `WorldSettings.modes` (unsupported) |
| regions `kill`/`CP_n`/`reg_finish` | 1+2 / 1+4+1 | typed `regions` (+ `checkpoints`, `finish`) |
| path `path_progress` | 1 / 1 | `progress.routes` |

---

## 4. Collision vs visual geometry (Phase 4)

### 4.1 What the kit contains (FILE, MDL7 parsed)

| Mesh class | Files (examples) | Triangles | Characteristics |
|---|---|---|---|
| **VISUAL MESH** | `geoCenter` 4 977, `geoMidLeft` 12 648, `geoMogul` 6 709, `appA_bricksMiddle` 3 784 | 0.5 – 13 k per piece | textured by **palette UV** (`colorPalette.tga` 32×32) or gradient atlas; vertex normals; some skinned (mushrooms 3 bones) |
| **COLLISION MESH** | `mapCOL0…7` (146 – 1 606 tris), `appA_COL_Middle` (740), `appA_COL_slime3` (72) | 72 – 1.6 k | **no skin/texture**, `tris ≈ verts` (faceted, unshared), **extruded slab of depth 407 units** along Y; each is a *separate entity* so the engine collides per piece |
| **COMBINED COLLISION** | `mapCol_combo.mdl` — 8 groups, 5 356 tris | – | merge of the 8 pieces; present in the map as a *passable+invisible* entity (editor/reference only) |
| **TRIGGER VOLUME** | `//region` boxes | – | AABB, name-coded, no mesh |
| **BACKGROUND MESH** | `BGeo*`, `mountain`, `cloud*`, `treesBG`, `leavesBG` (64 bones), `backgroundPlane` (2 tris) | 2 – 7 k | `PASSABLE`, dark ambient, large Y offset, `bgObject_act` parallax |
| **DECORATION** | `candle` (204), `plumsFG` (2 208), `particlesFG` (840) | – | foreground/emissive |
| **BLOCK BRUSHES** | WMP convex polyhedra | – | collision *and* sketch visuals (README: export blocks to MDL once happy) |

Dimensions: all gameplay lies in the **X–Z plane**; Y is the 407-unit-thick depth slab (BaseMap block Y range −1 280…1 024 includes oversized sentinels).

### 4.2 How Visual ≠ Collision is realised (and its limits)

* Two entities per object: *visual* (PASSABLE, textured, detailed) + *collision* (INVISIBLE, solid, simple) — see the ice/pink-sap pair (`ice` 848 tris + `ice_COL` 72 tris).
* **Limits:** pairing is by *convention only* (names `COL`, `_COL_`) and manual placement; no link between the two entities (moving one does not move the other — moving platforms must therefore be a **single** entity that is both visible and solid, which is why `moveBlock*.mdl` are small, cheap meshes); collision types are limited to *entity polygons* (arbitrary triangle soup): no primitive shapes, no one-way, no per-surface kind other than the entity's response class; the `COL` mesh must be a **closed convex-ish slab** or the engine collides unreliably; large concave COL meshes are expensive (1.6 k tris per piece in Spearstruck).

### 4.3 V2 requirements derived

1. **Visual mesh ≠ collision shape** for every object, linked inside one entity (`visual` + `collision` blocks) so they move/rotate/toggle together.
2. Collision primitives: **Box, Sphere, Capsule, Convex, Mesh (decomposed to convex), Slope, One-way, Trigger, Hazard, Slippery, Boost** — with `surface` kind as data.
3. Collision is **2D-polygon based** in the gameplay plane (what the locked-physics collision stand-in consumes), depth is presentation.
4. Triangle-budget accounting separate for visual / collision / background.

---

## 5. Visual, material and shader findings (inputs for Phase 12/13)

*Concepts only; no shader source is reproduced.*

* **Palette texturing.** Most models are coloured by **UV lookup into a 32×32 palette** (3 KB) — zero texture memory, no UV unwrapping per object, and a whole theme can be recoloured by swapping the palette ⇒ **Theme = palette swap**. Gradient lookup (`gradientTest` 512²) provides smooth sky/fruit colours.
* **Shading model** of the default geometry shader: fixed sun direction; `diffuse = 0.6 + 0.4·max(N·L,0)`; a Blinn-style specular term (power 32, weight 0.25); a **normal-driven highlight lookup** (matcap-like: `lookup = N·0.25 + 0.5` into a highlight texture); a slight normal tint of the red/green channels; an **albedo parameter that cross-fades towards the greyscale highlight** (the level designer's "grey-scale" slider); a **soft light that follows the player** (radius ≈ 470 units, quadratic falloff, 0.5 weight, direction-aware) — gives the stylised glow around the character; global `ambient+1` multiplier; **3×3 PCF shadow map** (dark = 0.65). Custom materials show **vertex displacement by time** (`sin(time·0.25 + pos·0.175)·7`) for wobbling decor.
* Textures: 512² albedo+normal pairs only on hero props; everything else ≤ 128². Texture payload of `BaseMap` is dominated by four 512² TGAs (3.1 MB uncompressed) and the `$$w`/WMB-embedded archive.
* **Background**: 64-object cap, parallax factors per axis, anchors (`bottom_z`, `fixed_x/z`), darker ambient per layer (−30…+20) = *cheap depth layering*.
* **VFX**: particle atlas + two quads; foreground leaf/ember particles are *map content* (`particlesFG.mdl` 840 tris).
* **Audio**: none in the kit (ambient/music hard-wired to game levels).

---

## 6. Packaging / Workshop findings (Phase 14 input)

| Item | Legacy | Weakness |
|---|---|---|
| Identity | folder name (e.g. `BaseMap`) | no id, no version, no author field (author is free text inside the description) |
| Description | `levelDescription.txt`, **UTF-16 LE**, ≤ 7 998 bytes | fragile encoding, no language/localisation |
| Thumbnail | `workshopPreview.png` ≤ 150 KB, shown at 490×200, up to 1024² | no explicit aspect rule |
| Splits | `splitSetup.txt` (names only) → `CP_0…CP_8` | names coupled to region *names*; no par times, no medals |
| Records | `splits.txt` in the user profile | per-user, unversioned |
| Dependencies | none declared; models/materials by *name* | missing assets discovered at load; assets of other maps can leak in |
| Safety | README: *"virus check the zip"*; shader `.fx` + `.c` can ship | executable content in a data package |
| Size | `BaseMap` 6.7 MB, `Spearstruck` 7.6 MB; WMB embeds textures | no compression, duplicates between maps (17 identical-file groups found, e.g. `startingCurve.mdl`, `colorPalette.tga` in both maps) |

---

## 7. What to keep, what to drop

### 7.1 Ideas worth keeping (carried into V2)

1. **Behaviours as pure functions of a global clock** (replay-safe, no integration drift) — keep and make explicit.
2. **Parameter annotations with defaults** (the `//skillN: name default` idea) → typed, self-describing **prefab parameter schemas**.
3. **Visual vs collision split by entity role** → one entity with `visual` and `collision` blocks.
4. **Name-coded gameplay roles** (`kill`, `CP_n`, `reg_finish`) → **typed** regions/checkpoints instead of name conventions.
5. **Progress path + checkpoint splits + PB comparison** (−/+ delta display) → keep and extend (par times, medals, segment times).
6. **Palette texturing** (tiny textures, theme = palette) → Theme system.
7. **Parallax background layers with anchors** → instanced background layers without the 64 cap.
8. **Toggle (counter parity) and timed (clock) blocks** → general `toggle`/`timed` behaviours (puzzle mechanics).
9. **Hot reload in dev (F1)** → editor live-preview/reload.
10. **Dangling-reference tolerance was a symptom** → replaced by strict validation (§SPEC 15).

### 7.2 Things NOT to copy

| Legacy | Why not | V2 replacement |
|---|---|---|
| Binary WMP/WMB/`$$w` + two truths | opaque, tool-locked, build artefacts next to sources | single `map.json` source of truth; deterministic build output |
| Behaviour hidden in the host executable, parameters as anonymous `skill1…20` + 32 flag bits | undiscoverable, untestable, positional | named, typed, schema-validated behaviour blocks |
| Name-coded regions | fragile (typos silently disable) | typed `regions[]` + validator |
| Geometry sketched with WED blocks, no primitive collision shapes | heavy, concave soup | collision primitives |
| Models/textures/materials by *name* from a host table | dangling refs | asset manifest + validator |
| Executable content in packages (`.fx`, `.c`) | security | data-only packages; materials are parameter sets of built-in shaders |
| 64 background objects, 9 checkpoints, 32 mushrooms hard caps | arbitrary engine limits | budgets in validator, instancing |
| Whole map loaded at once | memory, load time | chunk streaming |
| Progress = nearest point on a polyline | jumps on looping paths | windowed/monotonic projection, anchors, branches |
| Map-wide player-rule switches (`double jump`, `puzzle`) | change player physics | out of scope (locked physics) |

---

## 8. Open points / UNKNOWN

| # | Item | Status |
|---|---|---|
| U1 | Meaning of `field3/field4` in code-7 entity tail (values 0,2) | UNKNOWN |
| U2 | Meaning of path flags (8, 28, 158) and per-point 6-number groups | UNKNOWN (unused in samples) |
| U3 | Whether WMP unit = locked-spec Q | INFER (medium): grid 16, hull ±12.5, level spans 6 000–27 000 units; the HUD convention 52 Q = 1 m gives 115–525 m maps, plausible |
| U4 | Exact semantics of `skillN` entries 66–99 (internal scratch slots) | not needed |
| U5 | WMB lump directory field names | only offsets/lengths verified; not needed (V2 does not read WMB) |
| U6 | Runtime effect of boost slime on player state | not in locked spec → V2 declares the behaviour, does not apply it |

## 9. Handoff to the specification

The specification (`MAP_SYSTEM_V2_SPEC.md`) turns §3–§7 into: the `map.json` format, prefab/behaviour/collision/chunk/progress/theme/validation/editor/package systems and an Android performance budget, plus a migration path from the legacy text export and from the existing `LevelData`.
