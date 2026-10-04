# Pogostuck Physics — LOCKED SPEC

> **What this is.** The physics recovered from the compiled original game that can be carried into the Android project with confidence. It contains **only**:
> - **[A]**: value, operation and meaning are proven by compiled game code;
> - **[A·B]**: value and operation are proven; the meaning additionally relies on one named, standard engine routine (stated next to the item). It is required to assemble the model.
>
> **Excluded:** gameplay estimates (class C), anything unknown (class D), level-specific or mode-specific rules, achievement and UI thresholds, and the engine's internal collision solver.
>
> **Sources:**
> - `Pogostuck_Physics_Final_Review.md`: per-constant evidence, assembly, addresses;
> - `Pogostuck_Physics_Constants.csv`: 99 rows, IDs referenced below as `[P01]` etc.;
> - `Pogostuck_Original_Physics_Extraction.md`.
>
> **Status:** locked for implementation review. **Not implemented.** No Android or game code was changed.

---

## 1. Units, frame, state

| Symbol | Unit | Definition |
|---|---|---|
| Q | quant | Original length unit. Display: 52 Q = 1 "m" on the HUD [U01] (display convention only). |
| T | tick | Original time unit of `time_step` |
| L | load | Dimensionless spring-load counter |
| deg | degree | All angles |
| Δt | T | Time step of the current physics update |

- **Plane:** the game is 2-D in x (horizontal) and z (vertical, **up = +z**). The y coordinate is forced to 0 every frame.
- **Gravity direction:** on the main map gravity points to −z (`gravityAngle γ = 0`). The general form with γ is kept below for completeness.

**Player state** (all fixed-point 22.10 in the original; floating point is acceptable, see §9):

| Name | Symbol | Unit | Original field |
|---|---|---|---|
| velocity | v = (v_x, v_z) | Q/T | `speed` |
| slide velocity | s = (s_x, s_z) | Q/T | `slideSpeed` |
| body angle | θ | deg (0 = upright; accumulates without wrap in the air) | `angle` |
| angular velocity | ω | deg/T | `heroTurnSpeed` |
| spring load | L | L | `heroSpringLoaded` |
| spring window | L_min, L_max | L | `heroSpringLoadedMin/Max` |
| grounded flag | g ∈ {0,1} | — | `heroGroundContact` |
| no-ground timer | N | T | `noGroundContactTimer` |
| jump timer | J | T | `jumpTimer` |
| boost flag | p ∈ {0,1} | — | `powerJumpNext` |
| last jump angle | θ_j | deg | `lastJumpAngle` |
| slide mode | m ∈ {0,1} | — | `flags & 1` |
| surface normal | n = (n_x, n_z), \|n\| = 1 | — | `normal` (y zeroed) |
| normal angle | θ_n = atan2(n_z, n_x) | deg | `surfaceNormal` |

`wrap180(x)` maps an angle to (−180, 180] **[A·B: Gamestudio `ang()`]**.

## 2. Time step

| Item | Value | Class |
|---|---|---|
| Physics updates | **one update per rendered frame**; no fixed-step accumulator exists in the original | [A] |
| Δt | the engine `time_step` of that frame, in ticks; every rate below is per tick and multiplied by Δt | [A] (use) |
| `time_factor` during play | written every play frame as the var **973/1024 = 0.9501953125** (source literal 0.95) | [A] value · [A·B] meaning: Gamestudio scales `time_step` by `time_factor` |
| Frame cap | **120 fps** default; user range **30–240** | [A] |
| Android mapping | **Δt = 16 · Δt_real(s) · 0.9501953125** T. At 120 Hz this is **Δt = 0.126693 T**. | [A·B] (assumes `time_step = time_frame·time_factor`, ticks = 1/16 s, no smoothing) |

**Recommendation for Android** (an implementation choice, not an original fact): run a fixed 120 Hz loop with Δt = 0.126693 T. This reproduces the original at its default frame cap and keeps runs deterministic.

## 3. Update order inside one frame (as in the original)

1. Timers (E12)
2. Slide update (E15)
3. Turn (E4)
4. If g: tip pivot + ground pressure (E5); else: air step (E1)
5. Speed cap (E2)
6. Collision move by displacement (E3)
7. Ground probe → n, g (E6); ledge exit (E16)
8. If not g: L ← 0
9. Landing frame (g: 0→1): landing force and spring window (E7); slide entry (E15)
10. If g ∧ N = 0: charge or launch (E8–E11)
11. Else, if a body hit occurred this frame ∧ N = 0: wall bounce (E14)
12. Airborne: platform release (E17); speed cap again (E2)
13. Boost check (E13)
14. Spring extension → hull bottom (E18)

## 4. Locked equations

### E1 — Air step: gravity and drag [A]

- Main map (γ = 0):
  - **v_x ← v_x − 0.05·v_x·Δt**
  - **v_z ← v_z − 8.5·Δt**
  - No drag acts on v_z.
- General form [A·B, `vec_rotate` tilt convention]: `ê = R_γ·(1,0,0)`, `a = R_γ·(−0.05·(v·ê)·Δt, 0, −8.5·Δt)`, `v ← v + a`.
- Constants: gravity **8.5 Q/T²** [P01]; drag **0.05 /T** [P02] (stored −0.05000000074505806).
- Integrator: the velocity is updated first and the position is then moved by the new velocity (semi-implicit Euler) [A, corroborated by the original's own predictor P69].

### E2 — Speed cap [A]

**if |v| > 300: v ← 300·v/|v|** [P09]. Applied after E1/E5 and again after E17.

### E3 — Displacement [A]

**d = ((v_x + s_x)·Δt , (v_z + 4·s_z)·Δt)** [P11]. The body is then moved by d with collision.

The original passes d to the engine's `c_move` with engine friction set to 0 (`move_friction = 0`). The collision response itself is **not** locked (§7).

### E4 — Turn [A]

- **Lb = 1/(1 + 2g)**: 1 in the air, 1/3 on the ground [P15].
- **ω_t = 32·(u_left − u_right)**, with u ∈ [0, 1] (1 for a digital press) [P16].
- **ω ← ω + (ω_t − ω)·0.525·Δt / (1 + √J)** [P17].
- **θ ← θ + Lb·ω·Δt**. In the original this is applied through the engine's collision-checked `c_rotate`, and θ is then read back from the entity [A·B: `c_rotate` adds the angle unless blocked].
- Sign convention: **positive ω increases θ**. Turning "left" (original key A, button 2) gives positive ω_t.

### E5 — Grounded: tip pivot and ground pressure [A, direction A·B]

- **Tip pivot [A]** [P19]: after the rotation, let Δtip = tip_prev − tip. If |Δtip_x| < 256 ∧ |Δtip_z| < 256, move the body by (Δtip_x, 0, Δtip_z) with collision. The rotation therefore pivots on the pogo tip. `tip_prev` is refreshed every grounded frame.
- **Ground pressure** [P14]: **v ← −24·n**. The magnitude 24 Q/T is [A]. The direction "into the surface" is [A·B]: `R_{θ_n}·(−24, 0, 0)` with `R_θ(1,0,0) = (cos θ, 0, sin θ)`.

### E6 — Ground probe and grounded flag [A]

- Probe [P20]: from the body origin to **end = tip + (6 + |s_z|)·û_stick**, where û_stick is the unit vector from the stick's upper bone to the tip.
- The probe is a box of half-size **4 Q** [P22, A·B: box trace].
- Hit ⇒ n = hit normal with y = 0, normalized; θ_n = atan2(n_z, n_x).
- **g = hit ∧ (the collision move touched something this frame) ∧ (contact surface is not filtered out)**.
- g is forced to 0 if there is no hit, while stunned, while **N > 0** [P44], or in water.

### E7 — Landing force and spring window [A]

On the frame where g changes 0 → 1, with v = the velocity of that frame:
- **I = 1.65·|v|^0.925** [P23, P24]
- **L_min = max(40, I^0.9)** [P28, P29]
- **L_max = clamp(I + 20·p, 95 + 25·p, 300)** [P25a, P25, P26, P27]

Notes:
- The original adds the same extra term e to both clamp arguments for special objects. In normal play **e = 0**.
- If p > 1 it is reduced to 1 at landing. Only p ∈ {0,1} occurs in normal play.

### E8 — Charge [A]

On each grounded frame with N = 0:
- **if (L < L_min ∨ hold) ∧ L < L_max: L ← min(L + 16·Δt, L_max)** [P32];
- **else if L > 2: launch (E9–E11)** [P33].

`hold` = the charge input [P31]. In the original this is button 4, default keyboard code 57 (Space) [A·B for the key name]; on Android it is the touch "charge" input.

When **not grounded: L ← 0**.

### E9 — Launch velocity and angle [A, sin/cos convention A·B]

- **δ = clamp(wrap180(θ_n − 90 − θ), −45, 45)** [P35, P36]
- **a = θ + 0.1875·δ** [P37]
- **V = 0.74235·L·(−sin a, cos a)** [P34]. The value 0.74235 is stored as 0.7423499822616577. The direction formula is [A·B]: `vec_rotate` of (0, 0, 0.74235·L) by tilt a.
- **v ← (V_x + 0.25·s_x , V_z + 0·s_z)** [P38, P39]. This is an assignment: the incoming velocity is not added.

On flat ground (θ_n = 90): a = θ − 0.1875·clamp(θ, −45, 45). For |θ| ≤ 45 this is 0.8125·θ.

### E10 — Launch spin [A]

With σ = asin(n_x) in degrees [A·B: degrees]:

**ω ← ω + 0.1245·clamp(wrap180(θ − γ), −45, 45) − 0.25·1.5·sgn(σ)·|σ|^0.75** [P40, P40b, P41, P42, P43]

In normal play the original's special-collision factor (1 − 0.5·special) is 1.

### E11 — After launch [A]

**L ← 0, g ← 0, N ← 2 T [P44], J ← 0.45·√L_max [P45], θ ← wrap180(θ), θ_j ← θ, p ← 0.**

### E12 — Timers, every frame [A]

- **N ← max(N − Δt, 0)**
- **J ← 0 if g, else max(J − Δt, 0)**
- Stun timer ← max(stun − Δt, 0)
- If N > 0: g ← 0
- If not g: slide mode m ← 0

### E13 — Boost (power jump) [A]

- Airborne: **if int(|θ − θ_j|) > 285 ∧ p < 1 then p ← 1** [P46].
- Effect at the next landing: E7 with p = 1 gives `L_max = clamp(I + 20, 120, 300)`.
- p ← 0 after the launch (E11) and at a wall bounce (E14).

### E14 — Wall bounce [A magnitudes, direction A·B]

Trigger: the collision move hit something, the player is not grounded, N = 0, and the reported contact point is within 256 Q of the body. The threshold is 128 Q if the reported contact point has x = z = 0.

1. Side effects: N ← 2 [P54], p ← 0, L ← 0, θ ← wrap180(θ), θ_j ← θ.
2. Direction:
   - r̂ = reflection direction of the movement off the surface [A·B]. In the original this is the engine's `bounce` vector from `c_move`. When it is unavailable the game computes **r = v − 2(v·n)n** itself [P47, A].
   - r̂_y = 0; **d = 0.9·r̂/|r̂| + n** [P48].
3. Speed: **S = max(28, 0.4·|v|)·min(1 + n_z, 1)** [P49, P50, P73]. **b = S·d/|d|**; **if n_z > 0: b_z = n_z·S**.
4. **v ← (0.875·b_x + s_x , b_z + s_z)** [P51]. This is an assignment.
5. **ω ← 0.5·wrap180(γ − θ) − 0.2·1.5·sgn(σ)·|σ|^0.75**, σ = asin(n_x) in degrees [P52, P53]. This is an assignment: it rotates the body back toward upright.

There is **no restitution coefficient**: the rebound speed is S, not e·|v|.

### E15 — Slide / ice [A, decay A·B]

**Entry**, at landing on a surface flagged slippery [P60]:
- m ← 1;
- **s_x ← v_x**;
- **s_z ← 0**.

(The original's 0.25·v_z variant applies only in other levels and modes.) Landing on a non-slippery surface sets m ← 0.

**In slide mode (m = 1)**, each frame, per axis i ∈ {x, z}:
- **t = 48·normalize(n + R_γ·(0, 0, −1))**, i.e. 48 Q/T down the slope [P55, A·B for the direction];
- **s_i ← s_i + clamp(0.25·(t_i − s_i), −1.35, 1.35)·Δt** [P57, P58].

**Out of slide mode (m = 0)**:
- **if |s| < 0.25 ∨ not g: s ← 0** [P72];
- **else s ← s·(1 − 0.5·Δt)** [P59, A·B: `vec_lerp`].

Slide reaches the motion only through E3 (gains 1 on x, 4 on z) and E9 (0.25·s_x).

### E16 — Ledge exit [A]

If g was 1 at the start of the probe and the probe now misses: **v ← (s_x, 5)** [P71].

### E17 — Release from a moving platform [A]

Airborne with carried platform velocity c ≠ 0:
- **v_x ← v_x + c_x**
- **v_z ← v_z + c_z·(1 − 0.75·[c_z < 0])** [P61]
- Then E2.

### E18 — Collision hull [values A, use A·B]

- **x ∈ [−12.5, 12.5] Q** [P62, P63]
- **y ∈ [−32, 32] Q** (`pogo_width_y` = 32, never written)
- **z_max = 30 Q** [P64]
- **z_min = −55 + max(X, −12.25) Q** [P65, P66], updated only when N = 0.

X is the spring extension [P67, P68]:
- **X = 18·sin(0.9·P_b)** (degrees);
- if X < 0: **X ← X·(0.01·P_max)·(200 + X)·0.0025**;
- while charging: **P_b = P_max = L**;
- airborne: **P_b ← max(P_b − 120·Δt, −200)**.

While rotating (E4), z_min is temporarily raised by **16 Q** [P18].

## 5. Locked constants (all [A] values)

| ID | Name | Value | Unit |
|---|---|---|---|
| P01 | gravity | 8.5 | Q/T² |
| P02 | air drag (horizontal) | 0.05 (stored 0.05000000074505806) | 1/T |
| P09 | max speed | 300 | Q/T |
| P11 | slide z displacement gain | 4 | — |
| P14 | ground pressure | 24 | Q/T |
| P15 | ground turn factor | 1/(1+2g) → 1/3 | — |
| P16 | turn target | 32 | deg/T per unit input |
| P17 | turn response | 0.525 (stored 0.5249999761581421) | 1/T |
| P18 | hull lift during rotation | 16 | Q |
| P19 | tip pivot limit | 256 | Q |
| P20 | probe extension | 6 (+\|s_z\|) | Q |
| P22 | probe box half-size | 4 | Q |
| P23 | impact exponent | 0.925 (stored 0.925000011920929) | — |
| P24 | impact gain | 1.65 (stored 1.649999976158142) | L/(Q/T)^0.925 |
| P25a | boost bonus on impact | 20 | L |
| P25 | L_max floor | 95 | L |
| P26 | boost floor bonus | 25 | L |
| P27 | L_max cap | 300 | L |
| P28 | L_min exponent | 0.9 (stored 0.8999999761581421) | — |
| P29 | L_min floor | 40 | L |
| P32 | charge rate | 16 | L/T |
| P33 | minimum launch load | 2 | L |
| P34 | launch speed per load | 0.74235 (stored 0.7423499822616577) | (Q/T)/L |
| P35 | normal-angle offset | 90 | deg |
| P36 | normal-blend clamp | 45 | deg |
| P37 | normal-blend factor | 0.1875 | — |
| P38 | launch slide carry x | 0.25 | — |
| P39 | launch slide carry z | 0 | — |
| P40 | launch spin per tilt | 0.1245 (stored 0.12449999898672104) | (deg/T)/deg |
| P40b | launch spin tilt clamp | 45 | deg |
| P41 | slope-spin exponent | 0.75 | — |
| P42 | slope-spin gain | 1.5 | — |
| P43 | slope-spin factor at launch | 0.25 | — |
| P44 | no-ground time after launch/bounce | 2 | T |
| P45 | jump-timer gain | 0.45 (stored 0.44999998807907104) | T/√L |
| P46 | boost rotation threshold | 285 | deg |
| P47 | reflection factor (fallback) | −2 | — |
| P48 | bounce direction weight | 0.9 (stored 0.8999999761581421) | — |
| P49 | bounce speed factor | 0.4 (stored 0.4000000059604645) | — |
| P50 | bounce minimum speed | 28 | Q/T |
| P73 | bounce slope attenuation | min(1 + n_z, 1) | — |
| P51 | bounce x scale | 0.875 | — |
| P52 | bounce righting spin | 0.5 | (deg/T)/deg |
| P53 | slope-spin factor at bounce | 0.2 (stored 0.20000000298023224) | — |
| P55 | slide target speed | 48 | Q/T |
| P57 | slide response | 0.25 | 1/T |
| P58 | slide acceleration limit | 1.35 (stored 1.350000023841858) | Q/T² |
| P59 | slide decay | 0.5 | 1/T |
| P72 | slide stop threshold | 0.25 | Q/T |
| P61 | platform downward carry reduction | 0.75 | — |
| P71 | ledge exit pop | 5 | Q/T |
| P62/P63 | hull x | ±12.5 | Q |
| — | hull y | ±32 | Q |
| P64 | hull z_max | 30 | Q |
| P65 | hull z_min base | −55 | Q |
| P66 | spring-extension limit | −12.25 | Q |
| P67 | spring relax rate | 120 | L/T |
| P68 | spring extension amplitude | 18 | Q |
| T01 | time_factor | 973/1024 = 0.9501953125 | — |
| T02–T04 | frame cap | 120 (range 30–240) | fps |

## 6. Inputs (functional, for the Android mapping)

| Original | Locked behaviour |
|---|---|
| Turn left/right (keys A/D, buttons 2/3) | u_left, u_right ∈ [0, 1]; ω_t = 32·(u_left − u_right) |
| Charge (Space, button 4) | `hold` in E8 |

Analog-stick shaping in the original (dead zone, exponent, optional speed modifier 0.667) is an input preference, **not** physics. It is not locked.

## 7. Not locked: Android must supply these itself

| Item | Why | Requirement for Android |
|---|---|---|
| Collision response of the move/rotate (glide along surfaces, depenetration, step-up) | inside the engine (`acknex.dll`), not supplied (D) | Move by d (E3) against the level, sliding along contacts, never tunnelling. Report "touched something" for E6/E14 and the contact normal. |
| Engine flag arguments (548, 0x40000, 613, 8192, 1) | engine-defined (D) | Not needed |
| Pogo geometry: tip position and stick axis | in the model file, not supplied (D) | Must come from our own character model |
| Which surfaces are slippery | level data (D) | Our levels mark ice surfaces |
| Rendering scale (metres per Q) | not physics | Free choice. The HUD convention of the original is 52 Q = 1 m. |

## 8. Explicitly excluded (proven [A] but not part of the transferable core)

These are documented in the CSV and the review but **not locked**:
- Level gravity variants: −6.125 / −4.175 / −2 with drag 0.025 (P03–P07); the level-9 zone factor (P08).
- Swimming (W01–W04); super-jump and limit regions (R02); entity bounce pads (E01–E02).
- Special-mode terms: P10, P30, P43b, P56; shot jump S01–S02; start-area reset R01.
- Ground-correction pushes C01–C04: they compensate for the original engine's collision. Reproduce them only if our collision shows the same sticking.
- Achievements and UI: U02–U04.
- The trajectory predictor (P69–P70): it is evidence only.

## 9. Numeric precision

- The original stores every state value in 22.10 fixed point. Products round half-up; quotients truncate; double→var conversions round to nearest.
- A float/double implementation differs by at most ~1/1024 per stored quantity per frame.
- This difference is acceptable unless frame-exact replay compatibility with the original is required. It is not required.

## 10. Validation targets (DERIVED from the locked equations; to be used as tests, not as inputs)

Computed with Δt = 0.126693 T (120 fps, time_factor 973/1024), using E1 and E9 with the player upright and from flat ground.

| Load L | Launch speed (Q/T) | Apex (Q) | Apex (HUD m = Q/52) | Air time (T) | Air time (real s) |
|---|---|---|---|---|---|
| 40 | 29.69 | 50.0 | 0.96 | 6.97 | 0.458 |
| 95 | 70.52 | 288.1 | 5.54 | 16.47 | 1.083 |
| 120 | 89.08 | 461.2 | 8.87 | 20.90 | 1.375 |
| 200 | 148.47 | 1287.3 | 24.76 | 34.84 | 2.292 |
| 300 | 222.70 | 2903.4 | 55.83 | 52.32 | 3.442 |

**Passive bounce** (no charge input), starting from a 95 jump, landing with |v| = 70.52:
- I = 84.57 → L_min = 54.26 → launch 40.28;
- next: I = 50.37 → L_min = 40 → launch **29.69**, which is stable from then on.

**Other derived figures:**
- Charge from 0 to 95 takes 5.94 T; to 300 takes 18.75 T.
- Maximum turn rate is 32 deg/T in the air and 10.67 deg/T on the ground.
