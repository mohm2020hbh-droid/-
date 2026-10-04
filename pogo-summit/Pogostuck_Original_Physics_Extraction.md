# Pogostuck — Original Physics Extraction (machine-code evidence)

> **Scope.** The physics of the original game, read from the supplied binaries only: `Pogostuck.exe`, `pogoMain.dll` and `acknex.wdf`. `acknex.dll` (the engine) was **not** supplied.
>
> **Rule followed.** A number counts as a *physics constant* only when the assembly shows it entering a physics operation (velocity, position, rotation, collision, spring load or time step).
>
> **Not used as evidence:** values from the GDD, `Pogostuck_Physics_Master*.docx/.xlsx`, or Unity, Box2D and Three.js.
>
> **Companion file:** `Pogostuck_Physics_Constants.csv` (96 rows: every constant with value, IEEE/int hex, literal address, use address, function, instruction and role).
>
> **Confidence classes (as requested):**
> - **A** — confirmed from machine code.
> - **B** — strongly inferred (machine code plus engine semantics or a second independent path).
> - **C** — estimated from gameplay.
> - **D** — unknown.

---

## 0. Result in one paragraph

All player physics is hand-written game code inside one compiled Lite-C function, `playerMove` (285,528 bytes of x86). It sits in the LZSS-compressed overlay of `Pogostuck.exe`. The engine is used only as a collision and maths service: `c_move`, `c_rotate`, `c_trace`, `vec_rotate`, `pow`, `sqrt`.

**Not used anywhere:** the engine routines `vec_bounce`, `vec_accelerate` and `accelerate` have **zero** call sites in all 1807 functions. The engine's `move_friction` is explicitly set to `0` before every player `c_move`.

**Recovered equations, with all their literals (class A):**
- gravity, `‑8.5` per tick², and linear lateral air drag, `0.05` per tick;
- the speed cap, `300`;
- the turn controller: target `32`°/tick, response `0.525`, ground factor `1/3`;
- the grounded "stick" velocity, `‑24·n`;
- the landing impact law: `1.65·|v|^0.925`;
- the spring window: min = `max(40, I^0.9)`; max = `clamp(I+20p, 95+25p, 300)`;
- the charge rate, `16`/tick;
- the launch law: `v = 0.74235·load`, along the pogo axis biased `0.1875` toward the surface normal (clamped ±45°);
- the launch spin kick;
- the wall/"bonk" response (a manual reflection, not `vec_bounce`);
- the ice-slide model;
- the collision hull;
- the frame-time handling: `time_factor = 0.95`, default `fps_max = 120`, user range 30–240.

A second function, `playerPredictPos`, contains an independent copy of the airborne step (`‑0.05`, `‑8.5`). It corroborates the integrator.

**Unresolved:** only the engine internals, because `acknex.dll` is absent. These are how `c_move` glides and depenetrates, the meaning of the mode bits, and how `time_step` is computed and smoothed.

---

## 1. Inputs and integrity

| File (as supplied) | Size | SHA-256 | What it is |
|---|---|---|---|
| `Pogostuck.exe` | 4,453,516 | `14ba43637b3571a97f19761a0009e6459141b4a3c557855f4b595ca6ae3753a9` | A8 engine launcher plus the appended compiled game (overlay) |
| `pogoMain.dll` | 337,408 | `9ffd5a8dacd748e33f060adb84dd642e4d86c460314df3565b3ff15987d837f8` | Steam/platform plug-in (`ackSuper*` exports) |
| `acknex.wdf` | 250,878 | `8951fedf773f3a372fcb671a05a8ae62f7aa91a5194963e49b085d635234281f` | Engine definition file (window title, command line, icon) |
| `acknex.dll` | — | — | **not supplied** |
| decoded overlay image ("IMG") | 15,882,240 | `3dd01fe5daac963a994305e81dc83bc449c48b6cd3f9616ef1b0b1ab13824520` | Produced by this analysis; **not committed** (it is the original program) |

**Tools used:** Python 3, `pefile`, `capstone` 5.0.7. On top of these, a purpose-built Lite-C annotator and symbolic lifter were written for this compiler's code shape (described in §2.3).

**Everything in this report is reproducible from the files above.** All addresses below are **byte offsets in the decoded image (IMG)**, unless prefixed `file:` (raw `Pogostuck.exe` offset) or `VA:` (DLL virtual address).

---

## 2. Binary analysis (steps 1–2)

### 2.1 PE layout

| File | Sections (raw) | Imports | Exports | Notes |
|---|---|---|---|---|
| `Pogostuck.exe` (PE32 i386, entry `0x12c9`, timestamp 2010-06-11) | `.text` 25,444 B (entropy 6.20) · `.rdata` 7,088 · `.data` 6,268 · `.rsrc` 351,151 | `acknex.dll`: **only** `engine_open`, `engine_frame`, `engine_close`; `USER32` (2); `KERNEL32` (60) | — | The `.text` is the stock Gamestudio launcher: it contains no game logic and no game floats. **Overlay at `file:0x61000`, 4,056,204 B, entropy 7.82.** |
| `pogoMain.dll` (PE32 DLL, timestamp 2022-12-13) | `.text` 220,403 · `.rdata` 90,552 · `.data` 22,400 · `.gfids` · `.tls` · `.rsrc` · `.reloc` | `KERNEL32` (89), `steam_api.dll` (13), `IMM32` (2), `ADVAPI32` (1) | 238: `ackSuper*` (Steam achievements, stats, leaderboards, lobbies, cloud, UGC, controller), `compress_lzss`/`decompress_lzss`/`lzssDecode`, `kuEngineModelExporter…` | **No physics.** See §3. |
| `acknex.wdf` | — (not PE; magic `WDF\0`) | — | — | Window title `Pogostuck: Rage With Your Friends`, engine options `-nx 200 -ns`, icon bitmaps. **No code.** |

### 2.2 The overlay: where the game actually is

The overlay is laid out as follows:

| Overlay offset | Contents |
|---|---|
| `+0x00` | Header `aa 55 01 4a 43 02 00 07` |
| `+0x8c` | An obfuscated licensee field. **Deliberately not decoded in this report.** |
| `+0xa0` | Uncompressed size, dword `15,882,240` |
| `+0xa4` to end | Standard Okumura LZSS stream |

LZSS parameters: N = 4096, F = 18, threshold 2, flag bits LSB-first with 1 = literal, window prefilled with `0x20`, r₀ = N−F.

The overlay is **compression, not encryption**. No protection, DRM or anti-tamper code was bypassed or patched. The game's own anti-tamper checks are visible in the code; they were noted and left untouched.

The decoded image is a compiled **Conitec 3D GameStudio A8 Lite-C** program. Evidence:
- the debug database references `C:\GStudio8\include\acknex.h`;
- the engine API names;
- the `.wmb/.mdl/.fxo` assets.

It contains:

| Part | Location | Notes |
|---|---|---|
| Data segment | IMG `0x45` | A global with Lite-C `Pos` *p* lives at **IMG `0x45 + p`** |
| Code | Starts at base `0xdc225` | |
| Debug database (XML) | — | `VarInfo_Map`: 55,746 entries (Name, Type ∈ {FIXED, DOUBLE, FLOAT, LONG, POINTER, …}, Pos, Init source text). `StructInfo`: field names. 899 `CheckerItems`. |
| Function table | IMG `0x6c2a24`, 1807 entries | Code address = `dword[IMG 0x45+slot] + 0xdc225`. **1807/1807** entries land on a valid prologue, giving a complete name → address map. |

### 2.3 How this compiler encodes physics (needed to read the evidence)

| Construct | Machine code | Meaning |
|---|---|---|
| Prologue | `push ebp/ebx/esi/edi; call $+5; pop eax; mov ebx,eax; sub ebx,imm` | Position-independent code: `ebx` = data base, so every global or literal is `[ebx+Pos]` |
| Literal constant | `@const_N` global, type `DOUBLE` or `LONG`, **Init = source text** (e.g. `init=0.74235`) | The exact source literal is recoverable. Some literals are stored float-rounded (e.g. `-0.05` → `-0.05000000074505806`). The CSV gives the stored bits. |
| `var` (fixed 22.10) | int → var `shl eax,0xa`; var·var `imul edx; shrd eax,edx,0xa; adc eax,0`; var/var `mov edx,eax; sar edx,0x16; shl eax,0xa; idiv` | Every player state field is `FIXED` (22.10). Products round half-up (`adc`); quotients truncate. |
| var ↔ double | `call $+13; dq 1024.0; pop eax; fild var; fdiv [eax]` then `fmul [eax]; fistp` | Mixed expressions are evaluated in x87 double and rounded back to 1/1024 (`fistp`, round-to-nearest) |
| Struct field | literal with Init `OFFSET:STRUCT@94:heroSpringLoaded` | Field names are recovered for every access (`arg0.speed.z`, …) |
| Engine call | `mov eax,[ebx+slot]; call eax` | The slot is a `POINTER` global named after the engine function (`c_move`, `vec_rotate`, `time_step`, …) |
| Internal call | `e8 rel32` | Resolved with the function table |

A symbolic lifter (expression reconstruction over this naive temp-slot code) turned every function into statements such as `arg0.speed.z = arg0.speed.z + …`, keeping the IMG address of the final instruction. Each statement below is quoted **with that address**. Each literal is traced to the exact `fld/fmul/mov [ebx+Pos]` instruction that loads it (see the CSV).

---

## 3. Where the physics lives (step 11)

| Candidate | Verdict | Evidence |
|---|---|---|
| `Pogostuck.exe` `.text` | **No game physics** | 25 KB launcher. Imports only `engine_open/frame/close`. |
| **`Pogostuck.exe` overlay (Lite-C)** | **Yes: all player physics** | `playerMove` @IMG `0x3b184b` (60,074 instructions) is called once per rendered frame by `playerMoveAll` @`0x3f81e0` ← `mainFrameEventPlay` @`0x69ceda`. The trajectory predictor `playerPredictPos` @`0x2695e1` duplicates the airborne step. |
| `pogoMain.dll` | **No** | Exports are Steam/IO/LZSS helpers. The game calls from it only achievements (`ackSuperkuUnlockAchievement("jump_high"/"jump_degrees")`), Steam analog input and `ackSuperkuDMod` (an fmod used for replay timing). `ackSuperkuTimeFrame` (`VA:0x1000dc50`, a QPC delta) exists but **is never called** by the game code. Its physics-looking strings are the generic Gamestudio API name table and Steam stat names. |
| `acknex.wdf` | **No** | Configuration and icon only |
| `acknex.dll` (not supplied) | **Engine services only** | Provides `c_move` (collision + glide), `c_trace`, `c_rotate`, `vec_*` maths and the `time_step` clock. Its numeric internals are **unresolved** (§10). The game code shows which services are used and with which arguments. |

---

## 4. Requested symbols: cross-references (step 3)

Counts are call sites across all 1807 decompiled functions.

| Symbol | Exists as | Used by game code? | Physics role (evidence) |
|---|---|---|---|
| `vec_bounce` | Engine API slot `POINTER` (Pos `0x9BFB8`) | **0 call sites** | None. The bounce is computed manually (§7.8). |
| `vec_accelerate` | Engine slot (Pos `0x9BFB0`) | **0** | None |
| `accelerate` | Engine slot (Pos `0x71A90`) | **0** | None |
| `vec_rotateaxis` | Engine slot | **0** | None |
| `c_trace` | Engine slot (Pos `0x7A6D8`) | 26 | Ground probe through `pogo_trace` @`0x1e4aab` (box trace ±`arg4`), ground correction, anti-stuck rays |
| `c_rotate` | Engine slot (Pos `0x7A6CC`) | **1** | The player turn: `playerMove` @`0x3cf7de` |
| `ang_rotate` | Engine slot (Pos `0x749EC`) | 56 | Visual entities only (pogostick and character meshes, camera); never the physics state |
| `vec_rotate` | Engine slot (Pos `0x9C00C`) | 146 | Gravity vector @`0x3d21d2`, launch vector @`0x3dd5fe`, ground-stick vector @`0x3cfef9`, slide gravity direction @`0x3cbab9` |
| `jump_high` | **String literal** (Steam achievement id), not a variable | `playerMove` @`0x3eedaa`, `0x3eee18` | Unlocks when `int(Δz/52) ≥ 50`. Not a physics constant (gives the display unit, §7.13). |
| `jump_degrees` | **String literal** (achievement id) | @`0x3d9784`, `0x3d97d5`, `0x3ee907` | Unlocks when `| |Δangle| − 1080 | < 1.9` at landing. Not a physics constant. |
| `jump` | No variable of that name | — | 47 names contain "jump" (cosmetics such as `doubleJumpHeart`, animation `"jump"`). Real state: `jumpTimer`, `powerJumpNext`, `heroSpringLoaded*`. |
| `friction` | Only the engine var `move_friction` | Written **6×** | Set to `0` before the player `c_move`/`c_rotate` (@`0x3cf699`, `0x3d30aa`, `0x3d30ff`): engine friction is disabled for the player |
| `gravity` | A `FIXED` field of an engine struct (Pos `0x44`) | Not used by player code | Player gravity is the literal `‑8.5` (P01) plus the per-player field `gravityAngle` |
| `forceUp` | **Absent** (only `forceUpdate`) | — | — |
| `jumpH` | **Absent** (only `doubleJumpHeart/Helix`) | — | — |
| `speed` | Player struct field `speed` (VECTOR, STRUCT@94 + `0x99`) | Yes | The velocity (quants per tick) |
| `rootSpeed` | **Absent** | — | — |
| `surfaceNormal` | Player field (FIXED angle, +`0x139`) and `surfaceNormalVector` | Yes | `surfaceNormal = atan2v(n.z, n.x)` @`0x3d8cdf` |
| `GroundContact` | `heroGroundContact`, `heroGroundContactPrev`, `noGroundContactTimer` | Yes | Grounded state machine (§7.10) |
| `SpringLoad` | `heroSpringLoaded`, `…Max`, `…Last`, `…BonePerc`, `…BonePercMax`, `…BoneExtend` (player fields); `heroSpringLoadedMin` (global) | Yes | Spring charge and launch (§7.4–7.6) |
| `Rays`, `tickRays`, `bRays` | Only visual materials and bitmaps: `map3StickRays_mat` ("t**ickRays**" is a substring), `monolithRays_mat`, `stickLightrays`, `moonLightrays0`, `pp_godrays*` | Rendering only | **No physics.** The physical "rays" are the `c_trace` calls above. |

**Dead globals:** `heroBoostPower`, `heroBoostPowerMax`, `heroSpeed` and `heroPogoContact` are declared (with inits) but have **0 uses**. They must not be read as physics parameters.

---

## 5. How a number was admitted as a physics constant (steps 4–7)

1. The literal is located by its `[ebx+Pos]` load, and its type and source text are taken from `VarInfo`.
2. It is accepted **only if** the lifted statement containing that load assigns to, or feeds an engine call that changes, one of:
   - `arg0.speed`, `arg0.slideSpeed`, `arg0.heroTurnSpeed`, `arg0.angle`, `arg0.heroSpringLoaded*`, `arg0.jumpTimer`, `arg0.noGroundContactTimer`;
   - the player entity's position or bounding box;
   - the argument of `c_move`/`c_rotate`/`c_trace`/`vec_rotate` on those quantities;
   - `time_factor` / `fps_max`.
3. Numbers in sound, particles, camera, UI, achievements, emotes and cosmetics were rejected. Exception: the two achievement thresholds give the display unit and are labelled `U*`, not physics.
4. Every accepted literal appears in the CSV with:
   - its stored bits;
   - its literal address (`IMG 0x45+Pos`);
   - the address of the loading instruction;
   - the statement it produces.

---

## 6. Physics constants: main table

The full list is in the CSV. Value units: **quants** (engine length) and **ticks** (1 tick = 1/16 s of *game* time, the Gamestudio unit of `time_step`). "lit" = literal address, "use" = loading instruction.

| ID | PHYSICS CONSTANT | VALUE | HEX | FILE | ADDRESS | FUNCTION | ASSEMBLY EVIDENCE | ROLE | CONFIDENCE |
|---|---|---|---|---|---|---|---|---|---|
| P01 | `GRAVITY_ACCEL` | -8.5 | `0xc021000000000000` | EXE·overlay | lit IMG:0x0ba1f5 · use IMG:0x3d2080 | `playerMove @0x3d20f4` | `3d2080: fld qword ptr [ebx + 0xba1b0]` | gravity: speed += rotate((.., 0, -8.5*time_step), gravityAngle); applied to arg0.speed by vec_add @3d224e (main map: L114=1) | **A** |
| P02 | `AIR_DRAG_LATERAL` | -0.05 (stored -0.05000000074505806) | `0xbfa99999a0000000` | EXE·overlay | lit IMG:0x0ba1ed · use IMG:0x3d1ffe | `playerMove @0x3d20f4` | `3d1ffe: fld qword ptr [ebx + 0xba1a8]` | linear drag on velocity component along gravity-perpendicular axis: dv_lat = -0.05*v_lat*time_step (no vertical drag) | **A** |
| P03 | `GRAVITY_ACCEL_LEVEL8_TILTED` | -6.125 | `0xc018800000000000` | EXE·overlay | lit IMG:0x0ba19d · use IMG:0x3d0381 | `playerMove @0x3d03f5` | `3d0381: fld qword ptr [ebx + 0xba158]` | level_current==8 && gravityAngle!=0: vertical accel -6.125*time_step (replaces P01) | **A** |
| P05 | `GRAVITY_ACCEL_TILTED` | -4.175 (stored -4.175000190734863) | `0xc010b33340000000` | EXE·overlay | lit IMG:0x0ba1cd · use IMG:0x3d1ca0 | `playerMove @0x3d1d14` | `3d1ca0: fld qword ptr [ebx + 0xba188]` | level!=9 && gravityAngle!=0 (gravity zones): vertical accel -4.175*time_step | **A** |
| P07 | `GRAVITY_ACCEL_WATER` | -2 | `0xfffffffe` | EXE·overlay | lit IMG:0x0ba0ad · use IMG:0x3d10b6 | `playerMove @0x3d1126` | `3d10b6: mov eax, dword ptr [ebx + 0xba068]` | level 9 water (not swimming): vertical accel -2*time_step | **A** |
| P09 | `MAX_SPEED` | 300 | `0x0000012c` | EXE·overlay | lit IMG:0x0b9ca9 · use IMG:0x3d2bc8 | `playerMove @0x3d2bf9` | `3d2bc8: mov eax, dword ptr [ebx + 0xb9c64]` | if \|speed\|>300 then vec_normalize(speed,300) (also @3eabdc after platform carry) | **A** |
| P11 | `SLIDE_Z_DISPLACEMENT_GAIN` | 4 | `0x00000004` | EXE·overlay | lit IMG:0x0b99dd · use IMG:0x3d4a3a | `playerMove @0x3d4c92` | `3d4a3a: mov eax, dword ptr [ebx + 0xb9998]` | displacement.z = (speed.z + 4*slideSpeed.z*L3a8)*time_step  (x uses 1*slideSpeed.x) | **A** |
| P12 | `CMOVE_MODE` | 548 | `0x00000224` | EXE·overlay | lit IMG:0x0ba121 · use IMG:0x3d2f52 | `playerMove @0x3d2f58` | `3d2f52: mov eax, dword ptr [ebx + 0xba0dc]` | c_move(me, nullvector, displacement, 548) for player; flag bits are engine-defined (acknex.h not supplied) | **A** |
| P14 | `GROUND_STICK_SPEED` | -24 | `0xc038000000000000` | EXE·overlay | lit IMG:0x0ba179 · use IMG:0x3cfe00 | `playerMove @0x3cfe1b` | `3cfe00: fld qword ptr [ebx + 0xba134]` | while grounded: speed = vec_rotate((-24,0,0),(0,surfaceNormal,0)) = -24*n (press into surface) | **A** |
| P15 | `GROUND_TURN_DIVISOR` | 2 | `0x00000002` | EXE·overlay | lit IMG:0x0b9965 · use IMG:0x3cea7d | `playerMove @0x3ceacc` | `3cea7d: imul eax, dword ptr [ebx + 0xb9920]` | turn factor Lb1c4 = 1/(1+2*heroGroundContact): rotation rate x1/3 on ground | **A** |
| P16 | `TURN_TARGET_RATE` | 32 | `0x00000020` | EXE·overlay | lit IMG:0x0b9b39 · use IMG:0x3ceb16 | `playerMove @0x3cebae` | `3ceb16: mov eax, dword ptr [ebx + 0xb9af4]` | target turn speed L438 = (in[A]-in[D])*32 (deg per tick) | **A** |
| P17 | `TURN_RESPONSE_RATE` | 0.525 (stored 0.5249999761581421) | `0x3fe0ccccc0000000` | EXE·overlay | lit IMG:0x0ba14d · use IMG:0x3cf1e9 | `playerMove @0x3cf32d` | `3cf1e9: fmul qword ptr [ebx + 0xba108]` | heroTurnSpeed += (L438-heroTurnSpeed)*0.525/(1+sqrt(jumpTimer))*time_step | **A** |
| P19 | `PIVOT_MAX_CORRECTION` | 256 | `0x00000100` | EXE·overlay | lit IMG:0x0ba16d · use IMG:0x3cfb29 | `playerMove @0x3cfbee` | `3cfb29: mov eax, dword ptr [ebx + 0xba128]` | grounded: after c_rotate, c_move by (prevBone1Pos - Bone1) if \|dx\|,\|dz\|<256 -> rotation pivots on pogo tip | **A** |
| P20 | `GROUND_PROBE_EXTENSION` | 6 | `0x00000006` | EXE·overlay | lit IMG:0x0b9d55 · use IMG:0x3d81fb | `playerMove @0x3d823f` | `3d81fb: mov eax, dword ptr [ebx + 0xb9d10]` | probe end = Bone1 + normalize(Bone1-Bone2, 6+\|slideSpeed.z\|) | **A** |
| P21 | `GROUND_PROBE_TRACE_MODE` | 613 | `0x00000265` | EXE·overlay | lit IMG:0x0ba2cd · use IMG:0x3d850c | `playerMove @0x3d8544` | `3d850c: mov eax, dword ptr [ebx + 0xba288]` | pogo_trace(me, me.x, probeEnd, 613, 4) -> c_trace mode 613\|8192\|1 | **A** |
| P22 | `GROUND_PROBE_BOX_HALFSIZE` | 4 | `0x00000004` | EXE·overlay | lit IMG:0x0b99dd · use IMG:0x3d84f6 | `playerMove @0x3d8544` | `3d84f6: mov eax, dword ptr [ebx + 0xb9998]` | pogo_trace arg4: helper entity bbox = +-4 on x,y,z | **A** |
| P23 | `IMPACT_EXPONENT` | 0.925 (stored 0.925000011920929) | `0x3fed9999a0000000` | EXE·overlay | lit IMG:0x0ba361 · use IMG:0x3dac5f | `playerMove @0x3dac96` | `3dac5f: mov eax, dword ptr [ebx + 0xba31c]` | landing: impact = pow(\|speed\|,0.925)*1.65 | **A** |
| P24 | `IMPACT_GAIN` | 1.65 (stored 1.649999976158142) | `0x3ffa666660000000` | EXE·overlay | lit IMG:0x0ba369 · use IMG:0x3dacb8 | `playerMove @0x3dace6` | `3dacb8: fmul qword ptr [ebx + 0xba324]` | landing: impact = pow(\|speed\|,0.925)*1.65 | **A** |
| P25a | `IMPACT_POWERJUMP_BONUS` | 20 | `0x00000014` | EXE·overlay | lit IMG:0x0b9de5 · use IMG:0x3daeac | `playerMove @0x3daf10` | `3daeac: mov eax, dword ptr [ebx + 0xb9da0]` | +20*powerJumpNext added to impact before the L_max clamp (same expression feeds clamp @3daff4) | **A** |
| P25 | `SPRING_MAX_FLOOR` | 95 | `0x0000005f` | EXE·overlay | lit IMG:0x0ba371 · use IMG:0x3daf93 | `playerMove @0x3daff4` | `3daf93: mov eax, dword ptr [ebx + 0xba32c]` | heroSpringLoadedMax = clamp(impact+20*powerJump+extra, 95+25*powerJump+extra, 300) | **A** |
| P26 | `SPRING_MAX_FLOOR_POWER_BONUS` | 25 | `0x00000019` | EXE·overlay | lit IMG:0x0b9875 · use IMG:0x3daf69 | `playerMove @0x3daff4` | `3daf69: mov eax, dword ptr [ebx + 0xb9830]` | +25 to the floor of heroSpringLoadedMax when powerJumpNext | **A** |
| P27 | `SPRING_MAX_CAP` | 300 | `0x0000012c` | EXE·overlay | lit IMG:0x0b9ca9 · use IMG:0x3dafca | `playerMove @0x3daff4` | `3dafca: mov eax, dword ptr [ebx + 0xb9c64]` | upper clamp of heroSpringLoadedMax | **A** |
| P28 | `SPRING_MIN_EXPONENT` | 0.9 (stored 0.8999999761581421) | `0x3fecccccc0000000` | EXE·overlay | lit IMG:0x0b9cfd · use IMG:0x3db37f | `playerMove @0x3db3b6` | `3db37f: mov eax, dword ptr [ebx + 0xb9cb8]` | heroSpringLoadedMin = max(40, pow(impact,0.9)) | **A** |
| P29 | `SPRING_MIN_FLOOR` | 40 | `0x00000028` | EXE·overlay | lit IMG:0x0b9ba9 · use IMG:0x3db401 | `playerMove @0x3db41d` | `3db401: mov eax, dword ptr [ebx + 0xb9b64]` | heroSpringLoadedMin = max(40, ...) | **A** |
| P31 | `INPUT_CHARGE_BUTTON` | 4 | `0x00000004` | EXE·overlay | lit IMG:0x0b99dd · use IMG:0x3dbd78 | `playerMove @0x3dbe7e` | `3dbd78: mov eax, dword ptr [ebx + 0xb9998]` | inputIsDown(4) keeps charging (button 4 = scancode 57 Space / joy 257 by inputButtonSetDefault) | **A** |
| P32 | `SPRING_CHARGE_RATE` | 16 | `0x00000010` | EXE·overlay | lit IMG:0x0b9d4d · use IMG:0x3dbf94 | `playerMove @0x3dc019` | `3dbf94: mov eax, dword ptr [ebx + 0xb9d08]` | heroSpringLoaded = min(load + 16*time_step, heroSpringLoadedMax) | **A** |
| P33 | `LAUNCH_MIN_LOAD` | 2 | `0x00000002` | EXE·overlay | lit IMG:0x0b9965 · use IMG:0x3dc310 | `playerMove @0x3dc341` | `3dc310: mov eax, dword ptr [ebx + 0xb9920]` | launch only if heroSpringLoaded > 2 | **A** |
| P34 | `LAUNCH_SPEED_PER_LOAD` | 0.74235 (stored 0.7423499822616577) | `0x3fe7c154c0000000` | EXE·overlay | lit IMG:0x0ba3c1 · use IMG:0x3dd2e5 | `playerMove @0x3dd346` | `3dd2e5: fmul qword ptr [ebx + 0xba37c]` | launch: v = vec_rotate((0,0,0.74235*heroSpringLoaded),(0,a,0)) | **A** |
| P35 | `NORMAL_ANGLE_OFFSET` | 90 | `0x0000005a` | EXE·overlay | lit IMG:0x0b9d29 · use IMG:0x3dd407 | `playerMove @0x3dd47e` | `3dd407: mov eax, dword ptr [ebx + 0xb9ce4]` | d = ang(atan2v(n.z,n.x) - 90 - angle) | **A** |
| P36 | `NORMAL_BLEND_CLAMP` | 45 | `0x0000002d` | EXE·overlay | lit IMG:0x0ba3c9 · use IMG:0x3dd48c | `playerMove @0x3dd4c5` | `3dd48c: mov eax, dword ptr [ebx + 0xba384]` | d clamped to [-45,45] | **A** |
| P37 | `NORMAL_BLEND_FACTOR` | 0.1875 | `0x3fc8000000000000` | EXE·overlay | lit IMG:0x0ba3d1 · use IMG:0x3dd52e | `playerMove @0x3dd5c3` | `3dd52e: fld qword ptr [ebx + 0xba38c]` | launch angle a = angle + 0.1875*clamp(d,-45,45) | **A** |
| P38 | `LAUNCH_SLIDE_CARRY_X` | 0.25 | `0x3fd0000000000000` | EXE·overlay | lit IMG:0x0b9c55 · use IMG:0x3dd6ef | `playerMove @0x3dd775` | `3dd6ef: fmul qword ptr [ebx + 0xb9c10]` | speed.x = Vlaunch.x + 0.25*slideSpeed.x | **A** |
| P40 | `LAUNCH_SPIN_FROM_TILT` | 0.1245 (stored 0.12449999898672104) | `0x3fbfdf3b60000000` | EXE·overlay | lit IMG:0x0ba43d · use IMG:0x3e007e | `playerMove @0x3e0185` | `3e007e: fmul qword ptr [ebx + 0xba3f8]` | heroTurnSpeed += (0.1245*clamp(ang(angle-gravityAngle),-45,45) - 0.25*1.5*sign(s)*\|s\|^0.75)*(1-0.5*special), s=asinv(normal.x) | **A** |
| P44 | `NO_GROUND_TIMER_AFTER_LAUNCH` | 2 | `0x00000002` | EXE·overlay | lit IMG:0x0b9965 · use IMG:0x3e030d | `playerMove @0x3e0339` | `3e030d: mov eax, dword ptr [ebx + 0xb9920]` | noGroundContactTimer = 2 (ticks) after launch; grounded flag forced 0 while >0 | **A** |
| P45 | `JUMP_TIMER_GAIN` | 0.45 (stored 0.44999998807907104) | `0x3fdcccccc0000000` | EXE·overlay | lit IMG:0x0ba449 · use IMG:0x3e03e0 | `playerMove @0x3e0438` | `3e03e0: fmul qword ptr [ebx + 0xba404]` | jumpTimer = 0.45*sqrt(heroSpringLoadedMax); damps turn response via 1/(1+sqrt(jumpTimer)) | **A** |
| P46 | `POWERJUMP_ROTATION_THRESHOLD` | 285 | `0x0000011d` | EXE·overlay | lit IMG:0x0ba661 · use IMG:0x3ee4f7 | `playerMove @0x3ee58b` | `3ee4f7: mov edx, dword ptr [ebx + 0xba61c]` | in air: \|angle-lastJumpAngle\| > 285 deg -> powerJumpNext = 1 (boost on next landing) | **A** |
| P47 | `BONK_REFLECT` | -2 | `0xfffffffe` | EXE·overlay | lit IMG:0x0ba0ad · use IMG:0x3e56b3 | `playerMove @0x3e56f1` | `3e56b3: mov eax, dword ptr [ebx + 0xba068]` | bounce = speed - 2*(n.speed)*n (manual reflection; engine vec_bounce never called) | **A** |
| P48 | `BONK_DIR_REFLECT_WEIGHT` | 0.9 (stored 0.8999999761581421) | `0x3fecccccc0000000` | EXE·overlay | lit IMG:0x0b9cfd · use IMG:0x3e5845 | `playerMove @0x3e5867` | `3e5845: fld qword ptr [ebx + 0xb9cb8]` | bounce = normalize(reflect,0.9) + n | **A** |
| P49 | `BONK_SPEED_FACTOR` | 0.4 (stored 0.4000000059604645) | `0x3fd99999a0000000` | EXE·overlay | lit IMG:0x0ba539 · use IMG:0x3e591f | `playerMove @0x3e596a` | `3e591f: fmul qword ptr [ebx + 0xba4f4]` | bonk speed = max(28, 0.4*\|speed\|)*min(1+normal.z,1) | **A** |
| P50 | `BONK_MIN_SPEED` | 28 | `0x0000001c` | EXE·overlay | lit IMG:0x0b9fc1 · use IMG:0x3e594e | `playerMove @0x3e596a` | `3e594e: mov eax, dword ptr [ebx + 0xb9f7c]` | bonk speed floor 28 | **A** |
| P51 | `BONK_X_SCALE` | 0.875 | `0x3fec000000000000` | EXE·overlay | lit IMG:0x0ba541 · use IMG:0x3e5cfc | `playerMove @0x3e5dd5` | `3e5cfc: fmul qword ptr [ebx + 0xba4fc]` | speed.x = 0.875*bounce.x + slideSpeed.x; speed.z = bounce.z + slideSpeed.z | **A** |
| P52 | `BONK_SPIN_RIGHTING` | 0.5 | `0x3fe0000000000000` | EXE·overlay | lit IMG:0x0b986d · use IMG:0x3e610c | `playerMove @0x3e61a4` | `3e610c: fmul qword ptr [ebx + 0xb9828]` | heroTurnSpeed = 0.5*ang(gravityAngle-angle) - 0.2*1.5*sign(s)*\|s\|^0.75 | **A** |
| P55 | `ICE_SLIDE_TARGET_SPEED` | 48 | `0x00000030` | EXE·overlay | lit IMG:0x0b9ba1 · use IMG:0x3cbba9 | `playerMove @0x3cbbed` | `3cbba9: mov eax, dword ptr [ebx + 0xb9b5c]` | slide target = normalize(surfaceNormalVector + gravityDir, 48 - 24*(level10\|L30)) | **A** |
| P57 | `ICE_SLIDE_RESPONSE` | 0.25 | `0x3fd0000000000000` | EXE·overlay | lit IMG:0x0b9c55 · use IMG:0x3cbdc1 | `playerMove @0x3cbe3c` | `3cbdc1: fmul qword ptr [ebx + 0xb9c10]` | slideSpeed += clamp((target-slideSpeed)*0.25, -1.35, 1.35)*time_step (x and z) | **A** |
| P58 | `ICE_SLIDE_ACCEL_CLAMP` | 1.35 (stored 1.350000023841858) | `0x3ff59999a0000000` | EXE·overlay | lit IMG:0x0ba0d5 · use IMG:0x3cbddb | `playerMove @0x3cbe3c` | `3cbddb: fld qword ptr [ebx + 0xba090]` | per-tick change limited to +-1.35 | **A** |
| P59 | `SLIDE_DECAY_RATE` | 0.5 | `0x3fe0000000000000` | EXE·overlay | lit IMG:0x0b986d · use IMG:0x3cca3b | `playerMove @0x3cca8b` | `3cca3b: fld qword ptr [ebx + 0xb9828]` | not in slide mode & grounded & \|slide\|>=0.25: slideSpeed = lerp(slideSpeed, 0, 0.5*time_step); else 0 | **A** |
| P71 | `LEDGE_EXIT_POP_SPEED` | 5 | `0x00000005` | EXE·overlay | lit IMG:0x0b9889 · use IMG:0x3d86c9 | `playerMove @0x3d86f5` | `3d86c9: mov eax, dword ptr [ebx + 0xb9844]` | was grounded && probe misses: speed.x = slideSpeed.x; speed.z = 5 (leave ledge with small upward speed) | **A** |
| P62 | `HULL_MIN_X` | -12.5 | `0xc029000000000000` | EXE·overlay | lit IMG:0x0b9a81 · use IMG:0x3b3dde | `playerMove @0x3b3e09` | `3b3dde: fld qword ptr [ebx + 0xb9a3c]` | player entity bbox min_x | **A** |
| P63 | `HULL_MAX_X` | 12.5 | `0x4029000000000000` | EXE·overlay | lit IMG:0x0b9a79 · use IMG:0x3b3e4c | `playerMove @0x3b3e77` | `3b3e4c: fld qword ptr [ebx + 0xb9a34]` | player entity bbox max_x | **A** |
| P64 | `HULL_MAX_Z` | 30 | `0x0000001e` | EXE·overlay | lit IMG:0x0b9a99 · use IMG:0x3b3f5e | `playerMove @0x3b3f8a` | `3b3f5e: mov eax, dword ptr [ebx + 0xb9a54]` | player entity bbox max_z | **A** |
| P65 | `HULL_MIN_Z_BASE` | -55 | `0xffffffc9` | EXE·overlay | lit IMG:0x0ba5a1 · use IMG:0x3eb14b | `playerMove @0x3eb19e` | `3eb14b: mov eax, dword ptr [ebx + 0xba55c]` | min_z = -55 + max(heroSpringLoadedBoneExtend,-12.25) (when noGroundContactTimer==0) | **A** |
| T01 | `TIME_FACTOR_PLAY` | 0.95 (stored 0.949999988079071) | `0x3fee666660000000` | EXE·overlay | lit IMG:0x0dad09 · use IMG:0x69c64f | `mainFrameEventPlay @0x69c67a` | `69c64f: fld qword ptr [ebx + 0xdacc4]` | *time_factor = 0.95 every frame in play (engine scales time_step by time_factor) | **A (write) / B (engine semantics)** |
| T02 | `FPS_LIMIT_DEFAULT` | 120 | `0x00000078` | EXE·overlay | lit IMG:0x0c6efd · use IMG:0x4e750d | `settingsDefault @0x4e7522` | `4e750d: mov eax, dword ptr [ebx + 0xc6eb8]` | fps_limit2 = 120 -> *fps_max = fps_limit | **A** |
| T03 | `FPS_LIMIT_RANGE_MAX` | 240 | `0x000000f0` | EXE·overlay | lit IMG:0x0c7ed9 · use IMG:0x4ee498 | `dec_do @0x4ee4d1` | `4ee498: mov eax, dword ptr [ebx + 0xc7e94]` | fps_limit2 clamped to [30,240] | **A** |
| U01 | `QUANTS_PER_DISPLAY_METER` | 52 | `0x00000034` | EXE·overlay | lit IMG:0x0ba679 · use IMG:0x3eeba5 | `playerMove @0x3eebe5` | `3eeba5: mov eax, dword ptr [ebx + 0xba634]` | jump height shown as int(dz/52) "%dm" | **A** |

---

## 7. Assembly paths and how each equation was derived

Listings are IMG addresses from `playerMove` (unless named otherwise). They are condensed: pure stack-temp shuffles (`mov [ebp+t],eax / mov eax,[ebp+t]`) are omitted, and every remaining line is verbatim.

### 7.1 Gravity and air drag (P01, P02): full path

```asm
3d1fdb  jne / jmp            ; if (!L114) skip  (L114=1 unless a level-specific branch replaced the step)
3d1fe2  call $+13 ; dq 1024.0          ; var->double divisor
3d1ff0  fild dword ptr [ebp+0x12c]     ; L12c = vec_dot(normal, arg0.speed)   (set @3d0202)
3d1ff6  fdiv qword ptr [eax]           ; /1024
3d1ffe  fld  qword ptr [ebx+0xba1a8]   ; @const_135335  DOUBLE -0.05  (stored 0xbfa99999a0000000)
3d2004  fmul qword ptr [ebp+0x55d4]    ; -0.05 * v_lat
3d2015  mov  esi,[ebx+0x998c0]         ; time_step
3d2031  fild / fdiv 1024               ; time_step as double
3d2045  fmul                           ; -0.05 * v_lat * time_step
3d2080  fld  qword ptr [ebx+0xba1b0]   ; @const_135341  DOUBLE -8.5
3d2086  fmul qword ptr [ebp+0x55fc]    ; -8.5 * time_step
3d20a6  fmul [eax] (1024) ; fistp      ; -> var
3d20f4  call eax                       ; vector(x=-0.05*v_lat*ts, y=0, z=-8.5*ts)
3d2123  call eax                       ; vec_set(L4cc, ...)
3d213d  mov ecx,[ebx+0xba078]          ; OFFSET:STRUCT@94:gravityAngle
3d2197  call eax                       ; vector(0, arg0.gravityAngle, 0)
3d21d2  call eax                       ; vec_rotate(L4cc, (0,gravityAngle,0))
3d21ec  mov ecx,[ebx+0xb99b4]          ; OFFSET:STRUCT@94:speed
3d224e  call eax                       ; vec_add(arg0.speed, L4cc)
```

The axis is built at `3d00af–3d0202`: `normal = vec_rotate((1,0,0), (0, gravityAngle, 0))`, then `L12c = vec_dot(normal, arg0.speed)`.

**Derivation.** Per frame:

> **v ← v + R(gravityAngle)·( −0.05·(v·ê)·Δt , 0 , −8.5·Δt )**,
>
> where ê = R(gravityAngle)·x̂ and Δt = `time_step` (ticks).

On the main map `gravityAngle = 0` (@`0x3ce025`), so this reduces to:
- `v.z −= 8.5·Δt` (z is up in Gamestudio);
- `v.x −= 0.05·v.x·Δt`;
- **no vertical drag**.

The same vector is built by `playerPredictPos` @`0x269a78` with `Δt = 1.5` (@`0x269637`), followed by `pos += v·Δt` (@`0x269c3c`). This is an independent second path, which fixes the integrator as **semi-implicit (symplectic) Euler**.

**Level-specific variants (same code shape, A):**

| Condition | Gravity | Drag | Address |
|---|---|---|---|
| Level 8 with `gravityAngle≠0` | `−6.125` | `−0.025` | @`0x3d03f5` |
| Gravity zones (`level≠9`, `gravityAngle≠0`) | `−4.175` | `−0.025` | @`0x3d1d14` |
| Water (level 9, not swimming) | `−2` | `−0.025` | @`0x3d1126` |

`gravityAngle` itself is set per level and zone:
- `0` on the main map;
- `atan2v(dx, −dz)` toward gravity-helper entities (`appendixDoPlayerGravity` @`0x3aa77d`, `monolithDoPlayerGravity`, level 10 death-blocks @`0x3ce5f0`);
- `−map3Data.slantedSmoothed` (level 9);
- `−180` (a special mode).

### 7.2 Turning (P15–P17)

```asm
3cea7d..3ceacc  Lb1c4 = 1.0 / (1.0 + 2*!!heroGroundContact)      ; consts 2, 1.0, 1.0
3cead9/3ceaf1   inputGetValue(2), inputGetValue(3)                ; buttons 2/3 = scancodes 30 (A) / 32 (D)
3ceb16          @const LONG 32  -> L438 = (in2-in3)*32*(1-2*inputPlayerTurnInverted)
3cf1e9  fmul qword ptr [ebx+0xba108]   ; @const_134836 DOUBLE 0.525
3cf207  ...STRUCT@94:jumpTimer ; 3cf258 call sqrt
3cf274  fld  qword ptr [ebx+0xb9b74]   ; 1.0  ; fadd sqrt(jumpTimer)
3cf28c  fdiv                            ; (L438-heroTurnSpeed)*0.525 / (1+sqrt(jumpTimer))
3cf2cd  fmul time_step ; 3cf32d store  -> arg0.heroTurnSpeed +=
3cf7a6  vector(0, Lb1c4*heroTurnSpeed*time_step, 0)
3cf7de  call eax                        ; c_rotate(me, ..., mode 548|0x40000 on slide)
3cf945  arg0.angle = me.tilt
```

**Derivation.**
- `ω ← ω + (ω_target − ω)·0.525·Δt / (1 + √jumpTimer)`, with `ω_target = ±32·input` (degrees per tick).
- The body rotates by `ω·Δt` in the air and by `ω·Δt/3` on the ground.
- `me.min_z` is raised by 16 during the rotation (@`0x3cf536`/`0x3cf85f`).

**Input values.** Keyboard input gives 1. Analog input goes through dead-zone, `inputFactor` and `pow(·, inputPowerExponent2)` (default 1, user range 0.125–2), with an optional "speed button" multiplier `inputSpeedModifierFac` (default 0.667, user range 0.25–1) (`inputUpdate` @`0x1d9ab6`, `0x1da3ac`, `0x1da6e6`).

**Other turn controllers (A):**
- Outro/autopilot: `ω += ((ang(−20−angle)·0.375 − ω)·0.525)·Δt` (@`0x3cee06`).
- Water: input scaled by `0.6−0.15·waterAccelerating` (@`0x3cf15a`).

### 7.3 Grounded: rotation pivot on the pogo tip, and stick velocity (P14, P19)

```asm
3cfa60  vec_for_bone(L43c, me, "Bone1")
3cfad2  vec_diff(L460, arg0.prevBone1Pos, L43c)
3cfba0  @const LONG 256 ; |L460.x|<256 && |L460.z|<256
3cfdbe  c_move(me, nullvector, (L460.x,0,L460.z), 548 [|0x40000 in slide mode]) ; translate so Bone1 returns to its old spot
3cfe00  fld qword ptr [ebx+0xba134]    ; @const_134990 DOUBLE -24
3cfe1b  vector(-24,0,0) ; 3cfe64 STRUCT@94:surfaceNormal ; 3cfebe vector(0,surfaceNormal,0)
3cfef9  vec_rotate(L4cc, ...) ; 3cffa5 arg0.speed.x = L4cc.x ; 3d004b arg0.speed.z = L4cc.z
```

**Derivation.**
- While grounded, every frame the turn is followed by a `c_move` that puts the stick tip (`Bone1`) back where it was. The **turn pivots on the pogo tip**.
- The velocity is overwritten with `v = R(surfaceNormal)·(−24,0,0)`.
- Since `surfaceNormal = atan2v(n.z,n.x)` (@`0x3d8cdf`), this is **`v = −24·n`**: a constant press into the surface. The later `c_move` uses it to keep contact.
- The direction convention is **B**: on flat ground (n = +z) the alternative sign would make `v = +24` upward and break contact every frame, which the state machine does not allow.
- `prevBone1Pos` is refreshed each grounded frame (@`0x3eb3a8`).

### 7.4 Landing: impact and spring window (P23–P29)

```asm
3dac4a  call vec_length(arg0.speed)
3dac5f  push @const_136749 DOUBLE 0.925 ; 3dac96 call pow
3dacb8  fmul qword ptr [ebx+0xba324]   ; @const_136752 DOUBLE 1.65
3dacf2  mov [ebx+0x96e0c],eax          ; playerHeroImpactSpeedTrue = L3e0
3daf48  STRUCT@94:powerJumpNext ; 3daf69 LONG 25 ; 3daf93 LONG 95 ; 3dafca LONG 300
3daff4  call clamp                      ; heroSpringLoadedMax = clamp(L3e0+20*p+e, 95+25*p+e, 300)
3db37f  push @const_132070 DOUBLE 0.9 ; 3db3b6 call pow ; 3db3f4 heroSpringLoadedMin = pow(L3e0,0.9)
3db401  LONG 40 ; 3db41d call maxv ; 3db431 heroSpringLoadedMin = max(40, …)
```

**Derivation.** On the first grounded frame (`!heroGroundContactPrev && heroGroundContact`, @`0x3d9525`):

> **I = 1.65·|v|^0.925**
> **L_min = max(40, I^0.9)**
> **L_max = clamp(I + 20p + e, 95 + 25p + e, 300)**

- `p = powerJumpNext`: 0/1, or 5 in one special mode.
- `e` = `20` on special entities (`skill[99]==38`, @`0x3da2b6`) + `playerBoostExtraForNextJump`.
- `superJump` regions force `L_min = L_max = 300` (@`0x3db5f3`).
- A `limitJump` region (level 8) caps `L_max` at 125.
- `playerResetSpeed` caps `L_max` at 150 (@`0x26a9a1`).
- The `20` in `I+20p` comes from a `20·powerJumpNext` product at @`0x3daf10`. The same statement recomputes `playerHeroImpactSpeedCalc`.

### 7.5 Charging (P30–P33)

```asm
3dbd78  inputIsDown(4)          ; button 4 = scancode 57 (Space) / joystick 257 (inputButtonSetDefault @1d440d/1d46bf)
3dbe7e  if ((load < L_min + 20*outroPlaying) || (Space && !outro)) && load < L_max :
3dbf94  @const LONG 16 ; imul/shrd ; 3dc019 minv -> heroSpringLoaded = min(load + 16*time_step, L_max)
3dc341  else if load > 2 : LAUNCH
```

**Derivation.**
- The spring charges at **16 per tick**.
- It charges automatically until `L_min`, then keeps charging while **Space** is held, up to `L_max`.
- Releasing Space, or reaching `L_max`, launches.
- `heroSpringLoaded` is forced to `0` whenever the player is not grounded (@`0x3d94a8`).

### 7.6 Launch (P34–P39)

```asm
3dd2e5  fmul qword ptr [ebx+0xba37c]   ; @const_137124 DOUBLE 0.74235  (heroSpringLoaded * 0.74235)
3dd346  vector(0,0, load*0.74235)      ; along local +z
3dd3f9  atan2v(L298.z, L298.x)         ; L298 = surface normal saved at contact
3dd407  LONG 90 ; 3dd47e ang(atan2v(n) - 90 - angle)
3dd48c/3dd4a2  LONG 45 / -45 ; 3dd4c5 clamp
3dd52e  fld qword ptr [ebx+0xba38c]    ; @const_137151 DOUBLE 0.1875
3dd5c3  vector(0, angle + 0.1875*d, 0) ; 3dd5fe vec_rotate
3dd6ef  fmul 0.25 (slideSpeed.x)       ; 3dd775 speed.x = Vx + 0.25*slideSpeed.x
3dd83e  LONG 0                          ; 3dd8ac speed.z = Vz + 0*slideSpeed.z
```

**Derivation.**

> **|v₀| = 0.74235·L**, with direction = pogo axis rotated by **0.1875·clamp(ang(θₙ − 90° − angle), ±45°)** toward the surface normal.

- With Gamestudio's tilt rotation, `R(a)·(0,0,s) = (−s·sin a, 0, s·cos a)`, so `angle = 0` points straight up.
- On flat ground the launch angle is `0.8125·angle` for |angle| ≤ 45°.
- Horizontal slide is carried at 25%; vertical slide is discarded.
- The velocity is **replaced**, not added: pre-landing velocity is not kept, except through `I` → `L_max`.
- Special entities (`skill[98]&2048`) use `0.1` instead of `0.1875` (@`0x3ddfef`).

### 7.7 Launch spin and post-launch timers (P40–P46)

```asm
3dfe2f  ang(angle - gravityAngle) ; 3dfe76 clamp(-45,45)
3dfed0  asinv(normal.x) ; 3dff9d pow(|s|, 0.75) ; 3dffc6 sign(s)
3e007e  fmul 0.1245 ; 3e0008 1.5 ; 3e00ac 0.25 ; 3e00ef 0.5
3e0185  heroTurnSpeed += (0.1245*clamp(ang(angle-gravityAngle),±45) - 0.25*1.5*sign(s)|s|^0.75) * (1-0.5*special)
3e0277  heroSpringLoaded = 0 ; 3e02d8 heroGroundContact = 0 ; 3e030d LONG 2 -> 3e0339 noGroundContactTimer = 2
3e03be  sqrt(heroSpringLoadedMax) ; 3e03e0 fmul 0.45 ; 3e0438 jumpTimer = 0.45*sqrt(L_max)
```

**Derivation.**
- A tilted launch adds spin in the direction of the tilt, at `0.1245`°/tick per degree of tilt (capped at 45°).
- A sloped surface adds counter-spin: `−0.375·sign(s)·|s|^0.75`, where `s` is the slope angle in degrees.
- `jumpTimer` (decremented by `Δt` while airborne, @`0x3cb88a`) damps the turn response through `1/(1+√jumpTimer)` for the first few ticks.
- `noGroundContactTimer = 2` ticks blocks re-grounding (@`0x3cb5ff`).

**Power jump.** If the body turned more than **285°** in the air since the last jump (`|angle − lastJumpAngle| > 285`, @`0x3ee58b`), `powerJumpNext = 1`. The next landing then gets `+20` on `I` and a floor of `120`, and boost effects play.

### 7.8 Wall / body hit ("bonk"): manual reflection (P47–P54)

This path runs when `c_move` reported a hit (`hit.flags & 0x600`), the probe is not a valid ground contact, and the contact point is within 256 quants (@`0x3e2b57`).

```asm
3e5632  vec_set(bounce, n)        ; only when the engine's bounce vector is not available (L98==0)
3e56a5  vec_dot(n, speed) ; 3e56b3 LONG -2 ; 3e56f1 vec_scale(bounce, -2*dot) ; 3e5757 vec_add(bounce, speed)
3e5845  DOUBLE 0.9 ; 3e5867 vec_normalize(bounce, 0.9) ; 3e58a5 vec_add(bounce, n)
3e58ef  vec_length(speed) ; 3e591f fmul 0.4 ; 3e594e LONG 28 ; 3e596a maxv
3e598a  normal.z ; 3e59ab/3e59ce LONG 1 ; 3e59f1 minv(1+normal.z, 1) ; 3e5a0b imul/shrd ; 3e5a20 L8c = product
3e5b52  vec_normalize(bounce, L8c) ; 3e5c65 if n.z>0: bounce.z = n.z*L8c
3e5cfc  fmul 0.875 ; 3e5dd5 speed.x = 0.875*bounce.x + slideSpeed.x ; 3e5ee1 speed.z = bounce.z + slideSpeed.z
3e610c  0.5 ; 3e613a 0.2 ; 3e61a4 heroTurnSpeed = 0.5*ang(gravityAngle-angle) - 0.2*1.5*sign(s)|s|^0.75
3e52be  LONG 2 ; 3e52ea noGroundContactTimer = 2 ; 3e54d1 heroSpringLoaded = 0
```

**Derivation.**
- Direction: **d = normalize(0.9·r̂ + n)**, where `r = v − 2(v·n)n`. The engine's `bounce` vector is used instead of `r` when `c_move` supplied one (`L98 ≠ 0`); its exact computation is engine-internal (§10).
- Speed: **|v′| = max(28, 0.4|v|)·min(1 + n.z, 1)**.
- If `n.z > 0`, `v′.z = n.z·|v′|`.
- `v′.x` is then scaled by `0.875`.
- Spin is **set** (not added) to `0.5·ang(gravityAngle − angle)`, which rotates the body back toward upright, plus a slope term.
- There is **no restitution coefficient**: the rebound speed is `max(28, 0.4|v|)`, not `e·|v|`.

Hazard entities (e.g. `skill[99]==33` thorn/fall blocks, lava zones) override this with fixed kicks of `25–30` (clamped to ±`90`), `jumpTimer = 8` and `stunnedTimer = 4` (@`0x3e7b8a…0x3ea1a5`). Bounce entities (`skill[98]&144`) instead add, on landing, a radial push of `30` (`10` with flag 128) and clamp `v.z ≤ 170` (rows E01/E02).

### 7.9 Integration step, speed cap, collision call (P09–P13)

```asm
3d2bba  vec_length(speed) ; 3d2bc8 LONG 300 ; 3d2c58 vec_normalize(speed, 300)        ; |v| <= 300
3d2f52  LONG 548 -> L264 ; 3d30b2 LONG 0x40000 -> L264 |= (if noGroundContactTimer|slide|water)
3d30ff  *move_friction = 0
3d48e1  d.x = (Lb210*(speed.x + slideSpeed.x*L3a8) + extra.x*L254 + grapple.x) * time_step
3d4a3a  LONG 4 ; 3d4c92 d.z = (speed.z + 4*slideSpeed.z*L3a8 + extra.z*L254 + grapple.z) * time_step
3d4e36  c_move(me, nullvector, d, L264)
```

**Derivation.**
- **x ← x + v·Δt** follows the velocity update (semi-implicit Euler).
- Collision response, sliding along walls and depenetration happen **inside `c_move`** (engine).
- `Lb210` is 1, except near hard level edges where it fades to 0 (soft walls such as `1 − ((−11350 − x)/1000)²`).
- `me.y = 0` is forced every frame (@`0x3ccb5c`, `0x3eb1ff`): the game is planar in x–z.

### 7.10 Ground probe and grounded state (P20–P22, P71)

- **Probe ray** (@`0x3d813b–0x3d8544`): from the entity origin to `Bone1 + normalize(Bone1 − Bone2, 6 + |slideSpeed.z|)`, i.e. slightly past the pogo tip along the stick, using `pogo_trace(me, …, 613, 4)`.
- `pogo_trace` (@`0x1e4aab`) moves a helper entity with a **±4-quant box** to the start and runs `c_trace(start, end, 613|8192|1)` (@`0x1e500a`).
- **On hit:** `normal.y = 0`, normalize, `surfaceNormal = atan2v(n.z, n.x)` (@`0x3d8d1d`).
- **Grounded condition:** `heroGroundContact = trace_hit && (c_move hit flags & 0x600) && Lb200` (@`0x3d8e41`; `Lb200` is an entity-flag filter on the contact entity, @`0x3d875e–0x3d8905`). It is forced to 0 when there is no trace hit, while stunned, while `noGroundContactTimer > 0` or in water.
- **Ledge exit:** if the player was grounded and the probe misses, `v = (slideSpeed.x, +5)` (@`0x3d8674`, `0x3d86f5`).
- **Ground correction** (`playerUseCorrection = 2`, @`0x3d6e5e–0x3d76df`): a probe to `me + 0.9·(Bone1 − me)`, then a push along the normal of `0.5·(n·dir)·(len − dist)`.
- **Anti-stuck** (`pogoCorrectionDo` @`0x26d8dc`): 12 radial `c_trace` rays of 64 quants every 30°; push-out normalized to 16.

### 7.11 Ice / slide surfaces (P55–P60, P72)

- **Entering slide mode.** On landing on an entity with `flags & 64` (`map3WaterSlippery` sets it, @`0x34a778`), or in the special mode `L30`:
  - `flags |= 1`;
  - `slideSpeed.x = speed.x`;
  - `slideSpeed.z = 0.25·speed.z` (in some levels when `speed.z > 0`), else `0`.
- **While in slide mode** (@`0x3cb9db–0x3cc09e`):
  - `g = R(gravityAngle)·(0,0,−1)`;
  - `t = normalize(surfaceNormalVector + g, 48)` (24 in level 10/L30, and `t.z·0.667`);
  - **`slideSpeed += clamp((t − slideSpeed)·0.25, −1.35, 1.35)·Δt`** per axis.
- **Outside slide mode** (@`0x3cc88c–0x3cca8b`): `slideSpeed = 0` if `|slideSpeed| < 0.25` or airborne; otherwise it decays: `slideSpeed = lerp(slideSpeed, 0, 0.5·Δt)`.
- The slide feeds the displacement with gain 1 (x) and **4** (z) (§7.9). Slide mode clears whenever the player is airborne (@`0x3cb922`).

### 7.12 Time step (T01–T05)

- **`mainFrameEventPlay`** (@`0x69c67a`): `*time_factor = 0.95` **every play frame**. It then calls `playerMoveAll()` → `playerMove(playerDataLocal)` **once per frame** (@`0x69ceda`, `0x3f825a`).
- **Super-jump slow motion:** `time_step *= 0.5…1` (@`0x69ccd3`). Map-3 outro: `time_step *= 0.15`.
- **Frame-rate cap:**
  - `settingsDefault` (@`0x4e7522`): `fps_limit2 = 120`, then `fps_max = fps_limit`;
  - `dec_do` (@`0x4ee4d1`): user value clamped to `[30, 240]`;
  - `mainFrameEvent` (@`0x6a168e`): `fps_max = fps_limit`, or `60` when unfocused with `fpsLimitTabbedOut`.
- **Anti-tamper:** `time_factor < 0.9` or `> 1` exits the game (@`0x6a14e9`, `0x6a156d`).
- **Invalid runs:** frames longer than 1 tick (`time_frame > 1`) count toward `invalidRun` (@`0x69c6ef`).
- **No fixed-step accumulator exists in the game code.** Physics integrates with the variable per-frame `time_step`.
- `fps_lock`, `time_smooth` and `fpsTAS120` are never referenced by the game code.

### 7.13 Units recovered from the code (U01–U04)

- `int((z − lastJumpZ)/52)` is displayed as `"%dm"` (@`0x3eebe5`, `0x3eec3c`). The game's displayed **metre is 52 quants**.
- `"jump_high"` unlocks at ≥ 50 m (@`0x3eedf7`). The reconstructed model gives a maximum apex of about 55.8 m (§11.1), consistent with an achievement that requires near-maximum loads.
- **Time:** Gamestudio ticks (16 per second) drive the `time_frame/16.0` run-timer conversions (@`0x3ecd16`, `0x444e7a`). Physics uses `time_step`, which is additionally scaled by `time_factor = 0.95`.

### 7.14 Collision hull (P62–P68)

- `min_x/max_x = ∓12.5`, `min_y/max_y = ∓pogo_width_y` (global, init 32), `max_z = 30` (@`0x3b3e09–0x3b3f8a`).
- **`min_z = −55 + max(heroSpringLoadedBoneExtend, −12.25)`** (@`0x3eb19e`). The bottom of the hull follows the animated spring extension:
  - `extend = 18·sin(0.9·BonePerc)`;
  - `BonePerc` relaxes by `120·Δt` in the air;
  - the negative branch is rescaled by `0.01·PercMax·(200 + extend)·0.0025`.

---

## 8. Per-frame pipeline (real statement order in `playerMove`)

1. Timers: `stunnedTimer`, `noGroundContactTimer`, `jumpTimer` (−Δt, ≥ 0) @`3cb537…3cb8c8`. Airborne → clear slide flag.
2. Slide-speed update (slide mode) or decay @`3cb980…3ccaed`.
3. Moving-platform carry (rotating platforms `skill[99]==21`, conveyors) @`3ccb97…3cdf86`.
4. `gravityAngle` selection per level @`3cdfbf…3cea1e`.
5. Turn controller: `ω` update → `c_rotate` → `angle = me.tilt` @`3ceacc…3cf945`.
6. Grounded: pivot-on-tip `c_move`, then `v = −24·n`. Airborne: `v += R(gravityAngle)(−k·v_lat, 0, −g)·Δt` (or water/zone variants) @`3cfa24…3d224e`.
7. Speed cap `|v| ≤ 300` @`3d2bba`.
8. `move_friction = 0`; `c_move(me, 0, (v + slide·gains)·Δt, 548[|0x40000])` @`3d4e36`. Level boundary clamps.
9. Ground correction (`pogo_trace`) and `pogoCorrectionDo` @`3d6e5e…3d7af0`.
10. Ground probe → `surfaceNormal`, `heroGroundContact` @`3d813b…3d8f33`. Not grounded → `heroSpringLoaded = 0`.
11. Landing frame: impact `I`, `L_min`, `L_max`, slide-mode entry @`3d9525…3db661`.
12. Grounded → charge or **launch** (§7.5–7.7). Hit while not grounded → **bonk** (§7.8) @`3dbaf8…3ea7ca`.
13. Airborne: add `playerLateralSpeed` (platform release); cap 300 again @`3ea8b7…3eabdc`.
14. Spring visual → hull `min_z`; `prevBone1Pos` refresh @`3ead01…3eb3a8`.

---

## 9. CONFIRMED ORIGINAL PHYSICS (class A unless marked)

Units: L = quants, T = ticks (Δt = `time_step`). State is 22.10 fixed-point; mixed expressions are evaluated in double.

| # | Law | Equation (verbatim from lifted code) | Key address |
|---|---|---|---|
| 1 | Gravity (main map) | `v.z += −8.5·Δt` (vector rotated by `gravityAngle`) | `3d20f4`, `3d224e` |
| 2 | Air drag | `v_lat += −0.05·v_lat·Δt`; vertical undamped | `3d20f4` |
| 3 | Integrator | velocity first, then `c_move(v·Δt)`: semi-implicit Euler, once per frame, variable Δt | `3d224e`→`3d4e36`; `269b8c`→`269c3c` |
| 4 | Speed cap | `if |v|>300: v = 300·v̂` | `3d2c58`, `3eabdc` |
| 5 | Turn | `ω += (32·u − ω)·0.525·Δt/(1+√jumpTimer)`; rotate `ω·Δt` (air) or `ω·Δt/3` (ground) | `3cf32d`, `3cf7de` |
| 6 | Grounded velocity | `v = −24·n̂`; pivot on `Bone1` | `3cfe1b…3d004b`, `3cfdbe` |
| 7 | Impact | `I = 1.65·|v|^0.925` | `3dace6` |
| 8 | Spring window | `L_min = max(40, I^0.9)`, `L_max = clamp(I+20p+e, 95+25p+e, 300)` | `3db41d`, `3daff4` |
| 9 | Charge | `L = min(L + 16·Δt, L_max)` while `L < L_min` or Space held | `3dc019` |
| 10 | Launch speed | `|v₀| = 0.74235·L` (velocity replaced) | `3dd346` |
| 11 | Launch direction | `a = angle + 0.1875·clamp(ang(θₙ−90−angle), ±45)` | `3dd5c3` |
| 12 | Launch carry | `v.x += 0.25·slide.x`, `v.z += 0·slide.z` | `3dd775`, `3dd8ac` |
| 13 | Launch spin | `ω += 0.1245·clamp(ang(angle−gravityAngle),±45) − 0.375·sign(s)|s|^0.75` | `3e0185` |
| 14 | Post-launch | `noGroundContactTimer = 2`, `jumpTimer = 0.45·√L_max` | `3e0339`, `3e0438` |
| 15 | Power jump | air rotation `> 285°` → `p = 1` | `3ee58b` |
| 16 | Bonk | `d = norm(0.9·r̂ + n)`, `|v′| = max(28, 0.4|v|)·min(1+n.z,1)`, `v′.x·0.875`, `ω = 0.5·ang(gA−angle) − 0.3·sign(s)|s|^0.75` | `3e5867…3e61a4` |
| 17 | Ice slide | `slide += clamp((48·norm(n+g) − slide)·0.25, ±1.35)·Δt`; displacement gain x 1, z 4 | `3cbe3c`, `3d4c92` |
| 18 | Hull | x ±12.5, y ±32, z_max 30, z_min = −55 + max(ext, −12.25) | `3b3e09…`, `3eb19e` |
| 19 | Ground probe | box trace ±4 to `Bone1 + (6+|slide.z|)·stick̂` | `3d8544`, `1e500a` |
| 20 | Clock | `time_factor = 0.95`; `fps_max` default 120 (30–240) | `69c67a`, `4e7522` |
| 21 | Engine friction | `move_friction = 0` for the player | `3d30ff` |

---

## 10. UNRESOLVED PHYSICS (class D, or B where noted)

| Item | Why unresolved | What would resolve it |
|---|---|---|
| `c_move` internals: glide along surfaces, step-up, depenetration, how `hit`/`bounce`/`normal`/`target` are filled | Inside `acknex.dll` (not supplied) | `acknex.dll` disassembly, or the A8 manual (conitec.net is blocked from this environment) |
| Meaning of mode bits `548 (0x224)`, `0x40000`, `613 (0x265)`, `8192`, `1` | The `#define`s in `acknex.h` are not in the binary (only its path is) | `acknex.h` from a Gamestudio A8 install |
| How `time_step` is formed: `time_frame·time_factor`? smoothing (`time_smooth`, never referenced by the game)? clamping? | Engine-internal. That `time_factor` scales `time_step` is **B** (engine semantics, plus the game using `time_frame` for real-time timers and `time_step` for physics). | `acknex.dll`, or measurement (a C-class capture) |
| `vec_rotate` / tilt sign convention | Engine maths. Inferred **B** from code consistency (§7.3, §7.6). | `acknex.dll`, or one frame-capture |
| Geometry of `Bone1`/`Bone2` (stick length, tip offset from origin) | In the character/pogostick `.mdl` assets, which were not analysed (the `data.wrs` archive was not supplied) | Parse the model file |
| Exact value of `time_step` at 120 fps | Depends on the engine clock. Nominal `16/120·0.95 = 0.12667` T is **B**. | Capture |
| Per-entity surface flags (`skill[98]` bit meanings, `flags&64`) beyond the code paths shown | Set by level scripts and level files (`.wmb`) | Level files |
| Gamepad/Steam analog response curve inside `ackSuperkuGetAnalogActionData` | Steam Input runtime | Not needed for physics |

**Classes C and D:** no physics constant was assigned from gameplay estimation (class C) in this extraction. Every number in §6 and §9 is A, or B where the row says so.

---

## 11. RECONSTRUCTED PHYSICS (implementation recipe; equations A, engine glue B)

```text
per rendered frame (Δt = time_step in ticks; nominal 0.12667 at 120 fps with time_factor 0.95):
  timers -= Δt (stunned, noGroundContact, jumpTimer[air only]); if noGroundContactTimer>0: grounded=false
  slide update (§7.11)
  u = clamp(input_left - input_right, -1, 1)
  ω += (32*u - ω) * 0.525 * Δt / (1 + sqrt(jumpTimer))
  rotate body by ω*Δt*(grounded ? 1/3 : 1)            # with collision (c_rotate)
  if grounded: translate so pogo tip stays put; v = -24 * n
  else:        v.x += -0.05*v.x*Δt ; v.z += -8.5*Δt    # rotate by gravityAngle if ≠0
  if |v|>300: v = 300*v̂
  move with collision by (v + slide*(1,4))*Δt          # engine-friction 0
  probe from body origin to tip + (6+|slide.z|) along stick (box ±4) -> n, grounded
  if just landed: I = 1.65|v|^0.925 ; Lmin = max(40, I^0.9) ; Lmax = clamp(I+20p, 95+25p, 300)
  if grounded:
      if L < Lmin or (hold and L < Lmax): L = min(L + 16*Δt, Lmax)
      elif L > 2: launch:
          a = angle + 0.1875*clamp(ang(atan2(n.z,n.x)-90-angle), -45, 45)
          v = 0.74235*L * (-sin a, cos a) + (0.25*slide.x, 0)
          ω += 0.1245*clamp(ang(angle-gravityAngle),-45,45) - 0.375*sign(s)*|s|^0.75   (s = asin(n.x) in deg)
          L = 0 ; grounded = false ; noGroundContactTimer = 2 ; jumpTimer = 0.45*sqrt(Lmax) ; p = 0
  elif hit && contact near: bonk (§7.8)
  if |rotation since last jump| > 285°: p = 1
```

### 11.1 Derived figures (DERIVED from class-A equations; continuous-time approximations at Δt = 0.12667 T)

| Load L | v₀ (L/T) | Apex (quants) | Apex (game "m", ÷52) | Air time (game ticks → real s) |
|---|---|---|---|---|
| 40 (idle floor) | 29.69 | 50 | 0.96 | 6.97 T → 0.46 s |
| 95 (default max) | 70.52 | 288 | 5.54 | 16.6 T → 1.09 s |
| 120 (power-jump floor) | 89.08 | 461 | 8.87 | 20.9 T → 1.38 s |
| 200 | 148.47 | 1287 | 24.8 | 34.8 T → 2.29 s |
| 300 (cap) | 222.70 | 2903 | 55.8 | 52.3 T → 3.44 s |

**Passive bounce** (no Space): from a 95 jump, the launch speeds go 70.5 → 40.3 → **29.7 (stable idle hop, L_min = 40)**. Holding Space always allows at least `L_max ≥ 95`.

**Other derived rates:**
- Turn: max 32°/T = **512°/s game (486°/s real)** in the air, 171°/s on the ground; lag time constant 1/0.525 T = 0.119 s.
- Lateral drag half-life: ln2/0.05 = 13.9 T = 0.87 s.
- Charge time: 0→95 in 5.9 T (0.37 s); 0→300 in 18.75 T (1.17 s).

**Conversion to SI** (if the Android project keeps metres and seconds):
- Choose s_L (m per quant) and use **game seconds** = ticks/16 (multiply by 0.95 for wall-clock time if `time_factor` is honoured).
- g = 8.5·256·s_L m/s².
- Drag k = 0.05·16 = 0.8 s⁻¹.
- Launch speed = 0.74235·16·s_L·L m/s.
- Turn target = 512°/s.
- Charge rate = 256 load/s.
- With the game's own display unit (52 quants = 1 m): g = **41.85 m/s²**, v₀(95) = **21.70 m/s**, v₀(300) = **68.52 m/s**, max speed 300 L/T = **92.3 m/s**, stick press 24 L/T = 7.38 m/s.

The fixed-point (22.10) rounding of every stored value is part of the original behaviour. A float implementation will drift by at most ~1/1024 per stored quantity per frame.

---

## 12. Corrections to earlier assumptions

| Earlier assumption (docx / `PHYSICS_UNCERTAINTIES.md`) | Machine-code finding |
|---|---|
| `vec_bounce` = pure reflection drives the bounce | `vec_bounce` is **never called**. Bonk = `norm(0.9·r̂ + n)·max(28, 0.4|v|)`. Landing is **not** a restitution bounce at all: it goes through the spring (I → L_min/L_max → 0.74235·L). |
| `vec_accelerate`/`accelerate` give speed-proportional friction | **Never called.** Air drag is the explicit `−0.05·v_lat·Δt`. Engine `move_friction = 0`. |
| `jump_high` = top jump height; `jump_degrees` = launch/max tilt angle | Both are **Steam achievement ids**: ≥ 50 display-metres of height, and a ≈1080° rotation. |
| `Rays/tickRays/bRays` = ground rays | Visual materials. Ground detection is one `c_trace` box-ray along the stick. |
| `forceUp`, `jumpH`, `rootSpeed` | **Absent** from the program |
| `restitution e`, spring `k/c`, mass `m` | **Do not exist** in the original. The "spring" is a load counter (16/tick) mapped linearly to launch speed. |
| Fixed 1/120 s step | **Variable** per-frame step. `fps_max` defaults to 120 (user range 30–240) and `time_factor` is 0.95. |
| Max speed "if used" | **Used:** `|v| ≤ 300` L/T |

---

## 13. Method limits and ethics

- **Static analysis only.** No runtime capture was taken, so nothing here is *validated* against gameplay. Validation would mean a frame capture compared with §11.1.
- **No protection bypassed.** The overlay is LZSS-compressed, not encrypted. The licensee field was not decoded or published. The game's anti-tamper checks (`fuser(…)/sys_exit` paths) were only observed. No binary was modified.
- **Nothing original committed.** Neither the binaries nor the decoded image are in the repository. This report quotes only the minimal machine-code lines needed as evidence.
- **Originality rule still applies.** Physics equations and numeric parameters are functional facts. The Android project must still not copy the original code text, levels, models, textures, audio or branding.
