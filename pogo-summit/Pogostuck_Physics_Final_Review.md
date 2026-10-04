# Pogostuck — Final Physics Review (before IMPLEMENT)

> **Status:** review only. No game code and no Android project file was changed. The word IMPLEMENT has not been issued.
>
> **Inputs reviewed in full:**
> - `Pogostuck_Original_Physics_Extraction.md` (667 lines);
> - `Pogostuck_Physics_Constants.csv` (96 rows before this review, **99** after it).
>
> **Outputs of this review:**
> - this file;
> - the corrected CSV;
> - the corrected extraction report (§14 added there);
> - `Pogostuck_Physics_LOCKED_SPEC.md`.

---

## 1. How every value was re-verified

1. **Mechanical audit of every CSV row against the decoded image** (script `audit.py`). For each row:
   - the literal address equals `0x45 + Pos`;
   - the bytes at that address equal the recorded hex;
   - decoding those bytes gives the recorded value;
   - the instruction at the use address really reads `[ebx + Pos]`;
   - use and statement addresses lie inside the named function, with use ≤ statement.

   **Result: 99 / 99 rows pass, 0 failures.**
2. **Argument order checked on raw pushes** (cdecl: the first value pushed is the last argument):
   - `pow(|v|, 0.925)` @`3dac96`;
   - `pow(I, 0.9)` @`3db3b6`;
   - `clamp(I+20p+e, 95+25p+e, 300)` @`3daff4`;
   - `clamp(d, −45, 45)` @`3dd4c5` / `3dfe76`;
   - `clamp(x, −1.35, 1.35)` @`3cbe3c` / `3cc02a`;
   - `maxv(28, 0.4|v|)`, `minv(1+n_z, 1)`, `minv(L+16Δt, L_max)`, `maxv(40, ·)`;
   - `vec_normalize(·, 300 | 0.9 | L8c | 6+|s_z|)`.

   All match the documented equations.
3. **Storage precision checked** for values that matter:
   - the ground turn factor `1/(1+2g)` is stored as a **float** (`fstp dword`), so it is exactly 1/3 (float 0.33333334);
   - `time_factor = 0.95` is written as a **var** (`fmul 1024; fistp`) → integer 973 → **0.9501953125** (default FPU rounding to nearest);
   - all player state fields are var (22.10 fixed point).
4. **Execution context checked:**
   - `time_factor` is 0.95 in normal play (`mainFrameEventPlay` entry, `mainFrameEvent` when `mainFrameMode ≠ 5`) and 1 only in skate mode (`mainFrameEventSkate` @`69dc1f`);
   - the slow-motion `time_step` multipliers are undone after the player update (`*time_step = L324` @`69d0ca`);
   - `pogo_width_y` is never written (it stays at its initial 32);
   - the second speed cap @`3eabdc` is unconditional.

## 2. Corrections found in the previous deliverables

| # | Where | Problem | Correction |
|---|---|---|---|
| 1 | CSV column "ASSEMBLY EVIDENCE" | Lifted statements were **cut at 150 characters** (rows P11, P30, P31) | The CSV is regenerated with the **full** statement in `ASSEMBLY EVIDENCE` and a new `FULL_STATEMENT` column. Nothing is truncated. |
| 2 | Report §0 and §11 | Landing window written `L_max = clamp(I+20p, 95+25p, 300)`: **the `+e` term was missing** | `L_max = clamp(I + 20p + e, 95 + 25p + e, 300)`; `e = 0` in normal play |
| 3 | Report §11 recipe | Launch spin missing the factor `(1 − 0.5·special)` | Restored |
| 4 | Report §9 row 16 and the CSV P47 role | Wall bounce described as a "manual reflection". The machine code computes `v − 2(v·n)n` **only when `L98 = hit.flags & 0x600` is 0**. On the usual `c_move` hit (`L98 ≠ 0`) it uses the engine-written global `bounce`. | Direction source: engine `bounce` (**B**), with manual reflection as fallback (**A**). All magnitudes and gains stay **A**. |
| 5 | Report §9 row 16 | Missing `if n_z > 0: b_z = n_z·S`, the slide addition and the side effects (`powerJumpNext = 0`, `lastJumpAngle`, `L = 0`) | Full equation in §5 (E14) and in the LOCKED SPEC |
| 6 | Report §9 row 17 | Slide law shortened: missing the decay branch, the 0.25 stop threshold, the landing entry rule (`s_z = 0` on the main map) and the airborne reset | Full equation in E15 |
| 7 | CSV "CONFIDENCE" | Plain **A** was given to values whose *physical meaning* depends on engine routines (`vec_rotate` sign, `vec_lerp`, `ang` wrap, `sinv` degrees, bbox use, `bounce` vector, `time_factor` → `time_step`) | New columns `VALUE_CLASS` and `MEANING_CLASS`. 17 rows are now "A value / B meaning"; 3 engine-flag rows are "A value / D meaning". |
| 8 | CSV | Literals inside equations had no row of their own: `45` of the launch-spin clamp, the `0.5` special-collision factor, the `1`s in `min(1+n_z,1)` | Added as P40b, P43b, P73 (99 rows) |
| 9 | Report §6 / CSV T01 | `time_factor` given as 0.95 | Effective stored value **973/1024 = 0.9501953125** (var). Effect on `time_step` is **B**. |
| 10 | CSV U01–U04 | Labelled A like physics | Kept A as data, but marked `UNIT_DEF` / `NONPHYS`; they are not physics constants |
| 11 | CSV R01 role | "v_x ≤ 0, v_z ≤ 0" given without its condition | Applies only while the player is in the start area (`pos.x < −10624`) |

No numeric value in the previous deliverables was found to be wrong. All corrections concern truncation, missing terms and over-strong confidence labels.

## 3. Units used everywhere below

| Symbol | Unit | Meaning |
|---|---|---|
| Q | quant | engine length unit (the HUD shows 52 Q as 1 m, U01) |
| T | tick | Gamestudio time unit of `time_step` (nominally 1/16 s of game time) |
| L | load | dimensionless spring-load counter (`heroSpringLoaded`) |
| deg | degree | all Gamestudio angles |
| Δt | T | `time_step` of the current frame |

State: `v = arg0.speed`, `s = arg0.slideSpeed` (Q/T), `ω = arg0.heroTurnSpeed` (deg/T), `θ = arg0.angle` (deg), `γ = arg0.gravityAngle` (deg), `L = arg0.heroSpringLoaded`, `p = arg0.powerJumpNext`, `J = arg0.jumpTimer` (T), `N = arg0.noGroundContactTimer` (T), `g ∈ {0,1} = arg0.heroGroundContact`. All are var (22.10).

## 4. Classification summary

| Class | Count | Content |
|---|---|---|
| **CONFIRMED A** (value, operation and meaning from game code alone) | **79** | §6 below. Of these: 47 core main-map physics, 12 level-specific, 7 mode-specific, 4 timestep, 2 corroboration, 4 unit/non-physics, plus 3 correction/anti-stuck. |
| **INFERRED B** (value and operation A; meaning needs a documented engine routine) | **17** | §7 below |
| **ESTIMATED C** | **0** | No value was estimated from gameplay |
| **UNKNOWN D** | 3 constants with an A value but unknown meaning (engine flags), plus the engine items in §9 | §8 and §9 below |

## 5. Topic-by-topic verification with complete equations (items 5, 6 and 9 of the request)

| Topic | Verdict | Complete equation (all terms) | Evidence |
|---|---|---|---|
| **Gravity** | A | (E1) `a = R_γ · ( −0.05·(v·ê)·Δt , 0 , −8.5·Δt )`, `ê = R_γ·x̂`, `v ← v + a`. On the main map γ = 0: **v_z ← v_z − 8.5·Δt**. | P01, `3d20f4`→`3d224e`; corroborated by P69 |
| **Drag** | A | Main map: **v_x ← v_x − 0.05·v_x·Δt**. No drag on v_z. | P02 |
| **Max speed** | A | (E2) **if \|v\| > 300: v ← 300·v/\|v\|**, applied after the gravity step and again after platform release | P09, `3d2c58`, `3eabdc` |
| **Charge** | A | (E8) on a grounded frame: **if (L < L_min + 20·outro ∨ (hold ∧ ¬outro)) ∧ L < L_max then L ← min(L + 16·Δt, L_max)**; else **if L > 2 then launch**. Not grounded ⇒ **L ← 0**. (`hold` = button 4, default Space.) | P32, P33, P30, P31, `3d94a8` |
| **Launch multiplier** | A | (E9) **\|V\| = 0.74235·L** (Q/T), `V = R_a·(0, 0, 0.74235·L)` | P34 |
| **Jump angle** | A (B for the sin/cos sign) | (E9) **δ = clamp(wrap180(θ_n − 90 − θ), −45, 45)**, θ_n = atan2(n_z, n_x); **a = θ + 0.1875·δ**; V = 0.74235·L·(−sin a, cos a) [B]; **v ← (V_x + 0.25·s_x, V_z + 0·s_z)** (assignment). | P35–P39 |
| **Landing force** | A | (E7) on the frame g goes 0→1: **I = 1.65·\|v\|^0.925**; **L_min = max(40, I^0.9)**; **L_max = clamp(I + 20p + e, 95 + 25p + e, 300)**, e = 0 in normal play | P23–P29, P25a |
| **Ground pressure** | A magnitude, B direction | (E5) grounded: **v ← −24·n** (= R_{θ_n}·(−24, 0, 0)); then tip pivot: if \|Δtip_x\| < 256 ∧ \|Δtip_z\| < 256, translate the body by Δtip = tip_prev − tip | P14, P19 |
| **Wall bounce** | A magnitudes, B direction source | (E14) **r̂** = engine `bounce` (usual) or **r = v − 2(v·n)n** (fallback); r̂_y = 0; **d = 0.9·r̂/\|r̂\| + n**; **S = max(28, 0.4·\|v\|)·min(1 + n_z, 1)**; **b = S·d/\|d\|**; **if n_z > 0: b_z = n_z·S**; **v ← (0.875·b_x + s_x, b_z + s_z)**; **ω ← 0.5·wrap180(γ − θ) − 0.3·sgn(σ)·\|σ\|^0.75**, σ = asin(n_x) in degrees; **N ← 2, p ← 0, L ← 0, θ ← wrap180(θ), θ_lastJump ← θ**; in water J ← 4. | P47–P54, P73 |
| **Boost threshold** | A | (E13) airborne: **if int(\|θ − θ_lastJump\|) > 285 ∧ p < 1 then p ← 1**. Effect at the next landing: L_max gets +20, floor 120. Reset to 0 after the launch (`3e19be`) and on a wall bounce. | P46, P25a, P26 |
| **Timestep** | A (structure), B (time_step formation) | (E19) one `playerMove` per rendered frame; **Δt = engine time_step (T)**, variable; `time_factor ← 973/1024` each play frame; `fps_max` = 120 by default (user 30–240); no fixed-step accumulator in game code. Android choice Δt = 16·Δt_real·time_factor is **B**. | T01–T05, `69ceda` |
| **Collision dimensions** | A values, B use | (E18) **x ∈ [−12.5, 12.5]**, **y ∈ [−32, 32]**, **z_max = 30**, **z_min = −55 + max(X, −12.25)**, X = 18·sin(0.9·P_b) (deg); if X < 0: X ← X·(0.01·P_max)·(200 + X)·0.0025. While charging P_b = P_max = L; airborne P_b ← max(P_b − 120·Δt, −200). Updated only when N = 0. Ground probe: box ±4 Q. | P62–P68, P22 |
| **Ice sliding** | A (B for the lerp decay) | (E15) Entry: landing on a contact entity with flag 64 ⇒ slide mode on, **s_x ← v_x**, **s_z ← 0** (main map). In slide mode: **t = 48·normalize(n + R_γ·(0,0,−1))**; per axis **s_i ← s_i + clamp(0.25·(t_i − s_i), −1.35, 1.35)·Δt**. Out of slide mode: **if \|s\| < 0.25 ∨ ¬g then s ← 0 else s ← s·(1 − 0.5·Δt)** [B]. Slide mode cleared when airborne. Displacement: **d = ((v_x + s_x)·Δt, (v_z + 4·s_z)·Δt)**. Launch keeps 0.25·s_x. | P55, P57–P60, P72, P11, P38 |

Other complete equations used by the LOCKED SPEC:
- **(E3) Displacement:** `d_x = (v_x + s_x)·Δt`, `d_z = (v_z + 4·s_z)·Δt` (normal play: L3a8 = Lb210 = 1, no extra or grapple), applied by a collision move.
- **(E4) Turn:**
  - `Lb = 1/(1 + 2g)`;
  - `ω_t = 32·(u_left − u_right)`;
  - `ω ← ω + (ω_t − ω)·0.525·Δt/(1 + √J)`;
  - `θ ← θ + Lb·ω·Δt` (collision-checked rotation).
- **(E6) Ground probe:** `end = tip + (6 + |s_z|)·(tip − top)/|tip − top|`; box ±4 trace from the body origin.
  - Grounded = hit ∧ move-hit ∧ entity filter.
  - It is forced to 0 when the probe misses, while stunned, while N > 0, or in water.
- **(E10) Launch spin:** `ω ← ω + (0.1245·clamp(wrap180(θ − γ), −45, 45) − 0.25·1.5·sgn(σ)·|σ|^0.75)·(1 − 0.5·special)`.
- **(E11) After launch:** `L_last ← L`, `L ← 0`, `g ← 0`, `N ← 2`, `J ← 0.45·√L_max`, then `p ← 0`.
- **(E12) Timers each frame:** `stunned ← max(stunned − Δt, 0)`; `N ← max(N − Δt, 0)` (and `g ← 0` if N > 0 or in water); `J ← 0` if g else `max(J − Δt, 0)`; slide mode off when airborne.
- **(E16) Ledge exit:** grounded at the start of the probe and probe misses ⇒ `v ← (s_x, 5)`.
- **(E17) Platform release:** airborne and lat ≠ 0 ⇒ `v_x += lat_x`, `v_z += lat_z·(1 − 0.75·[lat_z < 0])`, then the cap.

---

## 6. CONFIRMED A — every constant (value, operation and meaning from compiled game code)

### Gravity and air drag

#### P01 — `GRAVITY_ACCEL`

| Field | Value |
|---|---|
| Full value | source literal `-8.5`; stored IEEE-754 double `-8.5` (`0xc021000000000000`) |
| Unit | Q/T² |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba1f5` = data base `0x45` + Pos `0xba1b0` (`@const_135341`) |
| Instruction address | IMG `0x3d2080` |
| Function | `playerMove` (statement ends @IMG `0x3d20f4`) |
| Lifted statement (full) | `r580 = vector((-0.05 * L12c) * (*time_step), 0, -8.5 * (*time_step))` |
| All literals in this statement | `-0.05`@3d1ffe, `-8.5`@3d2080, `0`@3d20b5 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d2078  fdiv qword ptr [eax]
3d207a  fstp qword ptr [ebp + 0x55fc]
3d2080  fld qword ptr [ebx + 0xba1b0] ; @const_135341{DOUBLE=-8.5 init=-8.5 hex=00000000000021c0}  <==
3d2086  fmul qword ptr [ebp + 0x55fc]
3d208c  fstp qword ptr [ebp + 0x5604]
3d2092  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3d209f  pop eax
3d20a0  fld qword ptr [ebp + 0x5604]
3d20a6  fmul qword ptr [eax]
```

**Math.** x87: fld −8.5 → fmul time_step → ×1024 → fistp ⇒ the var −8.5·Δt (rounded to 1/1024) becomes the z component of L4cc = vector(−0.05·v_lat·Δt, 0, −8.5·Δt). L4cc is rotated by (0,gravityAngle,0) (identity on the main map, gravityAngle = 0 @3ce025) and vec_add to arg0.speed @3d224e. Result: v_z ← v_z − 8.5·Δt.

#### P02 — `AIR_DRAG_LATERAL`

| Field | Value |
|---|---|
| Full value | source literal `-0.05`; stored IEEE-754 double `-0.05000000074505806` (`0xbfa99999a0000000`) |
| Unit | 1/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba1ed` = data base `0x45` + Pos `0xba1a8` (`@const_135335`) |
| Instruction address | IMG `0x3d1ffe` |
| Function | `playerMove` (statement ends @IMG `0x3d20f4`) |
| Lifted statement (full) | `r580 = vector((-0.05 * L12c) * (*time_step), 0, -8.5 * (*time_step))` |
| All literals in this statement | `-0.05`@3d1ffe, `-8.5`@3d2080, `0`@3d20b5 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d1ff6  fdiv qword ptr [eax]
3d1ff8  fstp qword ptr [ebp + 0x55d4]
3d1ffe  fld qword ptr [ebx + 0xba1a8] ; @const_135335{DOUBLE=-0.05000000074505806 init=-0.05 hex=000000a09999a9bf}  <==
3d2004  fmul qword ptr [ebp + 0x55d4]
3d200a  fstp qword ptr [ebp + 0x55dc]
3d2010  mov ecx, 4
3d2015  mov esi, dword ptr [ebx + 0x998c0] ; time_step<POINTER>
3d201b  mov eax, dword ptr [esi]
3d201d  mov dword ptr [ebp + 0x55e4], eax
```

**Math.** x87: fld −0.05 → fmul L12c (= vec_dot(ê, arg0.speed), ê = vec_rotate((1,0,0),(0,gravityAngle,0)) = x̂ on the main map) → fmul time_step ⇒ x component of L4cc. After vec_add: v_x ← v_x − 0.05·v_x·Δt. The z component has no drag term.

### Maximum speed

#### P09 — `MAX_SPEED`

| Field | Value |
|---|---|
| Full value | integer `300` (`0x0000012c`); entered as var `300·1024 = 307200` where shifted (`shl 10`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9ca9` = data base `0x45` + Pos `0xb9c64` (`@const_131759`) |
| Instruction address | IMG `0x3d2bc8` |
| Function | `playerMove` (statement ends @IMG `0x3d2bf9`) |
| Lifted statement (full) | `if !(r594 > 300) goto label_3d2c60` |
| All literals in this statement | `300`@3d2bc8 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d2bbc  add esp, 4
3d2bc2  mov dword ptr [ebp + 0x57f8], eax
3d2bc8  mov eax, dword ptr [ebx + 0xb9c64] ; @const_131759{LONG=300 init=300 hex=2c010000}  <==
3d2bce  shl eax, 0xa
3d2bd1  mov dword ptr [ebp + 0x57fc], eax
3d2bd7  mov ecx, dword ptr [ebp + 0x57f8]
3d2bdd  mov edx, dword ptr [ebp + 0x57fc]
3d2be3  xor eax, eax
3d2be5  cmp ecx, edx
```

**Math.** vec_length(arg0.speed) compared with 300 (int → var); if |v| > 300 then vec_normalize(arg0.speed, 300) rescales v to length 300 keeping its direction. Applied @3d2c58 (after the gravity step) and again @3eabdc (after platform carry).

### Displacement gains

#### P11 — `SLIDE_Z_DISPLACEMENT_GAIN`

| Field | Value |
|---|---|
| Full value | integer `4` (`0x00000004`); entered as var `4·1024 = 4096` where shifted (`shl 10`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b99dd` = data base `0x45` + Pos `0xb9998` (`@const_130436`) |
| Instruction address | IMG `0x3d4a3a` |
| Function | `playerMove` (statement ends @IMG `0x3d4c92`) |
| Lifted statement (full) | `*L5d00 = (((arg0.speed.z * (*time_step)) + (((arg0.slideSpeed.z * 4) * L3a8) * (*time_step))) + ((playerMoveExtraSpeed.z * L254) * (*time_step))) + (playerMoveExtraSpeedGrapple.z * (*time_step))` |
| All literals in this statement | `4`@3d4a3a |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d4a32  mov eax, dword ptr [esi]
3d4a34  mov dword ptr [ebp + 0x5d20], eax
3d4a3a  mov eax, dword ptr [ebx + 0xb9998] ; @const_130436{LONG=4 init=4 hex=04000000}  <==
3d4a40  shl eax, 0xa
3d4a43  mov dword ptr [ebp + 0x5d24], eax
3d4a49  mov eax, dword ptr [ebp + 0x5d20]
3d4a4f  mov edx, dword ptr [ebp + 0x5d24]
3d4a55  imul edx
3d4a57  shrd eax, edx, 0xa
```

**Math.** d.z = (v_z + 4·slide_z·L3a8 + extraZ·L254 + grappleZ)·Δt. imul by the int 4. The x row uses factor 1: d.x = Lb210·(v_x + slide_x·L3a8)·Δt + extraX·L254·Δt + grappleX·Δt.

### Turning

#### P15 — `GROUND_TURN_DIVISOR`

| Field | Value |
|---|---|
| Full value | integer `2` (`0x00000002`); entered as var `2·1024 = 2048` where shifted (`shl 10`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9965` = data base `0x45` + Pos `0xb9920` (`@const_130373`) |
| Instruction address | IMG `0x3cea7d` |
| Function | `playerMove` (statement ends @IMG `0x3ceacc`) |
| Lifted statement (full) | `Lb1c4 = 1.0 / (1.0 + ((!(!(arg0.heroGroundContact))) * 2))` |
| All literals in this statement | `2`@3cea7d, `1.0`@3cea96, `1.0`@3ceaa8 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3cea71  mov dword ptr [ebp + 0x4cb4], eax
3cea77  mov eax, dword ptr [ebp + 0x4cb4]
3cea7d  imul eax, dword ptr [ebx + 0xb9920] ; @const_130373{LONG=2 init=2 hex=02000000}  <==
3cea84  mov dword ptr [ebp + 0x4cb8], eax
3cea8a  fild dword ptr [ebp + 0x4cb8]
3cea90  fstp qword ptr [ebp + 0x4cbc]
3cea96  fld qword ptr [ebx + 0xb9b74] ; @const_131371{DOUBLE=1.0 init=1.0 hex=000000000000f03f}
3cea9c  fadd qword ptr [ebp + 0x4cbc]
3ceaa2  fstp qword ptr [ebp + 0x4cc4]
```

**Math.** imul heroGroundContact by 2, fadd 1.0, fdiv into 1.0, stored as FLOAT: Lb1c4 = 1/(1 + 2·g), g ∈ {0,1} ⇒ 1 in the air, 1/3 (float 0.33333334) on the ground. Multiplies the applied rotation.

#### P16 — `TURN_TARGET_RATE`

| Field | Value |
|---|---|
| Full value | integer `32` (`0x00000020`); entered as var `32·1024 = 32768` where shifted (`shl 10`) |
| Unit | deg/T per unit input |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9b39` = data base `0x45` + Pos `0xb9af4` (`@const_130964`) |
| Instruction address | IMG `0x3ceb16` |
| Function | `playerMove` (statement ends @IMG `0x3cebae`) |
| Lifted statement (full) | `L438 = ((r518 - r519) * 32) * (1 - (2 * inputPlayerTurnInverted))` |
| All literals in this statement | `2`@3cead2, `3`@3ceaea, `32`@3ceb16, `2`@3ceb40, `1`@3ceb6a |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3ceb0e  sub eax, ecx
3ceb10  mov dword ptr [ebp + 0x4ce0], eax
3ceb16  mov eax, dword ptr [ebx + 0xb9af4] ; @const_130964{LONG=32 init=32 hex=20000000}  <==
3ceb1c  shl eax, 0xa
3ceb1f  mov dword ptr [ebp + 0x4ce4], eax
3ceb25  mov eax, dword ptr [ebp + 0x4ce0]
3ceb2b  mov edx, dword ptr [ebp + 0x4ce4]
3ceb31  imul edx
3ceb33  shrd eax, edx, 0xa
```

**Math.** L438 = (inputGetValue(2) − inputGetValue(3))·32·(1 − 2·inputPlayerTurnInverted): the target turn rate. Keyboard values are 0 or 1, so the target is ±32 deg/T.

#### P17 — `TURN_RESPONSE_RATE`

| Field | Value |
|---|---|
| Full value | source literal `0.525`; stored IEEE-754 double `0.5249999761581421` (`0x3fe0ccccc0000000`) |
| Unit | 1/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba14d` = data base `0x45` + Pos `0xba108` (`@const_134836`) |
| Instruction address | IMG `0x3cf1e9` |
| Function | `playerMove` (statement ends @IMG `0x3cf32d`) |
| Lifted statement (full) | `arg0.heroTurnSpeed = arg0.heroTurnSpeed + ((((L438 - arg0.heroTurnSpeed) * 0.525) / (1.0 + r524)) * (*time_step))` |
| All literals in this statement | `0.525`@3cf1e9, `1.0`@3cf274 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3cf1dd  fstp qword ptr [ebp + 0x4e28]
3cf1e3  fld qword ptr [ebp + 0x4e28]
3cf1e9  fmul qword ptr [ebx + 0xba108] ; @const_134836{DOUBLE=0.5249999761581421 init=0.525 hex=000000c0cccce03f}  <==
3cf1ef  fstp qword ptr [ebp + 0x4e30]
3cf1f5  mov eax, dword ptr [ebp]
3cf1fb  mov dword ptr [ebp + 0x4e38], eax
3cf201  mov eax, dword ptr [ebp + 0x4e38]
3cf207  mov ecx, dword ptr [ebx + 0xb9f08] ; @const_133215{LONG=369 init=OFFSET:STRUCT@94:jumpTimer hex=71010000}
3cf20d  add eax, ecx
```

**Math.** fmul 0.525 on (L438 − ω), fdiv by (1.0 + sqrt(jumpTimer)), fmul Δt, add: ω ← ω + (ω_target − ω)·0.525·Δt/(1 + √jumpTimer). First-order lag toward the target.

### Ground pressure and tip pivot

#### P19 — `PIVOT_MAX_CORRECTION`

| Field | Value |
|---|---|
| Full value | integer `256` (`0x00000100`); entered as var `256·1024 = 262144` where shifted (`shl 10`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba16d` = data base `0x45` + Pos `0xba128` (`@const_134965`) |
| Instruction address | IMG `0x3cfb29` |
| Function | `playerMove` (statement ends @IMG `0x3cfbee`) |
| Lifted statement (full) | `if !((r535 < 256) && (r536 < 256)) goto label_3cfdc6` |
| All literals in this statement | `256`@3cfb29, `256`@3cfba0 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3cfb1d  add esp, 4
3cfb23  mov dword ptr [ebp + 0x4f8c], eax
3cfb29  mov eax, dword ptr [ebx + 0xba128] ; @const_134965{LONG=256 init=256 hex=00010000}  <==
3cfb2f  shl eax, 0xa
3cfb32  mov dword ptr [ebp + 0x4f90], eax
3cfb38  mov ecx, dword ptr [ebp + 0x4f8c]
3cfb3e  mov edx, dword ptr [ebp + 0x4f90]
3cfb44  xor eax, eax
3cfb46  cmp ecx, edx
```

**Math.** Grounded: L460 = prevBone1Pos − Bone1 (after rotation). If |L460.x| < 256 and |L460.z| < 256: c_move(me, nullvector, (L460.x, 0, L460.z)) ⇒ the pogo tip is put back where it was: rotation pivots on the tip.

### Ground probe

#### P20 — `GROUND_PROBE_EXTENSION`

| Field | Value |
|---|---|
| Full value | integer `6` (`0x00000006`); entered as var `6·1024 = 6144` where shifted (`shl 10`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9d55` = data base `0x45` + Pos `0xb9d10` (`@const_132256`) |
| Instruction address | IMG `0x3d81fb` |
| Function | `playerMove` (statement ends @IMG `0x3d823f`) |
| Lifted statement (full) | `r665 = vec_normalize(ADDR(L460), 6 + r664)` |
| All literals in this statement | `6`@3d81fb |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d81ef  add esp, 4
3d81f5  mov dword ptr [ebp + 0x659c], eax
3d81fb  mov eax, dword ptr [ebx + 0xb9d10] ; @const_132256{LONG=6 init=6 hex=06000000}  <==
3d8201  shl eax, 0xa
3d8204  mov dword ptr [ebp + 0x65a0], eax
3d820a  mov eax, dword ptr [ebp + 0x65a0]
3d8210  mov ecx, dword ptr [ebp + 0x659c]
3d8216  add eax, ecx
3d8218  mov dword ptr [ebp + 0x65a4], eax
```

**Math.** L460 = Bone1 − Bone2, vec_normalize(L460, 6 + |slideSpeed.z|), += Bone1 ⇒ probe end is 6 + |slide_z| Q beyond the tip along the stick axis.

### Landing force (impact) and spring window

#### P23 — `IMPACT_EXPONENT`

| Field | Value |
|---|---|
| Full value | source literal `0.925`; stored IEEE-754 double `0.925000011920929` (`0x3fed9999a0000000`) |
| Unit | exponent |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba361` = data base `0x45` + Pos `0xba31c` (`@const_136749`) |
| Instruction address | IMG `0x3dac5f` |
| Function | `playerMove` (statement ends @IMG `0x3dac96`) |
| Lifted statement (full) | `r700 = pow(r699, 0.925)` |
| All literals in this statement | `0.925`@3dac5f |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dac58  mov eax, dword ptr [ebx + 0xba320] ; @const_136749+4<DOUBLE>
3dac5e  push eax
3dac5f  mov eax, dword ptr [ebx + 0xba31c] ; @const_136749{DOUBLE=0.925000011920929 init=0.925 hex=000000a09999ed3f}  <==
3dac65  push eax
3dac66  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3dac73  pop eax
3dac74  fild dword ptr [ebp + 0x6be8]
3dac7a  fdiv qword ptr [eax]
3dac7c  fstp qword ptr [ebp + 0x6bec]
```

**Math.** Pushed as the 2nd argument of pow (cdecl: pushed first = last argument): r700 = pow(|v|, 0.925), |v| = vec_length(arg0.speed) on the landing frame.

#### P24 — `IMPACT_GAIN`

| Field | Value |
|---|---|
| Full value | source literal `1.65`; stored IEEE-754 double `1.649999976158142` (`0x3ffa666660000000`) |
| Unit | L per (Q/T)^0.925 |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba369` = data base `0x45` + Pos `0xba324` (`@const_136752`) |
| Instruction address | IMG `0x3dacb8` |
| Function | `playerMove` (statement ends @IMG `0x3dace6`) |
| Lifted statement (full) | `L3e0 = r700 * 1.65` |
| All literals in this statement | `0.925`@3dac5f, `1.65`@3dacb8 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dacb1  pop edx
3dacb2  fld qword ptr [ebp + 0x6bf4]
3dacb8  fmul qword ptr [ebx + 0xba324] ; @const_136752{DOUBLE=1.649999976158142 init=1.65 hex=000000606666fa3f}  <==
3dacbe  fstp qword ptr [ebp + 0x6bfc]
3dacc4  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3dacd1  pop eax
3dacd2  fld qword ptr [ebp + 0x6bfc]
3dacd8  fmul qword ptr [eax]
3dacda  fistp dword ptr [ebp + 0x6c04]
```

**Math.** fmul 1.65 ⇒ I = 1.65·|v|^0.925 (stored as var L3e0 and playerHeroImpactSpeedTrue).

#### P25a — `IMPACT_POWERJUMP_BONUS`

| Field | Value |
|---|---|
| Full value | integer `20` (`0x00000014`); entered as var `20·1024 = 20480` where shifted (`shl 10`) |
| Unit | L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9de5` = data base `0x45` + Pos `0xb9da0` (`@const_132449`) |
| Instruction address | IMG `0x3daeac` |
| Function | `playerMove` (statement ends @IMG `0x3daf10`) |
| Lifted statement (full) | `playerHeroImpactSpeedCalc = (L3e0 + (20 * arg0.powerJumpNext)) + L2c` |
| All literals in this statement | `20`@3daeac |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3daea4  mov eax, dword ptr [esi]
3daea6  mov dword ptr [ebp + 0x6c44], eax
3daeac  mov eax, dword ptr [ebx + 0xb9da0] ; @const_132449{LONG=20 init=20 hex=14000000}  <==
3daeb2  shl eax, 0xa
3daeb5  mov dword ptr [ebp + 0x6c48], eax
3daebb  mov eax, dword ptr [ebp + 0x6c48]
3daec1  mov edx, dword ptr [ebp + 0x6c44]
3daec7  imul edx
3daec9  shrd eax, edx, 0xa
```

**Math.** imul 20 · powerJumpNext: term of I + 20·p + e (also stored as playerHeroImpactSpeedCalc). The same expression is the 1st argument of the clamp @3daff4.

#### P25 — `SPRING_MAX_FLOOR`

| Field | Value |
|---|---|
| Full value | integer `95` (`0x0000005f`); entered as var `95·1024 = 97280` where shifted (`shl 10`) |
| Unit | L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba371` = data base `0x45` + Pos `0xba32c` (`@const_136778`) |
| Instruction address | IMG `0x3daf93` |
| Function | `playerMove` (statement ends @IMG `0x3daff4`) |
| Lifted statement (full) | `r701 = clamp((L3e0 + (20 * arg0.powerJumpNext)) + L2c, (95 + (25 * arg0.powerJumpNext)) + L2c, 300)` |
| All literals in this statement | `25`@3daf69, `95`@3daf93, `300`@3dafca |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3daf8a  adc eax, 0
3daf8d  mov dword ptr [ebp + 0x6c68], eax
3daf93  mov eax, dword ptr [ebx + 0xba32c] ; @const_136778{LONG=95 init=95 hex=5f000000}  <==
3daf99  shl eax, 0xa
3daf9c  mov dword ptr [ebp + 0x6c6c], eax
3dafa2  mov eax, dword ptr [ebp + 0x6c6c]
3dafa8  mov ecx, dword ptr [ebp + 0x6c68]
3dafae  add eax, ecx
3dafb0  mov dword ptr [ebp + 0x6c70], eax
```

**Math.** 2nd clamp argument (lower bound): L_max = clamp(I + 20p + e, 95 + 25p + e, 300).

#### P26 — `SPRING_MAX_FLOOR_POWER_BONUS`

| Field | Value |
|---|---|
| Full value | integer `25` (`0x00000019`); entered as var `25·1024 = 25600` where shifted (`shl 10`) |
| Unit | L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9875` = data base `0x45` + Pos `0xb9830` (`@const_130224`) |
| Instruction address | IMG `0x3daf69` |
| Function | `playerMove` (statement ends @IMG `0x3daff4`) |
| Lifted statement (full) | `r701 = clamp((L3e0 + (20 * arg0.powerJumpNext)) + L2c, (95 + (25 * arg0.powerJumpNext)) + L2c, 300)` |
| All literals in this statement | `25`@3daf69, `95`@3daf93, `300`@3dafca |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3daf61  mov eax, dword ptr [esi]
3daf63  mov dword ptr [ebp + 0x6c60], eax
3daf69  mov eax, dword ptr [ebx + 0xb9830] ; @const_130224{LONG=25 init=25 hex=19000000}  <==
3daf6f  shl eax, 0xa
3daf72  mov dword ptr [ebp + 0x6c64], eax
3daf78  mov eax, dword ptr [ebp + 0x6c64]
3daf7e  mov edx, dword ptr [ebp + 0x6c60]
3daf84  imul edx
3daf86  shrd eax, edx, 0xa
```

**Math.** imul 25 · powerJumpNext inside the lower bound 95 + 25p + e.

#### P27 — `SPRING_MAX_CAP`

| Field | Value |
|---|---|
| Full value | integer `300` (`0x0000012c`); entered as var `300·1024 = 307200` where shifted (`shl 10`) |
| Unit | L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9ca9` = data base `0x45` + Pos `0xb9c64` (`@const_131759`) |
| Instruction address | IMG `0x3dafca` |
| Function | `playerMove` (statement ends @IMG `0x3daff4`) |
| Lifted statement (full) | `r701 = clamp((L3e0 + (20 * arg0.powerJumpNext)) + L2c, (95 + (25 * arg0.powerJumpNext)) + L2c, 300)` |
| All literals in this statement | `25`@3daf69, `95`@3daf93, `300`@3dafca |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dafc2  add eax, ecx
3dafc4  mov dword ptr [ebp + 0x6c74], eax
3dafca  mov eax, dword ptr [ebx + 0xb9c64] ; @const_131759{LONG=300 init=300 hex=2c010000}  <==
3dafd0  shl eax, 0xa
3dafd3  mov dword ptr [ebp + 0x6c78], eax
3dafd9  mov eax, dword ptr [ebp + 0x6c78]
3dafdf  push eax
3dafe0  mov eax, dword ptr [ebp + 0x6c74]
3dafe6  push eax
```

**Math.** 3rd clamp argument (upper bound) of L_max.

#### P28 — `SPRING_MIN_EXPONENT`

| Field | Value |
|---|---|
| Full value | source literal `0.9`; stored IEEE-754 double `0.8999999761581421` (`0x3fecccccc0000000`) |
| Unit | exponent |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9cfd` = data base `0x45` + Pos `0xb9cb8` (`@const_132070`) |
| Instruction address | IMG `0x3db37f` |
| Function | `playerMove` (statement ends @IMG `0x3db3b6`) |
| Lifted statement (full) | `r705 = pow(L3e0, 0.9)` |
| All literals in this statement | `0.9`@3db37f |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3db378  mov eax, dword ptr [ebx + 0xb9cbc] ; @const_132070+4<DOUBLE>
3db37e  push eax
3db37f  mov eax, dword ptr [ebx + 0xb9cb8] ; @const_132070{DOUBLE=0.8999999761581421 init=0.9 hex=000000c0ccccec3f}  <==
3db385  push eax
3db386  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3db393  pop eax
3db394  fild dword ptr [ebp + 0x3e0]
3db39a  fdiv qword ptr [eax]
3db39c  fstp qword ptr [ebp + 0x6cfc]
```

**Math.** heroSpringLoadedMin = pow(I, 0.9) (0.9 is the 2nd pow argument).

#### P29 — `SPRING_MIN_FLOOR`

| Field | Value |
|---|---|
| Full value | integer `40` (`0x00000028`); entered as var `40·1024 = 40960` where shifted (`shl 10`) |
| Unit | L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9ba9` = data base `0x45` + Pos `0xb9b64` (`@const_131357`) |
| Instruction address | IMG `0x3db401` |
| Function | `playerMove` (statement ends @IMG `0x3db41d`) |
| Lifted statement (full) | `r706 = maxv(40, heroSpringLoadedMin)` |
| All literals in this statement | `40`@3db401 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3db3fa  mov eax, dword ptr [ebx + 0x7d9ec] ; heroSpringLoadedMin<FIXED>
3db400  push eax
3db401  mov eax, dword ptr [ebx + 0xb9b64] ; @const_131357{LONG=40 init=40 hex=28000000}  <==
3db407  shl eax, 0xa
3db40a  mov dword ptr [ebp + 0x6d10], eax
3db410  mov eax, dword ptr [ebp + 0x6d10]
3db416  push eax
3db417  mov eax, dword ptr [ebx + 0x90730] ; maxv<POINTER>
3db41d  call eax
```

**Math.** heroSpringLoadedMin = maxv(40, heroSpringLoadedMin) ⇒ L_min = max(40, I^0.9).

### Charge

#### P32 — `SPRING_CHARGE_RATE`

| Field | Value |
|---|---|
| Full value | integer `16` (`0x00000010`); entered as var `16·1024 = 16384` where shifted (`shl 10`) |
| Unit | L/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9d4d` = data base `0x45` + Pos `0xb9d08` (`@const_132243`) |
| Instruction address | IMG `0x3dbf94` |
| Function | `playerMove` (statement ends @IMG `0x3dc019`) |
| Lifted statement (full) | `r714 = minv(arg0.heroSpringLoaded + (16 * (*time_step)), arg0.heroSpringLoadedMax)` |
| All literals in this statement | `16`@3dbf94 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dbf8c  mov eax, dword ptr [esi]
3dbf8e  mov dword ptr [ebp + 0x6ed0], eax
3dbf94  mov eax, dword ptr [ebx + 0xb9d08] ; @const_132243{LONG=16 init=16 hex=10000000}  <==
3dbf9a  shl eax, 0xa
3dbf9d  mov dword ptr [ebp + 0x6ed4], eax
3dbfa3  mov eax, dword ptr [ebp + 0x6ed4]
3dbfa9  mov edx, dword ptr [ebp + 0x6ed0]
3dbfaf  imul edx
3dbfb1  shrd eax, edx, 0xa
```

**Math.** imul 16·Δt (var product), add heroSpringLoaded, minv with heroSpringLoadedMax: L ← min(L + 16·Δt, L_max).

#### P33 — `LAUNCH_MIN_LOAD`

| Field | Value |
|---|---|
| Full value | integer `2` (`0x00000002`); entered as var `2·1024 = 2048` where shifted (`shl 10`) |
| Unit | L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9965` = data base `0x45` + Pos `0xb9920` (`@const_130373`) |
| Instruction address | IMG `0x3dc310` |
| Function | `playerMove` (statement ends @IMG `0x3dc341`) |
| Lifted statement (full) | `if !(arg0.heroSpringLoaded > 2) goto label_3e29db` |
| All literals in this statement | `2`@3dc310 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dc308  mov eax, dword ptr [esi]
3dc30a  mov dword ptr [ebp + 0x6f5c], eax
3dc310  mov eax, dword ptr [ebx + 0xb9920] ; @const_130373{LONG=2 init=2 hex=02000000}  <==
3dc316  shl eax, 0xa
3dc319  mov dword ptr [ebp + 0x6f60], eax
3dc31f  mov ecx, dword ptr [ebp + 0x6f5c]
3dc325  mov edx, dword ptr [ebp + 0x6f60]
3dc32b  xor eax, eax
3dc32d  cmp ecx, edx
```

**Math.** if heroSpringLoaded > 2 and the charge condition is false ⇒ launch. Otherwise nothing happens this frame.

### Jump launch

#### P34 — `LAUNCH_SPEED_PER_LOAD`

| Field | Value |
|---|---|
| Full value | source literal `0.74235`; stored IEEE-754 double `0.7423499822616577` (`0x3fe7c154c0000000`) |
| Unit | (Q/T)/L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba3c1` = data base `0x45` + Pos `0xba37c` (`@const_137124`) |
| Instruction address | IMG `0x3dd2e5` |
| Function | `playerMove` (statement ends @IMG `0x3dd346`) |
| Lifted statement (full) | `r742 = vector(0, 0, arg0.heroSpringLoaded * 0.74235)` |
| All literals in this statement | `0.74235`@3dd2e5, `0`@3dd314, `0`@3dd32a |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dd2d9  fstp qword ptr [ebp + 0x71b0]
3dd2df  fld qword ptr [ebp + 0x71b0]
3dd2e5  fmul qword ptr [ebx + 0xba37c] ; @const_137124{DOUBLE=0.7423499822616577 init=0.74235 hex=000000c054c1e73f}  <==
3dd2eb  fstp qword ptr [ebp + 0x71b8]
3dd2f1  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3dd2fe  pop eax
3dd2ff  fld qword ptr [ebp + 0x71b8]
3dd305  fmul qword ptr [eax]
3dd307  fistp dword ptr [ebp + 0x71c0]
```

**Math.** fmul 0.74235 on heroSpringLoaded ⇒ L4cc = vector(0, 0, 0.74235·L). Speed magnitude of the launch.

#### P36 — `NORMAL_BLEND_CLAMP`

| Field | Value |
|---|---|
| Full value | integer `45` (`0x0000002d`); entered as var `45·1024 = 46080` where shifted (`shl 10`) |
| Unit | deg |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba3c9` = data base `0x45` + Pos `0xba384` (`@const_137143`) |
| Instruction address | IMG `0x3dd48c` |
| Function | `playerMove` (statement ends @IMG `0x3dd4c5`) |
| Lifted statement (full) | `r746 = clamp(r745, -45, 45)` |
| All literals in this statement | `90`@3dd407, `45`@3dd48c, `0xffffffd3`@3dd4a2 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dd480  add esp, 4
3dd486  mov dword ptr [ebp + 0x71fc], eax
3dd48c  mov eax, dword ptr [ebx + 0xba384] ; @const_137143{LONG=45 init=45 hex=2d000000}  <==
3dd492  shl eax, 0xa
3dd495  mov dword ptr [ebp + 0x7200], eax
3dd49b  mov eax, dword ptr [ebp + 0x7200]
3dd4a1  push eax
3dd4a2  mov eax, dword ptr [ebx + 0xba388] ; @const_137144{LONG=-45 init=0xffffffd3 hex=d3ffffff}
3dd4a8  shl eax, 0xa
```

**Math.** r746 = clamp(r745, −45, 45) (raw push order verified: 45, −45, x).

#### P37 — `NORMAL_BLEND_FACTOR`

| Field | Value |
|---|---|
| Full value | source literal `0.1875`; stored IEEE-754 double `0.1875` (`0x3fc8000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba3d1` = data base `0x45` + Pos `0xba38c` (`@const_137151`) |
| Instruction address | IMG `0x3dd52e` |
| Function | `playerMove` (statement ends @IMG `0x3dd5c3`) |
| Lifted statement (full) | `r747 = vector(0, arg0.angle + (0.1875 * r746), 0)` |
| All literals in this statement | `90`@3dd407, `45`@3dd48c, `0xffffffd3`@3dd4a2, `0.1875`@3dd52e, `0`@3dd56e, `0`@3dd5a7 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dd526  fdiv qword ptr [eax]
3dd528  fstp qword ptr [ebp + 0x7214]
3dd52e  fld qword ptr [ebx + 0xba38c] ; @const_137151{DOUBLE=0.1875 init=0.1875 hex=000000000000c83f}  <==
3dd534  fmul qword ptr [ebp + 0x7214]
3dd53a  fstp qword ptr [ebp + 0x721c]
3dd540  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3dd54d  pop eax
3dd54e  fild dword ptr [ebp + 0x7210]
3dd554  fdiv qword ptr [eax]
```

**Math.** fmul 0.1875 · r746, fadd angle ⇒ a = angle + 0.1875·clamp(…). vector(0, a, 0) is the tilt used to vec_rotate L4cc.

#### P38 — `LAUNCH_SLIDE_CARRY_X`

| Field | Value |
|---|---|
| Full value | source literal `0.25`; stored IEEE-754 double `0.25` (`0x3fd0000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9c55` = data base `0x45` + Pos `0xb9c10` (`@const_131557`) |
| Instruction address | IMG `0x3dd6ef` |
| Function | `playerMove` (statement ends @IMG `0x3dd775`) |
| Lifted statement (full) | `arg0.speed.x = L4cc.x + (arg0.slideSpeed.x * 0.25)` |
| All literals in this statement | `0.25`@3dd6ef |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dd6e3  fstp qword ptr [ebp + 0x7268]
3dd6e9  fld qword ptr [ebp + 0x7268]
3dd6ef  fmul qword ptr [ebx + 0xb9c10] ; @const_131557{DOUBLE=0.25 init=0.25 hex=000000000000d03f}  <==
3dd6f5  fstp qword ptr [ebp + 0x7270]
3dd6fb  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3dd708  pop eax
3dd709  fild dword ptr [ebp + 0x7258]
3dd70f  fdiv qword ptr [eax]
3dd711  fstp qword ptr [ebp + 0x7278]
```

**Math.** arg0.speed.x = L4cc.x + 0.25·slideSpeed.x (velocity is assigned, not accumulated).

#### P39 — `LAUNCH_SLIDE_CARRY_Z`

| Field | Value |
|---|---|
| Full value | integer `0` (`0x00000000`); entered as var `0·1024 = 0` where shifted (`shl 10`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9841` = data base `0x45` + Pos `0xb97fc` (`@const_130172`) |
| Instruction address | IMG `0x3dd83e` |
| Function | `playerMove` (statement ends @IMG `0x3dd8ac`) |
| Lifted statement (full) | `arg0.speed.z = L4cc.z + (arg0.slideSpeed.z * 0)` |
| All literals in this statement | `0`@3dd83e |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dd836  mov eax, dword ptr [esi]
3dd838  mov dword ptr [ebp + 0x72a8], eax
3dd83e  mov eax, dword ptr [ebx + 0xb97fc] ; @const_130172{LONG=0 init=0 hex=00000000}  <==
3dd844  shl eax, 0xa
3dd847  mov dword ptr [ebp + 0x72ac], eax
3dd84d  mov eax, dword ptr [ebp + 0x72a8]
3dd853  mov edx, dword ptr [ebp + 0x72ac]
3dd859  imul edx
3dd85b  shrd eax, edx, 0xa
```

**Math.** arg0.speed.z = L4cc.z + 0·slideSpeed.z: vertical slide is discarded at launch.

### Launch spin and post-launch timers

#### P40 — `LAUNCH_SPIN_FROM_TILT`

| Field | Value |
|---|---|
| Full value | source literal `0.1245`; stored IEEE-754 double `0.12449999898672104` (`0x3fbfdf3b60000000`) |
| Unit | (deg/T)/deg |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba43d` = data base `0x45` + Pos `0xba3f8` (`@const_137580`) |
| Instruction address | IMG `0x3e007e` |
| Function | `playerMove` (statement ends @IMG `0x3e0185`) |
| Lifted statement (full) | `arg0.heroTurnSpeed = arg0.heroTurnSpeed + (((r786 * 0.1245) - (((r790 * r791) * 1.5) * 0.25)) * (1 - (0.5 * (collisionSpecialTypeResult == 1))))` |
| All literals in this statement | `0.75`@3dff66, `1.5`@3e0008, `0.1245`@3e007e, `0.25`@3e00ac, `1`@3e00d0, `0.5`@3e00ef, `1`@3e0101 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e0072  fstp qword ptr [ebp + 0x78c4]
3e0078  fld qword ptr [ebp + 0x78c4]
3e007e  fmul qword ptr [ebx + 0xba3f8] ; @const_137580{DOUBLE=0.12449999898672104 init=0.1245 hex=000000603bdfbf3f}  <==
3e0084  fstp qword ptr [ebp + 0x78cc]
3e008a  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3e0097  pop eax
3e0098  fild dword ptr [ebp + 0x2ac]
3e009e  fdiv qword ptr [eax]
3e00a0  fstp qword ptr [ebp + 0x78d4]
```

**Math.** fmul 0.1245 on r786 = clamp(ang(angle − gravityAngle), −45, 45). Term of the launch spin kick.

#### P40b — `LAUNCH_SPIN_TILT_CLAMP`

| Field | Value |
|---|---|
| Full value | integer `45` (`0x0000002d`); entered as var `45·1024 = 46080` where shifted (`shl 10`) |
| Unit | deg |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba3c9` = data base `0x45` + Pos `0xba384` (`@const_137143`) |
| Instruction address | IMG `0x3dfe3d` |
| Function | `playerMove` (statement ends @IMG `0x3dfe76`) |
| Lifted statement (full) | `r786 = clamp(r785, -45, 45)` |
| All literals in this statement | `45`@3dfe3d, `0xffffffd3`@3dfe53 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dfe31  add esp, 4
3dfe37  mov dword ptr [ebp + 0x7864], eax
3dfe3d  mov eax, dword ptr [ebx + 0xba384] ; @const_137143{LONG=45 init=45 hex=2d000000}  <==
3dfe43  shl eax, 0xa
3dfe46  mov dword ptr [ebp + 0x7868], eax
3dfe4c  mov eax, dword ptr [ebp + 0x7868]
3dfe52  push eax
3dfe53  mov eax, dword ptr [ebx + 0xba388] ; @const_137144{LONG=-45 init=0xffffffd3 hex=d3ffffff}
3dfe59  shl eax, 0xa
```

**Math.** The clamp(…, −45, 45) on the tilt term of the launch spin.

#### P41 — `SLOPE_SPIN_EXPONENT`

| Field | Value |
|---|---|
| Full value | source literal `0.75`; stored IEEE-754 double `0.75` (`0x3fe8000000000000`) |
| Unit | exponent |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9db9` = data base `0x45` + Pos `0xb9d74` (`@const_132339`) |
| Instruction address | IMG `0x3dff66` |
| Function | `playerMove` (statement ends @IMG `0x3e0185`) |
| Lifted statement (full) | `arg0.heroTurnSpeed = arg0.heroTurnSpeed + (((r786 * 0.1245) - (((r790 * r791) * 1.5) * 0.25)) * (1 - (0.5 * (collisionSpecialTypeResult == 1))))` |
| All literals in this statement | `0.75`@3dff66, `1.5`@3e0008, `0.1245`@3e007e, `0.25`@3e00ac, `1`@3e00d0, `0.5`@3e00ef, `1`@3e0101 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dff5f  mov eax, dword ptr [ebx + 0xb9d78] ; @const_132339+4<DOUBLE>
3dff65  push eax
3dff66  mov eax, dword ptr [ebx + 0xb9d74] ; @const_132339{DOUBLE=0.75 init=0.75 hex=000000000000e83f}  <==
3dff6c  push eax
3dff6d  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3dff7a  pop eax
3dff7b  fild dword ptr [ebp + 0x788c]
3dff81  fdiv qword ptr [eax]
3dff83  fstp qword ptr [ebp + 0x7890]
```

**Math.** r790 = pow(|asinv(normal.x)|, 0.75); slope term of the launch spin (and of the wall-bounce spin, @3e5f96).

#### P42 — `SLOPE_SPIN_GAIN`

| Field | Value |
|---|---|
| Full value | source literal `1.5`; stored IEEE-754 double `1.5` (`0x3ff8000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9f61` = data base `0x45` + Pos `0xb9f1c` (`@const_133237`) |
| Instruction address | IMG `0x3e0008` |
| Function | `playerMove` (statement ends @IMG `0x3e0185`) |
| Lifted statement (full) | `arg0.heroTurnSpeed = arg0.heroTurnSpeed + (((r786 * 0.1245) - (((r790 * r791) * 1.5) * 0.25)) * (1 - (0.5 * (collisionSpecialTypeResult == 1))))` |
| All literals in this statement | `0.75`@3dff66, `1.5`@3e0008, `0.1245`@3e007e, `0.25`@3e00ac, `1`@3e00d0, `0.5`@3e00ef, `1`@3e0101 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dfffc  fstp qword ptr [ebp + 0x78ac]
3e0002  fld qword ptr [ebp + 0x78ac]
3e0008  fmul qword ptr [ebx + 0xb9f1c] ; @const_133237{DOUBLE=1.5 init=1.5 hex=000000000000f83f}  <==
3e000e  fstp qword ptr [ebp + 0x78b4]
3e0014  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3e0021  pop eax
3e0022  fld qword ptr [ebp + 0x78b4]
3e0028  fmul qword ptr [eax]
3e002a  fistp dword ptr [ebp + 0x78bc]
```

**Math.** (sign(s)·|s|^0.75)·1.5, s = asinv(normal.x) in degrees (asinv returns degrees: B).

#### P43 — `SLOPE_SPIN_LAUNCH_FACTOR`

| Field | Value |
|---|---|
| Full value | source literal `0.25`; stored IEEE-754 double `0.25` (`0x3fd0000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9c55` = data base `0x45` + Pos `0xb9c10` (`@const_131557`) |
| Instruction address | IMG `0x3e00ac` |
| Function | `playerMove` (statement ends @IMG `0x3e0185`) |
| Lifted statement (full) | `arg0.heroTurnSpeed = arg0.heroTurnSpeed + (((r786 * 0.1245) - (((r790 * r791) * 1.5) * 0.25)) * (1 - (0.5 * (collisionSpecialTypeResult == 1))))` |
| All literals in this statement | `0.75`@3dff66, `1.5`@3e0008, `0.1245`@3e007e, `0.25`@3e00ac, `1`@3e00d0, `0.5`@3e00ef, `1`@3e0101 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e00a0  fstp qword ptr [ebp + 0x78d4]
3e00a6  fld qword ptr [ebp + 0x78d4]
3e00ac  fmul qword ptr [ebx + 0xb9c10] ; @const_131557{DOUBLE=0.25 init=0.25 hex=000000000000d03f}  <==
3e00b2  fstp qword ptr [ebp + 0x78dc]
3e00b8  fld qword ptr [ebp + 0x78cc]
3e00be  fsub qword ptr [ebp + 0x78dc]
3e00c4  fstp qword ptr [ebp + 0x78e4]
3e00ca  mov ecx, dword ptr [ebx + 0x7af98] ; collisionSpecialTypeResult<LONG>
3e00d0  mov edx, dword ptr [ebx + 0xb9804] ; @const_130182{LONG=1 init=1 hex=01000000}
```

**Math.** ·0.25 on the slope term at launch ⇒ −0.375·sign(s)|s|^0.75.

#### P44 — `NO_GROUND_TIMER_AFTER_LAUNCH`

| Field | Value |
|---|---|
| Full value | integer `2` (`0x00000002`); entered as var `2·1024 = 2048` where shifted (`shl 10`) |
| Unit | T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9965` = data base `0x45` + Pos `0xb9920` (`@const_130373`) |
| Instruction address | IMG `0x3e030d` |
| Function | `playerMove` (statement ends @IMG `0x3e0339`) |
| Lifted statement (full) | `arg0.noGroundContactTimer = 2` |
| All literals in this statement | `2`@3e030d |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e0305  mov eax, dword ptr [esi]
3e0307  mov dword ptr [ebp + 0x794c], eax
3e030d  mov eax, dword ptr [ebx + 0xb9920] ; @const_130373{LONG=2 init=2 hex=02000000}  <==
3e0313  shl eax, 0xa
3e0316  mov dword ptr [ebp + 0x7950], eax
3e031c  mov eax, dword ptr [ebp + 0x7950]
3e0322  mov dword ptr [ebp + 0x794c], eax
3e0328  mov ecx, 4
3e032d  mov esi, dword ptr [ebp + 0x7948]
```

**Math.** noGroundContactTimer = 2 at launch. Decremented by Δt per frame (@3cb704); while > 0 heroGroundContact is forced 0 (@3cb665) and the trace is skipped (@3d83f1).

#### P45 — `JUMP_TIMER_GAIN`

| Field | Value |
|---|---|
| Full value | source literal `0.45`; stored IEEE-754 double `0.44999998807907104` (`0x3fdcccccc0000000`) |
| Unit | T/√L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba449` = data base `0x45` + Pos `0xba404` (`@const_137613`) |
| Instruction address | IMG `0x3e03e0` |
| Function | `playerMove` (statement ends @IMG `0x3e0438`) |
| Lifted statement (full) | `arg0.jumpTimer = r792 * 0.45` |
| All literals in this statement | `0.45`@3e03e0 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e03d9  pop edx
3e03da  fld qword ptr [ebp + 0x7968]
3e03e0  fmul qword ptr [ebx + 0xba404] ; @const_137613{DOUBLE=0.44999998807907104 init=0.45 hex=000000c0ccccdc3f}  <==
3e03e6  fstp qword ptr [ebp + 0x7970]
3e03ec  mov ecx, 4
3e03f1  mov esi, dword ptr [ebp + 0x7954]
3e03f7  mov eax, dword ptr [esi]
3e03f9  mov dword ptr [ebp + 0x7978], eax
3e03ff  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
```

**Math.** jumpTimer = 0.45·sqrt(heroSpringLoadedMax). Decremented by Δt in the air (@3cb88a), zeroed on the ground; enters the turn law as 1/(1 + √jumpTimer).

### Boost (power jump) threshold

#### P46 — `POWERJUMP_ROTATION_THRESHOLD`

| Field | Value |
|---|---|
| Full value | integer `285` (`0x0000011d`); entered as var `285·1024 = 291840` where shifted (`shl 10`) |
| Unit | deg |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba661` = data base `0x45` + Pos `0xba61c` (`@const_139827`) |
| Instruction address | IMG `0x3ee4f7` |
| Function | `playerMove` (statement ends @IMG `0x3ee58b`) |
| Lifted statement (full) | `if !((L44 > 285) && (arg0.powerJumpNext < 1)) goto label_3ee5f3` |
| All literals in this statement | `285`@3ee4f7, `1`@3ee53d |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3ee4f1  mov ecx, dword ptr [ebp + 0x44]
3ee4f7  mov edx, dword ptr [ebx + 0xba61c] ; @const_139827{LONG=285 init=285 hex=1d010000}  <==
3ee4fd  xor eax, eax
3ee4ff  cmp ecx, edx
3ee501  setg al
3ee504  mov dword ptr [ebp + 0x9c80], eax
3ee50a  mov eax, dword ptr [ebp]
3ee510  mov dword ptr [ebp + 0x9c84], eax
```

**Math.** In the air: L44 = int(|angle − lastJumpAngle|). If L44 > 285 and powerJumpNext < 1 ⇒ powerJumpNext = 1. lastJumpAngle is set at launch (@3dd28e) and at a wall bounce (@3e5470).

### Wall bounce (bonk)

#### P47 — `BONK_REFLECT`

| Field | Value |
|---|---|
| Full value | integer `-2` (`0xfffffffe`); entered as var `-2·1024 = -2048` where shifted (`shl 10`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba0ad` = data base `0x45` + Pos `0xba068` (`@const_134248`) |
| Instruction address | IMG `0x3e56b3` |
| Function | `playerMove` (statement ends @IMG `0x3e56f1`) |
| Lifted statement (full) | `r858 = vec_scale(bounce, -2 * r857)` |
| All literals in this statement | `0xfffffffe`@3e56b3 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e56a7  add esp, 8
3e56ad  mov dword ptr [ebp + 0x8680], eax
3e56b3  mov eax, dword ptr [ebx + 0xba068] ; @const_134248{LONG=-2 init=0xfffffffe hex=feffffff}  <==
3e56b9  shl eax, 0xa
3e56bc  mov dword ptr [ebp + 0x8684], eax
3e56c2  mov eax, dword ptr [ebp + 0x8684]
3e56c8  mov edx, dword ptr [ebp + 0x8680]
3e56ce  imul edx
3e56d0  shrd eax, edx, 0xa
```

**Math.** Fallback branch only (executed when L98 = hit.flags & 0x600 is 0): bounce = n; bounce·= −2(n·v); bounce += v ⇒ r = v − 2(n·v)n. When L98 ≠ 0 (the usual c_move hit) the engine-written global `bounce` is used instead (meaning B, see P48).

#### P49 — `BONK_SPEED_FACTOR`

| Field | Value |
|---|---|
| Full value | source literal `0.4`; stored IEEE-754 double `0.4000000059604645` (`0x3fd99999a0000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba539` = data base `0x45` + Pos `0xba4f4` (`@const_138467`) |
| Instruction address | IMG `0x3e591f` |
| Function | `playerMove` (statement ends @IMG `0x3e596a`) |
| Lifted statement (full) | `r863 = maxv(28, r862 * 0.4)` |
| All literals in this statement | `0.4`@3e591f, `28`@3e594e |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e5913  fstp qword ptr [ebp + 0x86f4]
3e5919  fld qword ptr [ebp + 0x86f4]
3e591f  fmul qword ptr [ebx + 0xba4f4] ; @const_138467{DOUBLE=0.4000000059604645 init=0.4 hex=000000a09999d93f}  <==
3e5925  fstp qword ptr [ebp + 0x86fc]
3e592b  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3e5938  pop eax
3e5939  fld qword ptr [ebp + 0x86fc]
3e593f  fmul qword ptr [eax]
3e5941  fistp dword ptr [ebp + 0x8704]
```

**Math.** r863 = maxv(28, 0.4·|v|).

#### P50 — `BONK_MIN_SPEED`

| Field | Value |
|---|---|
| Full value | integer `28` (`0x0000001c`); entered as var `28·1024 = 28672` where shifted (`shl 10`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9fc1` = data base `0x45` + Pos `0xb9f7c` (`@const_133582`) |
| Instruction address | IMG `0x3e594e` |
| Function | `playerMove` (statement ends @IMG `0x3e596a`) |
| Lifted statement (full) | `r863 = maxv(28, r862 * 0.4)` |
| All literals in this statement | `0.4`@3e591f, `28`@3e594e |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e5947  mov eax, dword ptr [ebp + 0x8704]
3e594d  push eax
3e594e  mov eax, dword ptr [ebx + 0xb9f7c] ; @const_133582{LONG=28 init=28 hex=1c000000}  <==
3e5954  shl eax, 0xa
3e5957  mov dword ptr [ebp + 0x8708], eax
3e595d  mov eax, dword ptr [ebp + 0x8708]
3e5963  push eax
3e5964  mov eax, dword ptr [ebx + 0x90730] ; maxv<POINTER>
3e596a  call eax
```

**Math.** Floor of the bounce speed in r863 = max(28, 0.4|v|).

#### P73 — `BONK_SLOPE_ATTEN`

| Field | Value |
|---|---|
| Full value | integer `1` (`0x00000001`); entered as var `1·1024 = 1024` where shifted (`shl 10`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9849` = data base `0x45` + Pos `0xb9804` (`@const_130182`) |
| Instruction address | IMG `0x3e59ab` |
| Function | `playerMove` (statement ends @IMG `0x3e59f1`) |
| Lifted statement (full) | `r864 = minv(1 + normal.z, 1)` |
| All literals in this statement | `1`@3e59ab, `1`@3e59ce |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e59a3  mov eax, dword ptr [esi]
3e59a5  mov dword ptr [ebp + 0x8714], eax
3e59ab  mov eax, dword ptr [ebx + 0xb9804] ; @const_130182{LONG=1 init=1 hex=01000000}  <==
3e59b1  shl eax, 0xa
3e59b4  mov dword ptr [ebp + 0x8718], eax
3e59ba  mov eax, dword ptr [ebp + 0x8718]
3e59c0  mov ecx, dword ptr [ebp + 0x8714]
3e59c6  add eax, ecx
3e59c8  mov dword ptr [ebp + 0x871c], eax
```

**Math.** r864 = minv(1 + normal.z, 1); L8c = r863·r864 (var product) ⇒ |v′| = max(28, 0.4|v|)·min(1 + n_z, 1). Then vec_normalize(bounce, L8c); if L298.z > 0: bounce.z = L298.z·L8c.

#### P51 — `BONK_X_SCALE`

| Field | Value |
|---|---|
| Full value | source literal `0.875`; stored IEEE-754 double `0.875` (`0x3fec000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba541` = data base `0x45` + Pos `0xba4fc` (`@const_138503`) |
| Instruction address | IMG `0x3e5cfc` |
| Function | `playerMove` (statement ends @IMG `0x3e5dd5`) |
| Lifted statement (full) | `arg0.speed.x = (bounce.x * 0.875) + arg0.slideSpeed.x` |
| All literals in this statement | `0.875`@3e5cfc |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e5cf0  fstp qword ptr [ebp + 0x878c]
3e5cf6  fld qword ptr [ebp + 0x878c]
3e5cfc  fmul qword ptr [ebx + 0xba4fc] ; @const_138503{DOUBLE=0.875 init=0.875 hex=000000000000ec3f}  <==
3e5d02  fstp qword ptr [ebp + 0x8794]
3e5d08  mov eax, dword ptr [ebp]
3e5d0e  mov dword ptr [ebp + 0x879c], eax
3e5d14  mov eax, dword ptr [ebp + 0x879c]
3e5d1a  mov ecx, dword ptr [ebx + 0xba08c] ; @const_134365{LONG=165 init=OFFSET:STRUCT@94:slideSpeed hex=a5000000}
3e5d20  add eax, ecx
```

**Math.** arg0.speed.x = 0.875·bounce.x + slideSpeed.x; arg0.speed.z = bounce.z + slideSpeed.z (assignment).

#### P52 — `BONK_SPIN_RIGHTING`

| Field | Value |
|---|---|
| Full value | source literal `0.5`; stored IEEE-754 double `0.5` (`0x3fe0000000000000`) |
| Unit | (deg/T)/deg |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b986d` = data base `0x45` + Pos `0xb9828` (`@const_130217`) |
| Instruction address | IMG `0x3e610c` |
| Function | `playerMove` (statement ends @IMG `0x3e61a4`) |
| Lifted statement (full) | `arg0.heroTurnSpeed = (r872 * 0.5) - (((r870 * r871) * 1.5) * 0.2)` |
| All literals in this statement | `0.75`@3e5f5f, `1.5`@3e6001, `0.5`@3e610c, `0.2`@3e613a |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e6100  fstp qword ptr [ebp + 0x8840]
3e6106  fld qword ptr [ebp + 0x8840]
3e610c  fmul qword ptr [ebx + 0xb9828] ; @const_130217{DOUBLE=0.5 init=0.5 hex=000000000000e03f}  <==
3e6112  fstp qword ptr [ebp + 0x8848]
3e6118  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3e6125  pop eax
3e6126  fild dword ptr [ebp + 0x2b0]
3e612c  fdiv qword ptr [eax]
3e612e  fstp qword ptr [ebp + 0x8850]
```

**Math.** heroTurnSpeed is SET: ω = 0.5·ang(gravityAngle − angle) − 0.2·1.5·sign(s)|s|^0.75.

#### P53 — `SLOPE_SPIN_BONK_FACTOR`

| Field | Value |
|---|---|
| Full value | source literal `0.2`; stored IEEE-754 double `0.20000000298023224` (`0x3fc99999a0000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9f1d` = data base `0x45` + Pos `0xb9ed8` (`@const_132940`) |
| Instruction address | IMG `0x3e613a` |
| Function | `playerMove` (statement ends @IMG `0x3e61a4`) |
| Lifted statement (full) | `arg0.heroTurnSpeed = (r872 * 0.5) - (((r870 * r871) * 1.5) * 0.2)` |
| All literals in this statement | `0.75`@3e5f5f, `1.5`@3e6001, `0.5`@3e610c, `0.2`@3e613a |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e612e  fstp qword ptr [ebp + 0x8850]
3e6134  fld qword ptr [ebp + 0x8850]
3e613a  fmul qword ptr [ebx + 0xb9ed8] ; @const_132940{DOUBLE=0.20000000298023224 init=0.2 hex=000000a09999c93f}  <==
3e6140  fstp qword ptr [ebp + 0x8858]
3e6146  fld qword ptr [ebp + 0x8848]
3e614c  fsub qword ptr [ebp + 0x8858]
3e6152  fstp qword ptr [ebp + 0x8860]
3e6158  mov ecx, 4
3e615d  mov esi, dword ptr [ebp + 0x8824]
```

**Math.** ·0.2 on the slope spin at a wall bounce ⇒ −0.3·sign(s)|s|^0.75.

#### P54 — `NO_GROUND_TIMER_AFTER_BONK`

| Field | Value |
|---|---|
| Full value | integer `2` (`0x00000002`); entered as var `2·1024 = 2048` where shifted (`shl 10`) |
| Unit | T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9965` = data base `0x45` + Pos `0xb9920` (`@const_130373`) |
| Instruction address | IMG `0x3e52be` |
| Function | `playerMove` (statement ends @IMG `0x3e52ea`) |
| Lifted statement (full) | `arg0.noGroundContactTimer = 2` |
| All literals in this statement | `2`@3e52be |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e52b6  mov eax, dword ptr [esi]
3e52b8  mov dword ptr [ebp + 0x85e8], eax
3e52be  mov eax, dword ptr [ebx + 0xb9920] ; @const_130373{LONG=2 init=2 hex=02000000}  <==
3e52c4  shl eax, 0xa
3e52c7  mov dword ptr [ebp + 0x85ec], eax
3e52cd  mov eax, dword ptr [ebp + 0x85ec]
3e52d3  mov dword ptr [ebp + 0x85e8], eax
3e52d9  mov ecx, 4
3e52de  mov esi, dword ptr [ebp + 0x85e4]
```

**Math.** At a wall bounce: noGroundContactTimer = 2, powerJumpNext = 0, angle = ang(angle), lastJumpAngle = angle, heroSpringLoaded = 0; in water jumpTimer = 4.

### Slide / ice / friction

#### P57 — `ICE_SLIDE_RESPONSE`

| Field | Value |
|---|---|
| Full value | source literal `0.25`; stored IEEE-754 double `0.25` (`0x3fd0000000000000`) |
| Unit | 1/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9c55` = data base `0x45` + Pos `0xb9c10` (`@const_131557`) |
| Instruction address | IMG `0x3cbdc1` |
| Function | `playerMove` (statement ends @IMG `0x3cbe3c`) |
| Lifted statement (full) | `r467 = clamp((L44ec.x - arg0.slideSpeed.x) * 0.25, -1.35, 1.35)` |
| All literals in this statement | `0.25`@3cbdc1, `1.35`@3cbddb, `-1.35`@3cbdfe |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3cbdb5  fstp qword ptr [ebp + 0x4598]
3cbdbb  fld qword ptr [ebp + 0x4598]
3cbdc1  fmul qword ptr [ebx + 0xb9c10] ; @const_131557{DOUBLE=0.25 init=0.25 hex=000000000000d03f}  <==
3cbdc7  fstp qword ptr [ebp + 0x45a0]
3cbdcd  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3cbdda  pop eax
3cbddb  fld qword ptr [ebx + 0xba090] ; @const_134375{DOUBLE=1.350000023841858 init=1.35 hex=000000a09999f53f}
3cbde1  fmul qword ptr [eax]
3cbde3  fistp dword ptr [ebp + 0x45a8]
```

**Math.** r467 = clamp((t_x − s_x)·0.25, −1.35, 1.35); s_x ← s_x + r467·Δt. Same for z with r468 @3cc02a.

#### P58 — `ICE_SLIDE_ACCEL_CLAMP`

| Field | Value |
|---|---|
| Full value | source literal `1.35`; stored IEEE-754 double `1.350000023841858` (`0x3ff59999a0000000`) |
| Unit | Q/T² |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba0d5` = data base `0x45` + Pos `0xba090` (`@const_134375`) |
| Instruction address | IMG `0x3cbddb` |
| Function | `playerMove` (statement ends @IMG `0x3cbe3c`) |
| Lifted statement (full) | `r467 = clamp((L44ec.x - arg0.slideSpeed.x) * 0.25, -1.35, 1.35)` |
| All literals in this statement | `0.25`@3cbdc1, `1.35`@3cbddb, `-1.35`@3cbdfe |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3cbdcd  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3cbdda  pop eax
3cbddb  fld qword ptr [ebx + 0xba090] ; @const_134375{DOUBLE=1.350000023841858 init=1.35 hex=000000a09999f53f}  <==
3cbde1  fmul qword ptr [eax]
3cbde3  fistp dword ptr [ebp + 0x45a8]
3cbde9  mov eax, dword ptr [ebp + 0x45a8]
3cbdef  push eax
3cbdf0  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3cbdfd  pop eax
```

**Math.** Limits of the per-tick slide acceleration: |Δs| ≤ 1.35·Δt per axis.

#### P72 — `SLIDE_STOP_THRESHOLD`

| Field | Value |
|---|---|
| Full value | source literal `0.25`; stored IEEE-754 double `0.25` (`0x3fd0000000000000`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9c55` = data base `0x45` + Pos `0xb9c10` (`@const_131557`) |
| Instruction address | IMG `0x3cc8bc` |
| Function | `playerMove` (statement ends @IMG `0x3cc945`) |
| Lifted statement (full) | `if !((r488 < 0.25) \| (!(arg0.heroGroundContact))) goto label_3cc9a2` |
| All literals in this statement | `0.25`@3cc8bc |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3cc8b0  fstp qword ptr [ebp + 0x477c]
3cc8b6  fld qword ptr [ebp + 0x477c]
3cc8bc  fcomp qword ptr [ebx + 0xb9c10] ; @const_131557{DOUBLE=0.25 init=0.25 hex=000000000000d03f}  <==
3cc8c2  mov dword ptr [ebp + 0x4784], 1
3cc8cc  fnstsw ax
3cc8ce  test ah, 1
3cc8d1  jne 0x3cc8dd
3cc8d3  mov dword ptr [ebp + 0x4784], 0
3cc8dd  mov eax, dword ptr [ebp]
```

**Math.** fcomp |slide| with 0.25 (test ah,1/jne ⇒ |slide| < 0.25). If |slide| < 0.25 OR not grounded: vec_set(slide, nullvector) ⇒ slide = 0.

#### P60 — `ICE_LANDING_Z_CARRY`

| Field | Value |
|---|---|
| Full value | source literal `0.25`; stored IEEE-754 double `0.25` (`0x3fd0000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9c55` = data base `0x45` + Pos `0xb9c10` (`@const_131557`) |
| Instruction address | IMG `0x3da813` |
| Function | `playerMove` (statement ends @IMG `0x3da86b`) |
| Lifted statement (full) | `arg0.slideSpeed.z = arg0.speed.z * 0.25` |
| All literals in this statement | `0.25`@3da813 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3da807  fstp qword ptr [ebp + 0x6b38]
3da80d  fld qword ptr [ebp + 0x6b38]
3da813  fmul qword ptr [ebx + 0xb9c10] ; @const_131557{DOUBLE=0.25 init=0.25 hex=000000000000d03f}  <==
3da819  fstp qword ptr [ebp + 0x6b40]
3da81f  mov ecx, 4
3da824  mov esi, dword ptr [ebp + 0x6b28]
3da82a  mov eax, dword ptr [esi]
3da82c  mov dword ptr [ebp + 0x6b48], eax
3da832  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
```

**Math.** Landing on a slippery contact entity (entity.flags & 64) or in mode L30: flags |= 1 (slide mode), slide_x = v_x. slide_z = 0.25·v_z only if (level 9 & mode > 18) or ((L30|level 10|9|100|110) & v_z > 0), otherwise slide_z = 0. On the normal main map: slide_z = 0.

### Platform release and ledge exit

#### P61 — `PLATFORM_CARRY_DOWN_REDUCTION`

| Field | Value |
|---|---|
| Full value | source literal `0.75`; stored IEEE-754 double `0.75` (`0x3fe8000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9db9` = data base `0x45` + Pos `0xb9d74` (`@const_132339`) |
| Instruction address | IMG `0x3eaa48` |
| Function | `playerMove` (statement ends @IMG `0x3eaafa`) |
| Lifted statement (full) | `arg0.speed.z = arg0.speed.z + (playerLateralSpeed.z * (1 - (0.75 * (playerLateralSpeed.z < 0))))` |
| All literals in this statement | `0`@3eaa14, `0.75`@3eaa48, `1`@3eaa5a |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3eaa3c  fild dword ptr [ebp + 0x9354]
3eaa42  fstp qword ptr [ebp + 0x9358]
3eaa48  fld qword ptr [ebx + 0xb9d74] ; @const_132339{DOUBLE=0.75 init=0.75 hex=000000000000e83f}  <==
3eaa4e  fmul qword ptr [ebp + 0x9358]
3eaa54  fstp qword ptr [ebp + 0x9360]
3eaa5a  fild dword ptr [ebx + 0xb9804] ; @const_130182{LONG=1 init=1 hex=01000000}
3eaa60  fstp qword ptr [ebp + 0x9368]
3eaa66  fld qword ptr [ebp + 0x9368]
3eaa6c  fsub qword ptr [ebp + 0x9360]
```

**Math.** In the air, when playerLateralSpeed ≠ 0 (release from a moving platform): v_x += lat_x; v_z += lat_z·(1 − 0.75·(lat_z < 0)).

#### P71 — `LEDGE_EXIT_POP_SPEED`

| Field | Value |
|---|---|
| Full value | integer `5` (`0x00000005`); entered as var `5·1024 = 5120` where shifted (`shl 10`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9889` = data base `0x45` + Pos `0xb9844` (`@const_130266`) |
| Instruction address | IMG `0x3d86c9` |
| Function | `playerMove` (statement ends @IMG `0x3d86f5`) |
| Lifted statement (full) | `arg0.speed.z = 5` |
| All literals in this statement | `5`@3d86c9 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d86c1  mov eax, dword ptr [esi]
3d86c3  mov dword ptr [ebp + 0x6644], eax
3d86c9  mov eax, dword ptr [ebx + 0xb9844] ; @const_130266{LONG=5 init=5 hex=05000000}  <==
3d86cf  shl eax, 0xa
3d86d2  mov dword ptr [ebp + 0x6648], eax
3d86d8  mov eax, dword ptr [ebp + 0x6648]
3d86de  mov dword ptr [ebp + 0x6644], eax
3d86e4  mov ecx, 4
3d86e9  mov esi, dword ptr [ebp + 0x6640]
```

**Math.** If the player was grounded at the start of the probe (L2b4) and the probe now misses: v = (slide_x, 5) (assignment).

### Spring animation (feeds hull)

#### P67 — `SPRING_VISUAL_RELAX_RATE`

| Field | Value |
|---|---|
| Full value | integer `120` (`0x00000078`); entered as var `120·1024 = 122880` where shifted (`shl 10`) |
| Unit | L/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9e2d` = data base `0x45` + Pos `0xb9de8` (`@const_132574`) |
| Instruction address | IMG `0x3eaca0` |
| Function | `playerMove` (statement ends @IMG `0x3ead01`) |
| Lifted statement (full) | `r946 = maxv(arg0.heroSpringLoadedBonePerc - (120 * (*time_step)), -200)` |
| All literals in this statement | `120`@3eaca0, `0xffffff38`@3eacde |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3eac98  mov eax, dword ptr [esi]
3eac9a  mov dword ptr [ebp + 0x93d8], eax
3eaca0  mov eax, dword ptr [ebx + 0xb9de8] ; @const_132574{LONG=120 init=120 hex=78000000}  <==
3eaca6  shl eax, 0xa
3eaca9  mov dword ptr [ebp + 0x93dc], eax
3eacaf  mov eax, dword ptr [ebp + 0x93dc]
3eacb5  mov edx, dword ptr [ebp + 0x93d8]
3eacbb  imul edx
3eacbd  shrd eax, edx, 0xa
```

**Math.** In the air: BonePerc = max(BonePerc − 120·Δt, −200). On the ground while charging BonePerc = BonePercMax = heroSpringLoaded (@3dc0dc).

### Ground correction and anti-stuck

#### C01 — `GROUND_CORRECTION_PROBE_SCALE`

| Field | Value |
|---|---|
| Full value | source literal `0.9`; stored IEEE-754 double `0.8999999761581421` (`0x3fecccccc0000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9cfd` = data base `0x45` + Pos `0xb9cb8` (`@const_132070`) |
| Instruction address | IMG `0x3d6f9c` |
| Function | `playerMove` (statement ends @IMG `0x3d6fcb`) |
| Lifted statement (full) | `r630 = vec_scale(ADDR(L3fc), 0.9)` |
| All literals in this statement | `0.9`@3d6f9c |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d6f8e  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3d6f9b  pop eax
3d6f9c  fld qword ptr [ebx + 0xb9cb8] ; @const_132070{DOUBLE=0.8999999761581421 init=0.9 hex=000000c0ccccec3f}  <==
3d6fa2  fmul qword ptr [eax]
3d6fa4  fistp dword ptr [ebp + 0x629c]
3d6faa  mov eax, dword ptr [ebp + 0x629c]
3d6fb0  push eax
3d6fb1  mov eax, ebp
3d6fb3  add eax, 0x3fc
```

**Math.** playerUseCorrection (init 2) and grounded: L3fc = me + 0.9·(Bone1 − me) (vec_diff, vec_scale 0.9, vec_add).

#### C03 — `ANTISTUCK_RAY_LENGTH`

| Field | Value |
|---|---|
| Full value | integer `64` (`0x00000040`); entered as var `64·1024 = 65536` where shifted (`shl 10`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ae451` = data base `0x45` + Pos `0xae40c` (`@const_74097`) |
| Instruction address | IMG `0x26de16` |
| Function | `pogoCorrectionDo` (statement ends @IMG `0x26de70`) |
| Lifted statement (full) | `L2c.x = 64 * r7` |
| All literals in this statement | `30`@26ddac, `64`@26de16 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
26de0a  add esp, 4
26de10  mov dword ptr [ebp + 0x108], eax
26de16  mov eax, dword ptr [ebx + 0xae40c] ; @const_74097{LONG=64 init=64 hex=40000000}  <==
26de1c  shl eax, 0xa
26de1f  mov dword ptr [ebp + 0x10c], eax
26de25  mov eax, dword ptr [ebp + 0x10c]
26de2b  mov edx, dword ptr [ebp + 0x108]
26de31  imul edx
26de33  shrd eax, edx, 0xa
```

**Math.** pogoCorrectionDo (runs a few frames after a jump while the player has not moved > 0.1 Q): 12 rays at 30° steps of length 64, from me − 0.2·ray to me + ray; each hit adds −ray normalised to (64 − hitDist).

#### C04 — `ANTISTUCK_PUSH`

| Field | Value |
|---|---|
| Full value | integer `16` (`0x00000010`); entered as var `16·1024 = 16384` where shifted (`shl 10`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ae489` = data base `0x45` + Pos `0xae444` (`@const_74174`) |
| Instruction address | IMG `0x26e4e8` |
| Function | `pogoCorrectionDo` (statement ends @IMG `0x26e518`) |
| Lifted statement (full) | `r14 = vec_normalize(ADDR(L20), 16)` |
| All literals in this statement | `16`@26e4e8 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **A** |

```asm
26e4e8  mov eax, dword ptr [ebx + 0xae444] ; @const_74174{LONG=16 init=16 hex=10000000}  <==
26e4ee  shl eax, 0xa
26e4f1  mov dword ptr [ebp + 0x224], eax
26e4f7  mov eax, dword ptr [ebp + 0x224]
26e4fd  push eax
26e4fe  mov eax, ebp
26e500  add eax, 0x20
```

**Math.** If any ray hit: sum normalised to 16 and added to the position (me += push), playerLocalHelperIgnoreTime = 4.

### Timestep

#### T02 — `FPS_LIMIT_DEFAULT`

| Field | Value |
|---|---|
| Full value | integer `120` (`0x00000078`); entered as var `120·1024 = 122880` where shifted (`shl 10`) |
| Unit | frames/s |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0c6efd` = data base `0x45` + Pos `0xc6eb8` (`@const_183142`) |
| Instruction address | IMG `0x4e750d` |
| Function | `settingsDefault` (statement ends @IMG `0x4e7522`) |
| Lifted statement (full) | `fps_limit2 = 120` |
| All literals in this statement | `120`@4e750d |
| Scope | TIMESTEP |
| Class | value+operation **A**; physical meaning **A** |

```asm
4e7507  mov dword ptr [ebx + 0x7b8b0], eax ; display_live_ranking<FIXED>
4e750d  mov eax, dword ptr [ebx + 0xc6eb8] ; @const_183142{LONG=120 init=120 hex=78000000}  <==
4e7513  shl eax, 0xa
4e7516  mov dword ptr [ebp + 0x84], eax
4e751c  mov eax, dword ptr [ebp + 0x84]
4e7522  mov dword ptr [ebx + 0x7d4c0], eax ; fps_limit2<FIXED>
```

**Math.** settingsDefault: fps_limit2 = 120; fps_limit = fps_limit2; *fps_max = fps_limit.

#### T03 — `FPS_LIMIT_RANGE_MAX`

| Field | Value |
|---|---|
| Full value | integer `240` (`0x000000f0`); entered as var `240·1024 = 245760` where shifted (`shl 10`) |
| Unit | frames/s |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0c7ed9` = data base `0x45` + Pos `0xc7e94` (`@const_184499`) |
| Instruction address | IMG `0x4ee498` |
| Function | `dec_do` (statement ends @IMG `0x4ee4d1`) |
| Lifted statement (full) | `r42 = clamp(fps_limit2, 30, 240)` |
| All literals in this statement | `240`@4ee498, `30`@4ee4ae |
| Scope | TIMESTEP |
| Class | value+operation **A**; physical meaning **A** |

```asm
4ee492  mov dword ptr [ebx + 0x7e3a8], eax ; inputPowerExponent2<FIXED>
4ee498  mov eax, dword ptr [ebx + 0xc7e94] ; @const_184499{LONG=240 init=240 hex=f0000000}  <==
4ee49e  shl eax, 0xa
4ee4a1  mov dword ptr [ebp + 0x2a4], eax
4ee4a7  mov eax, dword ptr [ebp + 0x2a4]
4ee4ad  push eax
4ee4ae  mov eax, dword ptr [ebx + 0xc7e90] ; @const_184498{LONG=30 init=30 hex=1e000000}
4ee4b4  shl eax, 0xa
```

**Math.** fps_limit2 = clamp(fps_limit2, 30, 240) on settings load; mainFrameEvent also clamps fps_limit to [30, 240] (@6a173d).

#### T04 — `FPS_LIMIT_RANGE_MIN`

| Field | Value |
|---|---|
| Full value | integer `30` (`0x0000001e`); entered as var `30·1024 = 30720` where shifted (`shl 10`) |
| Unit | frames/s |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0c7ed5` = data base `0x45` + Pos `0xc7e90` (`@const_184498`) |
| Instruction address | IMG `0x4ee4ae` |
| Function | `dec_do` (statement ends @IMG `0x4ee4d1`) |
| Lifted statement (full) | `r42 = clamp(fps_limit2, 30, 240)` |
| All literals in this statement | `240`@4ee498, `30`@4ee4ae |
| Scope | TIMESTEP |
| Class | value+operation **A**; physical meaning **A** |

```asm
4ee4a7  mov eax, dword ptr [ebp + 0x2a4]
4ee4ad  push eax
4ee4ae  mov eax, dword ptr [ebx + 0xc7e90] ; @const_184498{LONG=30 init=30 hex=1e000000}  <==
4ee4b4  shl eax, 0xa
4ee4b7  mov dword ptr [ebp + 0x2a8], eax
4ee4bd  mov eax, dword ptr [ebp + 0x2a8]
4ee4c3  push eax
4ee4c4  mov eax, dword ptr [ebx + 0x7d4c0] ; fps_limit2<FIXED>
4ee4ca  push eax
```

**Math.** Lower bound 30 of the same clamp.

#### T05 — `TIME_FACTOR_MIN_GUARD`

| Field | Value |
|---|---|
| Full value | source literal `0.9`; stored IEEE-754 double `0.8999999761581421` (`0x3fecccccc0000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0db211` = data base `0x45` + Pos `0xdb1cc` (`@const_259470`) |
| Instruction address | IMG `0x6a14bf` |
| Function | `mainFrameEvent` (statement ends @IMG `0x6a14e9`) |
| Lifted statement (full) | `if !((*time_factor) < 0.9) goto label_6a1529` |
| All literals in this statement | `0.9`@6a14bf |
| Scope | TIMESTEP |
| Class | value+operation **A**; physical meaning **A** |

```asm
6a14b3  fstp qword ptr [ebp + 0x204]
6a14b9  fld qword ptr [ebp + 0x204]
6a14bf  fcomp qword ptr [ebx + 0xdb1cc] ; @const_259470{DOUBLE=0.8999999761581421 init=0.9 hex=000000c0ccccec3f}  <==
6a14c5  mov dword ptr [ebp + 0x20c], 1
6a14cf  fnstsw ax
6a14d1  test ah, 1
6a14d4  jne 0x6a14e0
6a14d6  mov dword ptr [ebp + 0x20c], 0
6a14e0  mov eax, dword ptr [ebp + 0x20c]
```

**Math.** fcomp time_factor with 0.9: if < 0.9 ⇒ fuser(7) + sys_exit (anti speed-hack). Upper guard > 1 @6a156d.

### Independent corroboration

#### P69 — `PREDICT_GRAVITY (corroboration)`

| Field | Value |
|---|---|
| Full value | source literal `-8.5`; stored IEEE-754 double `-8.5` (`0xc021000000000000`) |
| Unit | Q/T² |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ae199` = data base `0x45` + Pos `0xae154` (`@const_73373`) |
| Instruction address | IMG `0x269a04` |
| Function | `playerPredictPos` (statement ends @IMG `0x269a78`) |
| Lifted statement (full) | `r10 = vector((-0.05 * r9) * L254, 0, -8.5 * L254)` |
| All literals in this statement | `-0.05`@2699a8, `-8.5`@269a04, `0`@269a39 |
| Scope | CORROB |
| Class | value+operation **A**; physical meaning **A** |

```asm
2699fc  fdiv qword ptr [eax]
2699fe  fstp qword ptr [ebp + 0x100]
269a04  fld qword ptr [ebx + 0xae154] ; @const_73373{DOUBLE=-8.5 init=-8.5 hex=00000000000021c0}  <==
269a0a  fmul qword ptr [ebp + 0x100]
269a10  fstp qword ptr [ebp + 0x108]
269a16  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
269a23  pop eax
269a24  fld qword ptr [ebp + 0x108]
269a2a  fmul qword ptr [eax]
```

**Math.** Independent copy of the air step in the trajectory predictor: vector(−0.05·v_lat·1.5, 0, −8.5·1.5), vec_rotate by gravityAngle, vec_add to the predicted velocity, then pos += v·1.5 (@269c3c).

#### P70 — `PREDICT_STEP_TICKS`

| Field | Value |
|---|---|
| Full value | source literal `1.5`; stored IEEE-754 double `1.5` (`0x3ff8000000000000`) |
| Unit | T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ae165` = data base `0x45` + Pos `0xae120` (`@const_73327`) |
| Instruction address | IMG `0x269623` |
| Function | `playerPredictPos` (statement ends @IMG `0x269637`) |
| Lifted statement (full) | `L254 = 1.5` |
| All literals in this statement | `1.5`@269623 |
| Scope | CORROB |
| Class | value+operation **A**; physical meaning **A** |

```asm
269615  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
269622  pop eax
269623  fld qword ptr [ebx + 0xae120] ; @const_73327{DOUBLE=1.5 init=1.5 hex=000000000000f83f}  <==
269629  fmul qword ptr [eax]
26962b  fistp dword ptr [ebp + 0x48]
269631  mov eax, dword ptr [ebp + 0x48]
269637  mov dword ptr [ebp + 0x254], eax
```

**Math.** Predictor step size 1.5 T (prediction only).

### Level-specific (A, not main-map)

#### P03 — `GRAVITY_ACCEL_LEVEL8_TILTED`

| Field | Value |
|---|---|
| Full value | source literal `-6.125`; stored IEEE-754 double `-6.125` (`0xc018800000000000`) |
| Unit | Q/T² |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba19d` = data base `0x45` + Pos `0xba158` (`@const_135048`) |
| Instruction address | IMG `0x3d0381` |
| Function | `playerMove` (statement ends @IMG `0x3d03f5`) |
| Lifted statement (full) | `r549 = vector((-0.025 * L12c) * (*time_step), 0, -6.125 * (*time_step))` |
| All literals in this statement | `-0.025`@3d02ff, `-6.125`@3d0381, `0`@3d03b6 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d0379  fdiv qword ptr [eax]
3d037b  fstp qword ptr [ebp + 0x50e8]
3d0381  fld qword ptr [ebx + 0xba158] ; @const_135048{DOUBLE=-6.125 init=-6.125 hex=00000000008018c0}  <==
3d0387  fmul qword ptr [ebp + 0x50e8]
3d038d  fstp qword ptr [ebp + 0x50f0]
3d0393  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3d03a0  pop eax
3d03a1  fld qword ptr [ebp + 0x50f0]
3d03a7  fmul qword ptr [eax]
```

**Math.** Level 8 (monolith) with gravityAngle ≠ 0 and leaderboardSpecialMode2 ≠ 14: L4cc = (−0.025·v_lat·Δt, 0, −6.125·Δt), L114 = 0 (main-map step skipped).

#### P04 — `AIR_DRAG_LEVEL8_TILTED`

| Field | Value |
|---|---|
| Full value | source literal `-0.025`; stored IEEE-754 double `-0.02500000037252903` (`0xbf999999a0000000`) |
| Unit | 1/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba18d` = data base `0x45` + Pos `0xba148` (`@const_135041`) |
| Instruction address | IMG `0x3d02ff` |
| Function | `playerMove` (statement ends @IMG `0x3d03f5`) |
| Lifted statement (full) | `r549 = vector((-0.025 * L12c) * (*time_step), 0, -6.125 * (*time_step))` |
| All literals in this statement | `-0.025`@3d02ff, `-6.125`@3d0381, `0`@3d03b6 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d02f7  fdiv qword ptr [eax]
3d02f9  fstp qword ptr [ebp + 0x50c0]
3d02ff  fld qword ptr [ebx + 0xba148] ; @const_135041{DOUBLE=-0.02500000037252903 init=-0.025 hex=000000a0999999bf}  <==
3d0305  fmul qword ptr [ebp + 0x50c0]
3d030b  fstp qword ptr [ebp + 0x50c8]
3d0311  mov ecx, 4
3d0316  mov esi, dword ptr [ebx + 0x998c0] ; time_step<POINTER>
3d031c  mov eax, dword ptr [esi]
3d031e  mov dword ptr [ebp + 0x50d0], eax
```

**Math.** Lateral drag factor in the level-8 tilted-gravity step (see P03).

#### P05 — `GRAVITY_ACCEL_TILTED`

| Field | Value |
|---|---|
| Full value | source literal `-4.175`; stored IEEE-754 double `-4.175000190734863` (`0xc010b33340000000`) |
| Unit | Q/T² |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba1cd` = data base `0x45` + Pos `0xba188` (`@const_135297`) |
| Instruction address | IMG `0x3d1ca0` |
| Function | `playerMove` (statement ends @IMG `0x3d1d14`) |
| Lifted statement (full) | `r577 = vector((-0.025 * L12c) * (*time_step), 0, -4.175 * (*time_step))` |
| All literals in this statement | `-0.025`@3d1c1e, `-4.175`@3d1ca0, `0`@3d1cd5 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d1c98  fdiv qword ptr [eax]
3d1c9a  fstp qword ptr [ebp + 0x5554]
3d1ca0  fld qword ptr [ebx + 0xba188] ; @const_135297{DOUBLE=-4.175000190734863 init=-4.175 hex=0000004033b310c0}  <==
3d1ca6  fmul qword ptr [ebp + 0x5554]
3d1cac  fstp qword ptr [ebp + 0x555c]
3d1cb2  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3d1cbf  pop eax
3d1cc0  fld qword ptr [ebp + 0x555c]
3d1cc6  fmul qword ptr [eax]
```

**Math.** level ≠ 9 and gravityAngle ≠ 0 (gravity-helper zones): L4cc = (−0.025·v_lat·Δt, 0, −4.175·Δt), L114 = 0.

#### P06 — `AIR_DRAG_TILTED`

| Field | Value |
|---|---|
| Full value | source literal `-0.025`; stored IEEE-754 double `-0.02500000037252903` (`0xbf999999a0000000`) |
| Unit | 1/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba18d` = data base `0x45` + Pos `0xba148` (`@const_135041`) |
| Instruction address | IMG `0x3d1c1e` |
| Function | `playerMove` (statement ends @IMG `0x3d1d14`) |
| Lifted statement (full) | `r577 = vector((-0.025 * L12c) * (*time_step), 0, -4.175 * (*time_step))` |
| All literals in this statement | `-0.025`@3d1c1e, `-4.175`@3d1ca0, `0`@3d1cd5 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d1c16  fdiv qword ptr [eax]
3d1c18  fstp qword ptr [ebp + 0x552c]
3d1c1e  fld qword ptr [ebx + 0xba148] ; @const_135041{DOUBLE=-0.02500000037252903 init=-0.025 hex=000000a0999999bf}  <==
3d1c24  fmul qword ptr [ebp + 0x552c]
3d1c2a  fstp qword ptr [ebp + 0x5534]
3d1c30  mov ecx, 4
3d1c35  mov esi, dword ptr [ebx + 0x998c0] ; time_step<POINTER>
3d1c3b  mov eax, dword ptr [esi]
3d1c3d  mov dword ptr [ebp + 0x553c], eax
```

**Math.** Lateral drag factor in the gravity-zone step (see P05).

#### P07 — `GRAVITY_ACCEL_WATER`

| Field | Value |
|---|---|
| Full value | integer `-2` (`0xfffffffe`); entered as var `-2·1024 = -2048` where shifted (`shl 10`) |
| Unit | Q/T² |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba0ad` = data base `0x45` + Pos `0xba068` (`@const_134248`) |
| Instruction address | IMG `0x3d10b6` |
| Function | `playerMove` (statement ends @IMG `0x3d1126`) |
| Lifted statement (full) | `r564 = vector((-0.025 * L12c) * (*time_step), 0, -2 * (*time_step))` |
| All literals in this statement | `-0.025`@3d1050, `0xfffffffe`@3d10b6, `0`@3d10e7 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d10ae  mov eax, dword ptr [esi]
3d10b0  mov dword ptr [ebp + 0x5354], eax
3d10b6  mov eax, dword ptr [ebx + 0xba068] ; @const_134248{LONG=-2 init=0xfffffffe hex=feffffff}  <==
3d10bc  shl eax, 0xa
3d10bf  mov dword ptr [ebp + 0x5358], eax
3d10c5  mov eax, dword ptr [ebp + 0x5358]
3d10cb  mov edx, dword ptr [ebp + 0x5354]
3d10d1  imul edx
3d10d3  shrd eax, edx, 0xa
```

**Math.** Level 9 water, not swimming: L4cc = (−0.025·v_lat·Δt, 0, −2·Δt). 32-bit int −2 promoted to var (shl 10).

#### P08 — `LATERAL_SPEED_SCALE_LEVEL9_ZONE`

| Field | Value |
|---|---|
| Full value | source literal `0.75`; stored IEEE-754 double `0.75` (`0x3fe8000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9db9` = data base `0x45` + Pos `0xb9d74` (`@const_132339`) |
| Instruction address | IMG `0x3d1fa9` |
| Function | `playerMove` (statement ends @IMG `0x3d1fcc`) |
| Lifted statement (full) | `L12c = L12c * 0.75` |
| All literals in this statement | `0.75`@3d1fa9 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d1f9b  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3d1fa8  pop eax
3d1fa9  fld qword ptr [ebx + 0xb9d74] ; @const_132339{DOUBLE=0.75 init=0.75 hex=000000000000e83f}  <==
3d1faf  fmul qword ptr [eax]
3d1fb1  fistp dword ptr [ebp + 0x55d0]
3d1fb7  mov eax, dword ptr [ebp + 0x12c]
3d1fbd  mov edx, dword ptr [ebp + 0x55d0]
3d1fc3  imul edx
3d1fc5  shrd eax, edx, 0xa
```

**Math.** Level 9 position zones: L12c ← 0.75·L12c before the drag term.

#### W01 — `SWIM_TARGET_SPEED`

| Field | Value |
|---|---|
| Full value | integer `60` (`0x0000003c`); entered as var `60·1024 = 61440` where shifted (`shl 10`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9d19` = data base `0x45` + Pos `0xb9cd4` (`@const_132150`) |
| Instruction address | IMG `0x3d0536` |
| Function | `playerMove` (statement ends @IMG `0x3d057e`) |
| Lifted statement (full) | `r552 = vector(0, 0, 60)` |
| All literals in this statement | `60`@3d0536, `0`@3d054c, `0`@3d0562 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d0534  mov dword ptr [esi], eax
3d0536  mov eax, dword ptr [ebx + 0xb9cd4] ; @const_132150{LONG=60 init=60 hex=3c000000}  <==
3d053c  shl eax, 0xa
3d053f  mov dword ptr [ebp + 0x512c], eax
3d0545  mov eax, dword ptr [ebp + 0x512c]
3d054b  push eax
3d054c  mov eax, dword ptr [ebx + 0xb97fc] ; @const_130172{LONG=0 init=0 hex=00000000}
3d0552  shl eax, 0xa
```

**Math.** Level 9 swimming (button 4 held): target = rotate((0,0,60), angle), z ×1.25 (sign-dependent) and ±5.

#### W02 — `SWIM_ACCEL_LIMIT_MAX`

| Field | Value |
|---|---|
| Full value | integer `30` (`0x0000001e`); entered as var `30·1024 = 30720` where shifted (`shl 10`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9a99` = data base `0x45` + Pos `0xb9a54` (`@const_130591`) |
| Instruction address | IMG `0x3d0a0b` |
| Function | `playerMove` (statement ends @IMG `0x3d0a44`) |
| Lifted statement (full) | `r558 = clamp(r557, 20, 30)` |
| All literals in this statement | `30`@3d0a0b, `20`@3d0a21 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d09ff  add esp, 4
3d0a05  mov dword ptr [ebp + 0x51dc], eax
3d0a0b  mov eax, dword ptr [ebx + 0xb9a54] ; @const_130591{LONG=30 init=30 hex=1e000000}  <==
3d0a11  shl eax, 0xa
3d0a14  mov dword ptr [ebp + 0x51e0], eax
3d0a1a  mov eax, dword ptr [ebp + 0x51e0]
3d0a20  push eax
3d0a21  mov eax, dword ptr [ebx + 0xb9da0] ; @const_132449{LONG=20 init=20 hex=14000000}
3d0a27  shl eax, 0xa
```

**Math.** Δ = target − v limited to length clamp(|v|, 20, 30) (vec_limit).

#### W03 — `SWIM_RESPONSE`

| Field | Value |
|---|---|
| Full value | source literal `0.5`; stored IEEE-754 double `0.5` (`0x3fe0000000000000`) |
| Unit | 1/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b986d` = data base `0x45` + Pos `0xb9828` (`@const_130217`) |
| Instruction address | IMG `0x3d0aea` |
| Function | `playerMove` (statement ends @IMG `0x3d0b7a`) |
| Lifted statement (full) | `r560 = vec_scale(ADDR(L4c0), (0.5 / (1 + arg0.jumpTimer)) * (*time_step))` |
| All literals in this statement | `1`@3d0aab, `0.5`@3d0aea |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d0ae2  fdiv qword ptr [eax]
3d0ae4  fstp qword ptr [ebp + 0x5200]
3d0aea  fld qword ptr [ebx + 0xb9828] ; @const_130217{DOUBLE=0.5 init=0.5 hex=000000000000e03f}  <==
3d0af0  fdiv qword ptr [ebp + 0x5200]
3d0af6  fstp qword ptr [ebp + 0x5208]
3d0afc  mov ecx, 4
3d0b01  mov esi, dword ptr [ebx + 0x998c0] ; time_step<POINTER>
3d0b07  mov eax, dword ptr [esi]
3d0b09  mov dword ptr [ebp + 0x5210], eax
```

**Math.** v += Δ·0.5·Δt/(1 + jumpTimer).

#### R02 — `SUPERJUMP_LOAD`

| Field | Value |
|---|---|
| Full value | integer `300` (`0x0000012c`); entered as var `300·1024 = 307200` where shifted (`shl 10`) |
| Unit | L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9ca9` = data base `0x45` + Pos `0xb9c64` (`@const_131759`) |
| Instruction address | IMG `0x3db5de` |
| Function | `playerMove` (statement ends @IMG `0x3db5f3`) |
| Lifted statement (full) | `heroSpringLoadedMin = 300` |
| All literals in this statement | `300`@3db5de |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3db5d5  add byte ptr [ebx], al
3db5d7  ror dword ptr [ecx + 0x6d5485], 0
3db5de  mov eax, dword ptr [ebx + 0xb9c64] ; @const_131759{LONG=300 init=300 hex=2c010000}  <==
3db5e4  shl eax, 0xa
3db5e7  mov dword ptr [ebp + 0x6d58], eax
3db5ed  mov eax, dword ptr [ebp + 0x6d58]
3db5f3  mov dword ptr [ebx + 0x7d9ec], eax ; heroSpringLoadedMin<FIXED>
```

**Math.** superJump region: L_min = 300, L_max = L_min.

#### E01 — `BOUNCE_ENTITY_PUSH`

| Field | Value |
|---|---|
| Full value | integer `30` (`0x0000001e`); entered as var `30·1024 = 30720` where shifted (`shl 10`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9a99` = data base `0x45` + Pos `0xb9a54` (`@const_130591`) |
| Instruction address | IMG `0x3ded92` |
| Function | `playerMove` (statement ends @IMG `0x3dedbb`) |
| Lifted statement (full) | `L38 = 30 - ((!(!(int(L184.skill[98] & 128)))) * 20)` |
| All literals in this statement | `98`@3ded09, `0x80`@3ded33, `20`@3ded85, `30`@3ded92 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3ded85  imul eax, dword ptr [ebx + 0xb9da0] ; @const_132449{LONG=20 init=20 hex=14000000}
3ded8c  mov dword ptr [ebp + 0x75cc], eax
3ded92  mov eax, dword ptr [ebx + 0xb9a54] ; @const_130591{LONG=30 init=30 hex=1e000000}  <==
3ded98  mov ecx, dword ptr [ebp + 0x75cc]
3ded9e  sub eax, ecx
3deda0  mov dword ptr [ebp + 0x75d0], eax
3deda6  mov eax, dword ptr [ebp + 0x75d0]
3dedac  shl eax, 0xa
3dedaf  mov dword ptr [ebp + 0x75d4], eax
```

**Math.** Landing on an entity with skill[98] & 144: push = normalize(me − ent, L38), L38 = 30 − 20·(skill[98] & 128) (level 10: min(L38, 25); +7 in one level-8 case); v_x += push_x.

#### E02 — `BOUNCE_ENTITY_VZ_CAP`

| Field | Value |
|---|---|
| Full value | integer `170` (`0x000000aa`); entered as var `170·1024 = 174080` where shifted (`shl 10`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba421` = data base `0x45` + Pos `0xba3dc` (`@const_137461`) |
| Instruction address | IMG `0x3df566` |
| Function | `playerMove` (statement ends @IMG `0x3df652`) |
| Lifted statement (full) | `r776 = clamp(L484.z, (leaderboardSpecialMode2 == 14) * -200, (170 - arg0.speed.z) + (((level_current == 9) \| (level_current >= 100)) * 100))` |
| All literals in this statement | `14`@3df4d8, `0xffffff38`@3df506, `170`@3df566, `9`@3df589, `100`@3df5b1, `100`@3df5f8 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **A** |

```asm
3df55e  mov eax, dword ptr [esi]
3df560  mov dword ptr [ebp + 0x76e4], eax
3df566  mov eax, dword ptr [ebx + 0xba3dc] ; @const_137461{LONG=170 init=170 hex=aa000000}  <==
3df56c  shl eax, 0xa
3df56f  mov dword ptr [ebp + 0x76e8], eax
3df575  mov eax, dword ptr [ebp + 0x76e8]
3df57b  mov ecx, dword ptr [ebp + 0x76e4]
3df581  sub eax, ecx
3df583  mov dword ptr [ebp + 0x76ec], eax
```

**Math.** Same contact: v_z += clamp(push_z, −200·(mode == 14), 170 − v_z + 100·(level 9 | level ≥ 100)).

### Mode-specific (A, special modes only)

#### P10 — `SLIDE_GAIN_SPECIALMODE`

| Field | Value |
|---|---|
| Full value | source literal `0.2`; stored IEEE-754 double `0.20000000298023224` (`0x3fc99999a0000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9f1d` = data base `0x45` + Pos `0xb9ed8` (`@const_132940`) |
| Instruction address | IMG `0x3d310d` |
| Function | `playerMove` (statement ends @IMG `0x3d3143`) |
| Lifted statement (full) | `L3a8 = 1.0 - (0.2 * L30)` |
| All literals in this statement | `0.2`@3d310d, `1.0`@3d311f |
| Scope | MODE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d3101  fild dword ptr [ebp + 0x30]
3d3107  fstp qword ptr [ebp + 0x58c0]
3d310d  fld qword ptr [ebx + 0xb9ed8] ; @const_132940{DOUBLE=0.20000000298023224 init=0.2 hex=000000a09999c93f}  <==
3d3113  fmul qword ptr [ebp + 0x58c0]
3d3119  fstp qword ptr [ebp + 0x58c8]
3d311f  fld qword ptr [ebx + 0xb9b74] ; @const_131371{DOUBLE=1.0 init=1.0 hex=000000000000f03f}
3d3125  fsub qword ptr [ebp + 0x58c8]
3d312b  fstp qword ptr [ebp + 0x58d0]
3d3131  fld qword ptr [ebp + 0x58d0]
```

**Math.** L3a8 = 1.0 − 0.2·L30 (L30 = main map AND leaderboardSpecialMode2 == 3). Multiplies slideSpeed in the displacement. L3a8 = 1 in normal play.

#### P30 — `SPRING_MIN_OUTRO_BONUS`

| Field | Value |
|---|---|
| Full value | integer `20` (`0x00000014`); entered as var `20·1024 = 20480` where shifted (`shl 10`) |
| Unit | L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9de5` = data base `0x45` + Pos `0xb9da0` (`@const_132449`) |
| Instruction address | IMG `0x3dbd2f` |
| Function | `playerMove` (statement ends @IMG `0x3dbe7e`) |
| Lifted statement (full) | `if !(((arg0.heroSpringLoaded < (heroSpringLoadedMin + ((!(!(outroPlaying))) * 20))) \| (r712 && (!(outroPlaying)))) && (arg0.heroSpringLoaded < arg0.heroSpringLoadedMax)) goto label_3dc2dd` |
| All literals in this statement | `20`@3dbd2f, `4`@3dbd78 |
| Scope | MODE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3dbd23  mov dword ptr [ebp + 0x6e70], eax
3dbd29  mov eax, dword ptr [ebp + 0x6e70]
3dbd2f  imul eax, dword ptr [ebx + 0xb9da0] ; @const_132449{LONG=20 init=20 hex=14000000}  <==
3dbd36  mov dword ptr [ebp + 0x6e74], eax
3dbd3c  mov eax, dword ptr [ebp + 0x6e74]
3dbd42  shl eax, 0xa
3dbd45  mov dword ptr [ebp + 0x6e78], eax
3dbd4b  mov eax, dword ptr [ebx + 0x7d9ec] ; heroSpringLoadedMin<FIXED>
3dbd51  mov ecx, dword ptr [ebp + 0x6e78]
```

**Math.** Charge condition term L_min + 20·(outroPlaying ≠ 0): only during the outro sequence.

#### P43b — `LAUNCH_SPIN_SPECIAL_HALF`

| Field | Value |
|---|---|
| Full value | source literal `0.5`; stored IEEE-754 double `0.5` (`0x3fe0000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b986d` = data base `0x45` + Pos `0xb9828` (`@const_130217`) |
| Instruction address | IMG `0x3e00ef` |
| Function | `playerMove` (statement ends @IMG `0x3e0185`) |
| Lifted statement (full) | `arg0.heroTurnSpeed = arg0.heroTurnSpeed + (((r786 * 0.1245) - (((r790 * r791) * 1.5) * 0.25)) * (1 - (0.5 * (collisionSpecialTypeResult == 1))))` |
| All literals in this statement | `0.75`@3dff66, `1.5`@3e0008, `0.1245`@3e007e, `0.25`@3e00ac, `1`@3e00d0, `0.5`@3e00ef, `1`@3e0101 |
| Scope | MODE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3e00e3  fild dword ptr [ebp + 0x78ec]
3e00e9  fstp qword ptr [ebp + 0x78f0]
3e00ef  fld qword ptr [ebx + 0xb9828] ; @const_130217{DOUBLE=0.5 init=0.5 hex=000000000000e03f}  <==
3e00f5  fmul qword ptr [ebp + 0x78f0]
3e00fb  fstp qword ptr [ebp + 0x78f8]
3e0101  fild dword ptr [ebx + 0xb9804] ; @const_130182{LONG=1 init=1 hex=01000000}
3e0107  fstp qword ptr [ebp + 0x7900]
3e010d  fld qword ptr [ebp + 0x7900]
3e0113  fsub qword ptr [ebp + 0x78f8]
```

**Math.** Whole kick ×(1 − 0.5·(collisionSpecialTypeResult == 1)): halved on special-collision entities.

#### P56 — `ICE_SLIDE_Z_SCALE_SPECIAL`

| Field | Value |
|---|---|
| Full value | source literal `0.667`; stored IEEE-754 double `0.6669999957084656` (`0x3fe5581060000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba0c9` = data base `0x45` + Pos `0xba084` (`@const_134361`) |
| Instruction address | IMG `0x3cbc88` |
| Function | `playerMove` (statement ends @IMG `0x3cbcc2`) |
| Lifted statement (full) | `L44ec.z = L44ec.z * 0.667` |
| All literals in this statement | `0.667`@3cbc88 |
| Scope | MODE |
| Class | value+operation **A**; physical meaning **A** |

```asm
3cbc7a  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3cbc87  pop eax
3cbc88  fld qword ptr [ebx + 0xba084] ; @const_134361{DOUBLE=0.6669999957084656 init=0.667 hex=000000601058e53f}  <==
3cbc8e  fmul qword ptr [eax]
3cbc90  fistp dword ptr [ebp + 0x4574]
3cbc96  mov eax, dword ptr [ebp + 0x4570]
3cbc9c  mov edx, dword ptr [ebp + 0x4574]
3cbca2  imul edx
3cbca4  shrd eax, edx, 0xa
```

**Math.** If level 10 or L30: t_z ← 0.667·t_z.

#### S01 — `SHOT_JUMP_SPEED_KEEP`

| Field | Value |
|---|---|
| Full value | source literal `0.275`; stored IEEE-754 double `0.2750000059604645` (`0x3fd19999a0000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ad03d` = data base `0x45` + Pos `0xacff8` (`@const_67853`) |
| Instruction address | IMG `0x24a118` |
| Function | `playerDoubleJumpDo` (statement ends @IMG `0x24a152`) |
| Lifted statement (full) | `playerDataLocal.speed.x = playerDataLocal.speed.x * 0.275` |
| All literals in this statement | `0.275`@24a118 |
| Scope | MODE |
| Class | value+operation **A**; physical meaning **A** |

```asm
24a10a  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
24a117  pop eax
24a118  fld qword ptr [ebx + 0xacff8] ; @const_67853{DOUBLE=0.2750000059604645 init=0.275 hex=000000a09999d13f}  <==
24a11e  fmul qword ptr [eax]
24a120  fistp dword ptr [ebp + 0xc0]
24a126  mov eax, dword ptr [ebp + 0xbc]
24a12c  mov edx, dword ptr [ebp + 0xc0]
24a132  imul edx
24a134  shrd eax, edx, 0xa
```

**Math.** playerDoubleJumpDo (shot jump, level 9 / dungeon modes): v ← 0.275·v.

#### S02 — `SHOT_JUMP_KICK`

| Field | Value |
|---|---|
| Full value | source literal `0.65`; stored IEEE-754 double `0.6499999761581421` (`0x3fe4ccccc0000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ad049` = data base `0x45` + Pos `0xad004` (`@const_67867`) |
| Instruction address | IMG `0x24a276` |
| Function | `playerDoubleJumpDo` (statement ends @IMG `0x24a2ce`) |
| Lifted statement (full) | `L1c.x = -dungeonActionVector.x * 0.65` |
| All literals in this statement | `0.65`@24a276 |
| Scope | MODE |
| Class | value+operation **A**; physical meaning **A** |

```asm
24a26a  fstp qword ptr [ebp + 0xe4]
24a270  fld qword ptr [ebp + 0xe4]
24a276  fmul qword ptr [ebx + 0xad004] ; @const_67867{DOUBLE=0.6499999761581421 init=0.65 hex=000000c0cccce43f}  <==
24a27c  fstp qword ptr [ebp + 0xec]
24a282  mov ecx, 4
24a287  mov esi, dword ptr [ebp + 0xd4]
24a28d  mov eax, dword ptr [esi]
24a28f  mov dword ptr [ebp + 0xf4], eax
24a295  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
```

**Math.** Then v += k with k = −0.65·(rotate((0,0,−100), angle)); k_z ×0.5 if k_z < 0 else ×1.2 (in the gravity frame).

#### R01 — `RESET_SPRING_MAX_CAP`

| Field | Value |
|---|---|
| Full value | integer `150` (`0x00000096`); entered as var `150·1024 = 153600` where shifted (`shl 10`) |
| Unit | L |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ae22d` = data base `0x45` + Pos `0xae1e8` (`@const_73517`) |
| Instruction address | IMG `0x26a97e` |
| Function | `playerResetSpeed` (statement ends @IMG `0x26a9a1`) |
| Lifted statement (full) | `r3 = minv(playerDataLocal.heroSpringLoadedMax, 150)` |
| All literals in this statement | `150`@26a97e |
| Scope | MODE |
| Class | value+operation **A**; physical meaning **A** |

```asm
26a976  mov eax, dword ptr [esi]
26a978  mov dword ptr [ebp + 0xe8], eax
26a97e  mov eax, dword ptr [ebx + 0xae1e8] ; @const_73517{LONG=150 init=150 hex=96000000}  <==
26a984  shl eax, 0xa
26a987  mov dword ptr [ebp + 0xec], eax
26a98d  mov eax, dword ptr [ebp + 0xec]
26a993  push eax
26a994  mov eax, dword ptr [ebp + 0xe8]
26a99a  push eax
```

**Math.** playerResetSpeed, only while the player is in the start area (pos.x < −10624): v_x = min(v_x, 0), v_z = min(v_z, 0), v_z ≥ −150, z capped at spawn + 512, L_max = min(L_max, 150), L = min(L, L_max).

### Unit definition and non-physics thresholds (A, but not physics)

#### U01 — `QUANTS_PER_DISPLAY_METER`

| Field | Value |
|---|---|
| Full value | integer `52` (`0x00000034`); entered as var `52·1024 = 53248` where shifted (`shl 10`) |
| Unit | Q per display-metre |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba679` = data base `0x45` + Pos `0xba634` (`@const_139898`) |
| Instruction address | IMG `0x3eeba5` |
| Function | `playerMove` (statement ends @IMG `0x3eebe5`) |
| Lifted statement (full) | `L230 = int((L204.z - localPlayerLastPogoJumpPos.z) / 52)` |
| All literals in this statement | `52`@3eeba5 |
| Scope | UNIT_DEF |
| Class | value+operation **A**; physical meaning **A** |

```asm
3eeb9d  sub eax, ecx
3eeb9f  mov dword ptr [ebp + 0x9d74], eax
3eeba5  mov eax, dword ptr [ebx + 0xba634] ; @const_139898{LONG=52 init=52 hex=34000000}  <==
3eebab  shl eax, 0xa
3eebae  mov dword ptr [ebp + 0x9d78], eax
3eebb4  mov eax, dword ptr [ebp + 0x9d74]
3eebba  mov ecx, dword ptr [ebp + 0x9d78]
3eebc0  mov edx, eax
3eebc2  sar edx, 0x16
```

**Math.** HUD: L230 = int((me.z − lastPogoJumpPos.z)/52) printed with "%dm". Defines the game display metre = 52 Q. Not a physics constant.

#### U02 — `JUMP_HIGH_ACHIEVEMENT_M`

| Field | Value |
|---|---|
| Full value | integer `50` (`0x00000032`); entered as var `50·1024 = 51200` where shifted (`shl 10`) |
| Unit | display m |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b99fd` = data base `0x45` + Pos `0xb99b8` (`@const_130470`) |
| Instruction address | IMG `0x3eed7d` |
| Function | `playerMove` (statement ends @IMG `0x3eedf7`) |
| Lifted statement (full) | `if !((L230 >= 50) && (!(r996))) goto label_3eee20` |
| All literals in this statement | `50`@3eed7d, `0`@3eedbe |
| Scope | NONPHYS |
| Class | value+operation **A**; physical meaning **A** |

```asm
3eed71  add esp, 0x10
3eed77  mov ecx, dword ptr [ebp + 0x230]
3eed7d  mov edx, dword ptr [ebx + 0xb99b8] ; @const_130470{LONG=50 init=50 hex=32000000}  <==
3eed83  xor eax, eax
3eed85  cmp ecx, edx
3eed87  setge al
3eed8a  mov dword ptr [ebp + 0x9dc0], eax
3eed90  mov eax, ebx
3eed92  add eax, 0xba63c
```

**Math.** "jump_high" achievement when L230 ≥ 50.

#### U03 — `JUMP_DEGREES_ACHIEVEMENT`

| Field | Value |
|---|---|
| Full value | integer `1080` (`0x00000438`); entered as var `1080·1024 = 1105920` where shifted (`shl 10`) |
| Unit | deg |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba2f9` = data base `0x45` + Pos `0xba2b4` (`@const_136525`) |
| Instruction address | IMG `0x3d95cd` |
| Function | `playerMove` (statement ends @IMG `0x3d95fd`) |
| Lifted statement (full) | `r680 = abs@1(r679 - 1080)` |
| All literals in this statement | `1080`@3d95cd |
| Scope | NONPHYS |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d95c1  mov eax, dword ptr [ebp + 0x6890]
3d95c7  mov dword ptr [ebp + 0x40], eax
3d95cd  mov eax, dword ptr [ebx + 0xba2b4] ; @const_136525{LONG=1080 init=1080 hex=38040000}  <==
3d95d3  shl eax, 0xa
3d95d6  mov dword ptr [ebp + 0x6894], eax
3d95dc  mov eax, dword ptr [ebp + 0x40]
3d95e2  mov ecx, dword ptr [ebp + 0x6894]
3d95e8  sub eax, ecx
3d95ea  mov dword ptr [ebp + 0x6898], eax
```

**Math.** "jump_degrees": r680 = |r679 − 1080| with r679 = |angle − lastJumpAngle| on the landing frame.

#### U04 — `JUMP_DEGREES_TOLERANCE`

| Field | Value |
|---|---|
| Full value | source literal `1.9`; stored IEEE-754 double `1.899999976158142` (`0x3ffe666660000000`) |
| Unit | deg |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba2fd` = data base `0x45` + Pos `0xba2b8` (`@const_136529`) |
| Instruction address | IMG `0x3d962d` |
| Function | `playerMove` (statement ends @IMG `0x3d9763`) |
| Lifted statement (full) | `if !(((r680 < 1.9) && (playerAngleShowTimer > 0)) && (((!(level_current)) \| (level_current == 8)) \| (level_current == 9))) goto label_3d9806` |
| All literals in this statement | `1080`@3d95cd, `1.9`@3d962d, `0`@3d964e, `0`@3d9693, `8`@3d96bb, `9`@3d96fc |
| Scope | NONPHYS |
| Class | value+operation **A**; physical meaning **A** |

```asm
3d9621  fstp qword ptr [ebp + 0x68a0]
3d9627  fld qword ptr [ebp + 0x68a0]
3d962d  fcomp qword ptr [ebx + 0xba2b8] ; @const_136529{DOUBLE=1.899999976158142 init=1.9 hex=000000606666fe3f}  <==
3d9633  mov dword ptr [ebp + 0x68a8], 1
3d963d  fnstsw ax
3d963f  test ah, 1
3d9642  jne 0x3d964e
3d9644  mov dword ptr [ebp + 0x68a8], 0
3d964e  mov eax, dword ptr [ebx + 0xb97fc] ; @const_130172{LONG=0 init=0 hex=00000000}
```

**Math.** Tolerance: r680 < 1.9.


---

## 7. INFERRED B — value and operation are A, physical meaning needs engine-routine semantics

Each block gives the A evidence. The **Math** line names the engine assumption that makes the meaning B. The assumptions are:
- `vec_rotate` tilt convention R(θ)(1,0,0) = (cos θ, 0, sin θ);
- `vec_lerp(a, b, f) = a + (b − a)·f`;
- `ang()` wraps to ±180;
- `sinv` / `asinv` work in degrees;
- the entity bbox is what `c_move`/`c_rotate` collide with;
- the `bounce` global is the `c_move` reflection vector;
- `time_factor` multiplies `time_step`;
- PC scancode 57 = Space.

### A value, B meaning (needs engine-routine semantics)

#### P14 — `GROUND_STICK_SPEED`

| Field | Value |
|---|---|
| Full value | source literal `-24`; stored IEEE-754 double `-24.0` (`0xc038000000000000`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba179` = data base `0x45` + Pos `0xba134` (`@const_134990`) |
| Instruction address | IMG `0x3cfe00` |
| Function | `playerMove` (statement ends @IMG `0x3cfe1b`) |
| Lifted statement (full) | `r540 = vector(-24, 0, 0)` |
| All literals in this statement | `0`@3cfdc6, `0`@3cfddc, `-24`@3cfe00 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3cfdf2  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3cfdff  pop eax
3cfe00  fld qword ptr [ebx + 0xba134] ; @const_134990{DOUBLE=-24.0 init=-24 hex=00000000000038c0}  <==
3cfe06  fmul qword ptr [eax]
3cfe08  fistp dword ptr [ebp + 0x4ff0]
3cfe0e  mov eax, dword ptr [ebp + 0x4ff0]
3cfe14  push eax
3cfe15  mov eax, dword ptr [ebx + 0x9c038] ; vector<POINTER>
3cfe1b  call eax
```

**Math.** Grounded: L4cc = vector(−24, 0, 0) → vec_rotate by (0, surfaceNormal, 0) → arg0.speed.x/z = L4cc.x/z. With surfaceNormal = atan2v(n.z, n.x) this equals −24·n (constant velocity into the surface) under the Gamestudio tilt convention R(θ)(1,0,0) = (cos θ, 0, sin θ). Magnitude 24: A. Direction −n: B (rotation convention; the opposite sign would launch the player off flat ground every frame).

#### P18 — `ROTATE_HULL_LIFT`

| Field | Value |
|---|---|
| Full value | integer `16` (`0x00000010`); entered as var `16·1024 = 16384` where shifted (`shl 10`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9d4d` = data base `0x45` + Pos `0xb9d08` (`@const_132243`) |
| Instruction address | IMG `0x3cf502` |
| Function | `playerMove` (statement ends @IMG `0x3cf536`) |
| Lifted statement (full) | `L204.min_z = L204.min_z + 16` |
| All literals in this statement | `16`@3cf502 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3cf4fa  mov eax, dword ptr [esi]
3cf4fc  mov dword ptr [ebp + 0x4eb8], eax
3cf502  mov eax, dword ptr [ebx + 0xb9d08] ; @const_132243{LONG=16 init=16 hex=10000000}  <==
3cf508  shl eax, 0xa
3cf50b  mov dword ptr [ebp + 0x4ebc], eax
3cf511  mov eax, dword ptr [ebp + 0x4eb8]
3cf517  mov ecx, dword ptr [ebp + 0x4ebc]
3cf51d  add eax, ecx
3cf51f  mov dword ptr [ebp + 0x4eb8], eax
```

**Math.** me.min_z += 16 before c_rotate and −16 after (@3cf85f): the hull bottom is raised 16 Q while the rotation is collision-checked (effect inside c_rotate = engine).

#### P22 — `GROUND_PROBE_BOX_HALFSIZE`

| Field | Value |
|---|---|
| Full value | integer `4` (`0x00000004`); entered as var `4·1024 = 4096` where shifted (`shl 10`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b99dd` = data base `0x45` + Pos `0xb9998` (`@const_130436`) |
| Instruction address | IMG `0x3d84f6` |
| Function | `playerMove` (statement ends @IMG `0x3d8544`) |
| Lifted statement (full) | `r669 = pogo_trace(L204, ADDR(L204.x), ADDR(L460), 613, 4)` |
| All literals in this statement | `4`@3d84f6, `0x265`@3d850c |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3d84ee  mov eax, dword ptr [esi]
3d84f0  mov dword ptr [ebp + 0x6604], eax
3d84f6  mov eax, dword ptr [ebx + 0xb9998] ; @const_130436{LONG=4 init=4 hex=04000000}  <==
3d84fc  shl eax, 0xa
3d84ff  mov dword ptr [ebp + 0x6608], eax
3d8505  mov eax, dword ptr [ebp + 0x6608]
3d850b  push eax
3d850c  mov eax, dword ptr [ebx + 0xba288] ; @const_136174{LONG=613 init=0x265 hex=65020000}
3d8512  shl eax, 0xa
```

**Math.** pogo_trace sets its helper entity min = −4 and max = +4 on x, y, z (vec_fill @1e4d7d/1e4dcc). Then c_trace from the helper ⇒ the ground probe is a box trace of half-size 4 Q (box behaviour of c_trace = engine).

#### P31 — `INPUT_CHARGE_BUTTON`

| Field | Value |
|---|---|
| Full value | integer `4` (`0x00000004`); entered as var `4·1024 = 4096` where shifted (`shl 10`) |
| Unit | button index |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b99dd` = data base `0x45` + Pos `0xb9998` (`@const_130436`) |
| Instruction address | IMG `0x3dbd78` |
| Function | `playerMove` (statement ends @IMG `0x3dbe7e`) |
| Lifted statement (full) | `if !(((arg0.heroSpringLoaded < (heroSpringLoadedMin + ((!(!(outroPlaying))) * 20))) \| (r712 && (!(outroPlaying)))) && (arg0.heroSpringLoaded < arg0.heroSpringLoadedMax)) goto label_3dc2dd` |
| All literals in this statement | `20`@3dbd2f, `4`@3dbd78 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3dbd6f  setl al
3dbd72  mov dword ptr [ebp + 0x6e80], eax
3dbd78  mov eax, dword ptr [ebx + 0xb9998] ; @const_130436{LONG=4 init=4 hex=04000000}  <==
3dbd7e  push eax
3dbd7f  call 0x1dd748
3dbd84  add esp, 4
3dbd8a  mov dword ptr [ebp + 0x6e84], eax
3dbd90  mov ecx, dword ptr [ebx + 0x969d0] ; outroPlaying<FIXED>
3dbd96  xor eax, eax
```

**Math.** inputIsDown(4) in the charge condition. Button 4 default sources (inputButtonSetDefault): keyboard code 57, joystick 257, pad 12. Code 57 = Space in the PC scancode table: B.

#### P35 — `NORMAL_ANGLE_OFFSET`

| Field | Value |
|---|---|
| Full value | integer `90` (`0x0000005a`); entered as var `90·1024 = 92160` where shifted (`shl 10`) |
| Unit | deg |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9d29` = data base `0x45` + Pos `0xb9ce4` (`@const_132176`) |
| Instruction address | IMG `0x3dd407` |
| Function | `playerMove` (statement ends @IMG `0x3dd47e`) |
| Lifted statement (full) | `r745 = ang((r744 - 90) - arg0.angle)` |
| All literals in this statement | `90`@3dd407 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3dd3fb  add esp, 8
3dd401  mov dword ptr [ebp + 0x71e4], eax
3dd407  mov eax, dword ptr [ebx + 0xb9ce4] ; @const_132176{LONG=90 init=90 hex=5a000000}  <==
3dd40d  shl eax, 0xa
3dd410  mov dword ptr [ebp + 0x71e8], eax
3dd416  mov eax, dword ptr [ebp + 0x71e4]
3dd41c  mov ecx, dword ptr [ebp + 0x71e8]
3dd422  sub eax, ecx
3dd424  mov dword ptr [ebp + 0x71ec], eax
```

**Math.** r745 = ang(atan2v(n.z, n.x) − 90 − angle): signed difference between the surface-normal direction (converted to the stick-angle convention by −90) and the body angle. ang() wrapping to ±180: B.

#### P48 — `BONK_DIR_REFLECT_WEIGHT`

| Field | Value |
|---|---|
| Full value | source literal `0.9`; stored IEEE-754 double `0.8999999761581421` (`0x3fecccccc0000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9cfd` = data base `0x45` + Pos `0xb9cb8` (`@const_132070`) |
| Instruction address | IMG `0x3e5845` |
| Function | `playerMove` (statement ends @IMG `0x3e5867`) |
| Lifted statement (full) | `r860 = vec_normalize(bounce, 0.9)` |
| All literals in this statement | `0.9`@3e5845 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3e5837  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3e5844  pop eax
3e5845  fld qword ptr [ebx + 0xb9cb8] ; @const_132070{DOUBLE=0.8999999761581421 init=0.9 hex=000000c0ccccec3f}  <==
3e584b  fmul qword ptr [eax]
3e584d  fistp dword ptr [ebp + 0x86cc]
3e5853  mov eax, dword ptr [ebp + 0x86cc]
3e5859  push eax
3e585a  mov eax, dword ptr [ebx + 0x7a610] ; bounce<POINTER>
3e5860  push eax
```

**Math.** bounce.y = 0; vec_normalize(bounce, 0.9); bounce += n (L298, unit length) ⇒ direction d = 0.9·r̂ + n before the final rescale. r̂ comes from the engine `bounce` vector in the usual case (B: Gamestudio documents it as the c_move reflection vector).

#### P55 — `ICE_SLIDE_TARGET_SPEED`

| Field | Value |
|---|---|
| Full value | integer `48` (`0x00000030`); entered as var `48·1024 = 49152` where shifted (`shl 10`) |
| Unit | Q/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9ba1` = data base `0x45` + Pos `0xb9b5c` (`@const_131276`) |
| Instruction address | IMG `0x3cbba9` |
| Function | `playerMove` (statement ends @IMG `0x3cbbed`) |
| Lifted statement (full) | `r466 = vec_normalize(ADDR(L44ec), 48 - (((level_current == 10) \| L30) * 24))` |
| All literals in this statement | `10`@3cbb55, `24`@3cbb9c, `48`@3cbba9 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3cbb9c  imul eax, dword ptr [ebx + 0xba080] ; @const_134349{LONG=24 init=24 hex=18000000}
3cbba3  mov dword ptr [ebp + 0x4550], eax
3cbba9  mov eax, dword ptr [ebx + 0xb9b5c] ; @const_131276{LONG=48 init=48 hex=30000000}  <==
3cbbaf  mov ecx, dword ptr [ebp + 0x4550]
3cbbb5  sub eax, ecx
3cbbb7  mov dword ptr [ebp + 0x4554], eax
3cbbbd  mov eax, dword ptr [ebp + 0x4554]
3cbbc3  shl eax, 0xa
3cbbc6  mov dword ptr [ebp + 0x4558], eax
```

**Math.** Slide mode (flags & 1): g = vec_rotate((0,0,−1),(0,gravityAngle,0)); t = vec_normalize(surfaceNormalVector + g, 48 − 24·(level == 10 | L30)). Target slide velocity of magnitude 48 along (n + g) = down-slope direction (direction meaning B, needs vec_normalize/vec_rotate semantics; magnitude A).

#### P59 — `SLIDE_DECAY_RATE`

| Field | Value |
|---|---|
| Full value | source literal `0.5`; stored IEEE-754 double `0.5` (`0x3fe0000000000000`) |
| Unit | 1/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b986d` = data base `0x45` + Pos `0xb9828` (`@const_130217`) |
| Instruction address | IMG `0x3cca3b` |
| Function | `playerMove` (statement ends @IMG `0x3cca8b`) |
| Lifted statement (full) | `r490 = vec_lerp(ADDR(arg0.slideSpeed), ADDR(arg0.slideSpeed), nullvector, 0.5 * (*time_step))` |
| All literals in this statement | `0.5`@3cca3b |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3cca33  fdiv qword ptr [eax]
3cca35  fstp qword ptr [ebp + 0x47cc]
3cca3b  fld qword ptr [ebx + 0xb9828] ; @const_130217{DOUBLE=0.5 init=0.5 hex=000000000000e03f}  <==
3cca41  fmul qword ptr [ebp + 0x47cc]
3cca47  fstp qword ptr [ebp + 0x47d4]
3cca4d  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3cca5a  pop eax
3cca5b  fld qword ptr [ebp + 0x47d4]
3cca61  fmul qword ptr [eax]
```

**Math.** Not in slide mode, grounded and |slide| ≥ 0.25: vec_lerp(slide, slide, nullvector, 0.5·Δt) ⇒ slide ← slide·(1 − 0.5·Δt) (lerp semantics a + (b − a)·f: B).

#### P62 — `HULL_MIN_X`

| Field | Value |
|---|---|
| Full value | source literal `-12.5`; stored IEEE-754 double `-12.5` (`0xc029000000000000`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9a81` = data base `0x45` + Pos `0xb9a3c` (`@const_130575`) |
| Instruction address | IMG `0x3b3dde` |
| Function | `playerMove` (statement ends @IMG `0x3b3e09`) |
| Lifted statement (full) | `L204.min_x = -12.5` |
| All literals in this statement | `-12.5`@3b3dde |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3b3dd0  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3b3ddd  pop eax
3b3dde  fld qword ptr [ebx + 0xb9a3c] ; @const_130575{DOUBLE=-12.5 init=-12.5 hex=00000000000029c0}  <==
3b3de4  fmul qword ptr [eax]
3b3de6  fistp dword ptr [ebp + 0xa20]
3b3dec  mov eax, dword ptr [ebp + 0xa20]
3b3df2  mov dword ptr [ebp + 0xa1c], eax
3b3df8  mov ecx, 4
3b3dfd  mov esi, dword ptr [ebp + 0xa18]
```

**Math.** me.min_x = −12.5 (FIXED field of the entity). Collision use of the bbox: engine (B).

#### P63 — `HULL_MAX_X`

| Field | Value |
|---|---|
| Full value | source literal `12.5`; stored IEEE-754 double `12.5` (`0x4029000000000000`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9a79` = data base `0x45` + Pos `0xb9a34` (`@const_130574`) |
| Instruction address | IMG `0x3b3e4c` |
| Function | `playerMove` (statement ends @IMG `0x3b3e77`) |
| Lifted statement (full) | `L204.max_x = 12.5` |
| All literals in this statement | `12.5`@3b3e4c |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3b3e3e  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3b3e4b  pop eax
3b3e4c  fld qword ptr [ebx + 0xb9a34] ; @const_130574{DOUBLE=12.5 init=12.5 hex=0000000000002940}  <==
3b3e52  fmul qword ptr [eax]
3b3e54  fistp dword ptr [ebp + 0xa2c]
3b3e5a  mov eax, dword ptr [ebp + 0xa2c]
3b3e60  mov dword ptr [ebp + 0xa28], eax
3b3e66  mov ecx, 4
3b3e6b  mov esi, dword ptr [ebp + 0xa24]
```

**Math.** me.max_x = 12.5.

#### P64 — `HULL_MAX_Z`

| Field | Value |
|---|---|
| Full value | integer `30` (`0x0000001e`); entered as var `30·1024 = 30720` where shifted (`shl 10`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9a99` = data base `0x45` + Pos `0xb9a54` (`@const_130591`) |
| Instruction address | IMG `0x3b3f5e` |
| Function | `playerMove` (statement ends @IMG `0x3b3f8a`) |
| Lifted statement (full) | `L204.max_z = 30` |
| All literals in this statement | `30`@3b3f5e |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3b3f56  mov eax, dword ptr [esi]
3b3f58  mov dword ptr [ebp + 0xa48], eax
3b3f5e  mov eax, dword ptr [ebx + 0xb9a54] ; @const_130591{LONG=30 init=30 hex=1e000000}  <==
3b3f64  shl eax, 0xa
3b3f67  mov dword ptr [ebp + 0xa4c], eax
3b3f6d  mov eax, dword ptr [ebp + 0xa4c]
3b3f73  mov dword ptr [ebp + 0xa48], eax
3b3f79  mov ecx, 4
3b3f7e  mov esi, dword ptr [ebp + 0xa44]
```

**Math.** me.max_z = 30.

#### P65 — `HULL_MIN_Z_BASE`

| Field | Value |
|---|---|
| Full value | integer `-55` (`0xffffffc9`); entered as var `-55·1024 = -56320` where shifted (`shl 10`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba5a1` = data base `0x45` + Pos `0xba55c` (`@const_139311`) |
| Instruction address | IMG `0x3eb14b` |
| Function | `playerMove` (statement ends @IMG `0x3eb19e`) |
| Lifted statement (full) | `L204.min_z = -55 + r948` |
| All literals in this statement | `-12.25`@3eb11b, `0xffffffc9`@3eb14b |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3eb13f  add esp, 8
3eb145  mov dword ptr [ebp + 0x94a8], eax
3eb14b  mov eax, dword ptr [ebx + 0xba55c] ; @const_139311{LONG=-55 init=0xffffffc9 hex=c9ffffff}  <==
3eb151  shl eax, 0xa
3eb154  mov dword ptr [ebp + 0x94ac], eax
3eb15a  mov eax, dword ptr [ebp + 0x94ac]
3eb160  mov ecx, dword ptr [ebp + 0x94a8]
3eb166  add eax, ecx
3eb168  mov dword ptr [ebp + 0x94b0], eax
```

**Math.** When noGroundContactTimer == 0: me.min_z = −55 + max(heroSpringLoadedBoneExtend, −12.25).

#### P66 — `HULL_MIN_Z_EXT_LIMIT`

| Field | Value |
|---|---|
| Full value | source literal `-12.25`; stored IEEE-754 double `-12.25` (`0xc028800000000000`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba5ad` = data base `0x45` + Pos `0xba568` (`@const_139315`) |
| Instruction address | IMG `0x3eb11b` |
| Function | `playerMove` (statement ends @IMG `0x3eb13d`) |
| Lifted statement (full) | `r948 = maxv(arg0.heroSpringLoadedBoneExtend, -12.25)` |
| All literals in this statement | `-12.25`@3eb11b |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3eb10d  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3eb11a  pop eax
3eb11b  fld qword ptr [ebx + 0xba568] ; @const_139315{DOUBLE=-12.25 init=-12.25 hex=00000000008028c0}  <==
3eb121  fmul qword ptr [eax]
3eb123  fistp dword ptr [ebp + 0x94a4]
3eb129  mov eax, dword ptr [ebp + 0x94a4]
3eb12f  push eax
3eb130  mov eax, dword ptr [ebp + 0x94a0]
3eb136  push eax
```

**Math.** Lower limit of the extension term in min_z.

#### P68 — `SPRING_VISUAL_EXTEND_AMPL`

| Field | Value |
|---|---|
| Full value | integer `18` (`0x00000012`); entered as var `18·1024 = 18432` where shifted (`shl 10`) |
| Unit | Q |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9891` = data base `0x45` + Pos `0xb984c` (`@const_130283`) |
| Instruction address | IMG `0x3eadf9` |
| Function | `playerMove` (statement ends @IMG `0x3eae53`) |
| Lifted statement (full) | `arg0.heroSpringLoadedBoneExtend = r947 * 18` |
| All literals in this statement | `0.9`@3eadb6, `18`@3eadf9 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3eaded  add esp, 4
3eadf3  mov dword ptr [ebp + 0x9414], eax
3eadf9  mov eax, dword ptr [ebx + 0xb984c] ; @const_130283{LONG=18 init=18 hex=12000000}  <==
3eadff  shl eax, 0xa
3eae02  mov dword ptr [ebp + 0x9418], eax
3eae08  mov eax, dword ptr [ebp + 0x9414]
3eae0e  mov edx, dword ptr [ebp + 0x9418]
3eae14  imul edx
3eae16  shrd eax, edx, 0xa
```

**Math.** Extend = 18·sinv(0.9·BonePerc) (sinv in degrees: B); if Extend < 0: Extend ← Extend·(0.01·BonePercMax)·(200 + Extend)·0.0025. Feeds min_z (P65).

#### T01 — `TIME_FACTOR_PLAY`

| Field | Value |
|---|---|
| Full value | source literal `0.95`; stored IEEE-754 double `0.949999988079071` (`0x3fee666660000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0dad09` = data base `0x45` + Pos `0xdacc4` (`@const_258634`) |
| Instruction address | IMG `0x69c64f` |
| Function | `mainFrameEventPlay` (statement ends @IMG `0x69c67a`) |
| Lifted statement (full) | `*time_factor = 0.95` |
| All literals in this statement | `0.95`@69c64f |
| Scope | TIMESTEP |
| Class | value+operation **A**; physical meaning **B** |

```asm
69c641  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
69c64e  pop eax
69c64f  fld qword ptr [ebx + 0xdacc4] ; @const_258634{DOUBLE=0.949999988079071 init=0.95 hex=000000606666ee3f}  <==
69c655  fmul qword ptr [eax]
69c657  fistp dword ptr [ebp + 0xc]
69c65d  mov eax, dword ptr [ebp + 0xc]
69c663  mov dword ptr [ebp + 8], eax
69c669  mov ecx, 4
69c66e  mov esi, dword ptr [ebx + 0x9988c] ; time_factor<POINTER>
```

**Math.** mainFrameEventPlay writes *time_factor = 0.95 unconditionally at entry (also @69daa5, @6a2136 when mainFrameMode ≠ 5). Skate mode writes 1 (@69dc1f). That time_factor scales time_step is engine semantics (B).

#### C02 — `GROUND_CORRECTION_GAIN`

| Field | Value |
|---|---|
| Full value | source literal `0.5`; stored IEEE-754 double `0.5` (`0x3fe0000000000000`) |
| Unit | — |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b986d` = data base `0x45` + Pos `0xb9828` (`@const_130217`) |
| Instruction address | IMG `0x3d7486` |
| Function | `playerMove` (statement ends @IMG `0x3d74e3`) |
| Lifted statement (full) | `r640 = vec_normalize(normal, (r639 * (r636 - r637)) * 0.5)` |
| All literals in this statement | `0.5`@3d7486 |
| Scope | CORE |
| Class | value+operation **A**; physical meaning **B** |

```asm
3d747a  fstp qword ptr [ebp + 0x635c]
3d7480  fld qword ptr [ebp + 0x635c]
3d7486  fmul qword ptr [ebx + 0xb9828] ; @const_130217{DOUBLE=0.5 init=0.5 hex=000000000000e03f}  <==
3d748c  fstp qword ptr [ebp + 0x6364]
3d7492  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3d749f  pop eax
3d74a0  fld qword ptr [ebp + 0x6364]
3d74a6  fmul qword ptr [eax]
3d74a8  fistp dword ptr [ebp + 0x636c]
```

**Math.** After pogo_trace(me, me, L3fc, 613, 4) hits: push length = (n·dir)·(|me − L3fc| − |me − target|)·0.5 along the normal, applied by c_move. Exact c_move response: engine.

#### W04 — `WATER_DRAG`

| Field | Value |
|---|---|
| Full value | source literal `0.075`; stored IEEE-754 double `0.07500000298023224` (`0x3fb3333340000000`) |
| Unit | 1/T |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0b9b75` = data base `0x45` + Pos `0xb9b30` (`@const_131116`) |
| Instruction address | IMG `0x3d0ea1` |
| Function | `playerMove` (statement ends @IMG `0x3d0ef1`) |
| Lifted statement (full) | `r563 = vec_lerp(ADDR(arg0.speed), ADDR(arg0.speed), nullvector, 0.075 * (*time_step))` |
| All literals in this statement | `0.075`@3d0ea1 |
| Scope | LEVEL |
| Class | value+operation **A**; physical meaning **B** |

```asm
3d0e99  fdiv qword ptr [eax]
3d0e9b  fstp qword ptr [ebp + 0x52dc]
3d0ea1  fld qword ptr [ebx + 0xb9b30] ; @const_131116{DOUBLE=0.07500000298023224 init=0.075 hex=000000403333b33f}  <==
3d0ea7  fmul qword ptr [ebp + 0x52dc]
3d0ead  fstp qword ptr [ebp + 0x52e4]
3d0eb3  call $+13 ; dq 1024.0 (inline literal, address popped into eax next)
3d0ec0  pop eax
3d0ec1  fld qword ptr [ebp + 0x52e4]
3d0ec7  fmul qword ptr [eax]
```

**Math.** Not swimming: vec_lerp(v, v, 0, 0.075·Δt) ⇒ v ← v·(1 − 0.075·Δt).


---

## 8. UNKNOWN D — constants whose value is A but whose meaning is unknown

### A value, D meaning (engine flag arguments)

#### P12 — `CMOVE_MODE`

| Field | Value |
|---|---|
| Full value | integer `548` (`0x00000224`); entered as var `548·1024 = 561152` where shifted (`shl 10`) |
| Unit | flags |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba121` = data base `0x45` + Pos `0xba0dc` (`@const_134627`) |
| Instruction address | IMG `0x3d2f52` |
| Function | `playerMove` (statement ends @IMG `0x3d2f58`) |
| Lifted statement (full) | `L264 = 548` |
| All literals in this statement | `0x224`@3d2f52 |
| Scope | ENGINE_ARG |
| Class | value+operation **A**; physical meaning **D** |

```asm
3d2f52  mov eax, dword ptr [ebx + 0xba0dc] ; @const_134627{LONG=548 init=0x224 hex=24020000}  <==
3d2f58  mov dword ptr [ebp + 0x264], eax
```

**Math.** L264 = 548 (0x224) is the mode argument of the player c_move @3d4e36. Bit meanings are defined in acknex.h (not in the binary).

#### P13 — `CMOVE_MODE_EXTRA`

| Field | Value |
|---|---|
| Full value | integer `262144` (`0x00040000`); entered as var `262144·1024 = 268435456` where shifted (`shl 10`) |
| Unit | flags |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba161` = data base `0x45` + Pos `0xba11c` (`@const_134923`) |
| Instruction address | IMG `0x3d30b2` |
| Function | `playerMove` (statement ends @IMG `0x3d30ba`) |
| Lifted statement (full) | `L264 = L264 \| 262144` |
| All literals in this statement | `0x40000`@3d30b2 |
| Scope | ENGINE_ARG |
| Class | value+operation **A**; physical meaning **D** |

```asm
3d30aa  mov dword ptr [esi], eax
3d30ac  mov eax, dword ptr [ebp + 0x264]
3d30b2  mov ecx, dword ptr [ebx + 0xba11c] ; @const_134923{LONG=262144 init=0x40000 hex=00000400}  <==
3d30b8  or eax, ecx
3d30ba  mov dword ptr [ebp + 0x264], eax
```

**Math.** L264 |= 0x40000 when noGroundContactTimer ≠ 0, slide mode (flags & 1) or isInWater. Meaning engine-defined.

#### P21 — `GROUND_PROBE_TRACE_MODE`

| Field | Value |
|---|---|
| Full value | integer `613` (`0x00000265`); entered as var `613·1024 = 627712` where shifted (`shl 10`) |
| Unit | flags |
| File | `Pogostuck.exe` → LZSS overlay (`file:0x61000`) → decoded Lite-C image (IMG) |
| Memory address (literal) | IMG `0x0ba2cd` = data base `0x45` + Pos `0xba288` (`@const_136174`) |
| Instruction address | IMG `0x3d850c` |
| Function | `playerMove` (statement ends @IMG `0x3d8544`) |
| Lifted statement (full) | `r669 = pogo_trace(L204, ADDR(L204.x), ADDR(L460), 613, 4)` |
| All literals in this statement | `4`@3d84f6, `0x265`@3d850c |
| Scope | ENGINE_ARG |
| Class | value+operation **A**; physical meaning **D** |

```asm
3d8505  mov eax, dword ptr [ebp + 0x6608]
3d850b  push eax
3d850c  mov eax, dword ptr [ebx + 0xba288] ; @const_136174{LONG=613 init=0x265 hex=65020000}  <==
3d8512  shl eax, 0xa
3d8515  mov dword ptr [ebp + 0x660c], eax
3d851b  mov eax, dword ptr [ebp + 0x660c]
3d8521  push eax
3d8522  mov eax, ebp
3d8524  add eax, 0x460
```

**Math.** pogo_trace(me, me.x, probeEnd, 613, 4) → c_trace(start, end, 613 | 8192 | 1) @1e500a. Bit meanings engine-defined.


## 9. UNKNOWN D — physics not present in the supplied files

| Item | Why unknown | What resolves it |
|---|---|---|
| `c_move` collision response: glide, step, depenetration; how `hit`, `normal`, `target` and `bounce` are produced | inside `acknex.dll`, not supplied | `acknex.dll`, or a frame capture |
| Bits of the modes 548, 0x40000, 613, 8192, 1 | `acknex.h` defines, not in the binary | `acknex.h` |
| How `time_step` is formed (smoothing, clamping) | engine | `acknex.dll`, or a capture |
| Pogo bone geometry (`Bone1` tip, `Bone2`), initial `min_z` before the first spring update | model files, not supplied | the `.mdl` file |
| Meaning of the entity bits (`flags & 64` slippery, `skill[98]` masks) as set by levels | level files | `.wmb` files |

## 10. ESTIMATED C

None. No value in the review, the CSV or the LOCKED SPEC comes from gameplay estimation.
