# PHYSICS_UNCERTAINTIES.md — Register of every uncertain physics value

> Reference: `Pogostuck_Physics_Master_Merged_DataWRS.docx` + `Pogostuck_Physics_Master.xlsx` + the analysis PDF.
> **Main rule:** no value here is presented as an original Pogostuck value. The new document **contains no numeric physics value** (its §25 and "Final status" say so explicitly). Every number used is either (a) confirmed from the XLSX (A), (b) our calibrated value (`TUNE_ME` / C), (c) derived by an equation from other unknown values (DERIVED-from-C), or (d) a gameplay design choice that isn't in the original (`DESIGN`).
> Classification: **A** confirmed · **B** reconstructed equation · **C** unknown/needs calibration · **DESIGN** our gameplay feature.
> Last updated: analysis stage (before IMPLEMENT). Values marked "new" don't exist in the code yet.

---

## 1) What the document itself says is not established (§25) — and where each stands with us

| Doc symbol | Meaning | Doc status | Value in our code | Our grade | How it will be calibrated | Effect on movement |
|---|---|---|---|---|---|---|
| `g` | gravity | not established | 26 m/s² (`gravity`) | C | air time `T = 2v₀/g` + jump rhythm ≈ 150 ticks (XLSX V-026, A) | apex height and air time; higher = more "snappy" jumps |
| `μ` | friction coefficient | not established | none in linear form (today: `deceleration` 16 m/s² + `slideFriction` 0.3 + `energyLoss` 0.15) | C | keep today's slide distances: normal ground stops in ≈ 0.2 s from 3 m/s; ice keeps ≥ 90% after 1 s | landing slide and sliding on slopes/ice |
| `e` | restitution | not established | 0.18 floor / 0.45 wall (`floorRestitution`/`wallRestitution`) | C | unchanged unless needed; tested with `v_n' = −e·v_n` | landing rebound and wall bounce |
| `k` | spring stiffness | not established | **none (new)** | C | from a target extension time ≤ 5–6 ticks: `ω_n = √(k/m)`; initial estimate k/m ≈ 1420 s⁻² (ESTIMATED) | launch latency after release and maximum launch force |
| `c` | spring damping | not established | **none (new)** | C | equivalent landing restitution ≈ 0.18 ⇒ `ζ ≈ 0.48`, `c = 2ζ√(k·m)` (DERIVED-from-C); see D1 | landing absorption vs launch strength |
| `m` | effective mass | not established | **none (new)** | C — **not identifiable** | trajectories only depend on `k/m`, `c/m`, `τ/I` ⇒ fixed `m = 1` (normalisation, not a measurement) | none on its own; only the ratios matter |
| `v_jump` | launch speed | not established | 8.5 … 19 m/s (`launchSpeedMin/Max`) | C | **will become an output of the spring** (not an input); calibrate k/m and x_max to reproduce the same envelope so LEVEL_01 stays solvable | jump distance/height |
| `jump_high` | meaning and value unknown | unknown | — | C | **assumed** (C) to be the top jump height: `H = v_max²/2g ≈ 6.9 m` today | — |
| `jump_degrees` | launch/rotation angle | unknown | **assumed** (C) to match `tiltMaxAngle` = 65° | C | unchanged | maximum launch angle from the surface normal |
| `max_speed` | speed cap, "if used" | unknown whether used | 22 horizontal / 32 fall | C (+ unproven it exists in the original) | keep (decision D4) as a gameplay constraint | prevents extreme speeds; silently clamps horizontal speed |
| `air_control` | air control | not established | 1 (×), rate 380°/s, acceleration 1600°/s² | C | reproduce the current angular response with the new τ/I form | rotation speed in the air (and boost readiness) |
| `ground_control` | ground control | not established | 1 (×), rate 150°/s | C | as above | aiming speed before jumping |
| `Δt` | timestep | **not established in the doc** | 1/120 s | **A from XLSX V-001** (read from the replay debug panel) | not calibrated | — (conflict 1: the XLSX evidence wins) |
| `collision_offset` | contact correction distance | not established | 0.05 m hardcoded (`SUPPORT_TOL`) | C | moved into Config; tested so there is no visible gap or penetration | ground hold at edges/slopes |

---

## 2) New parameters the rebuild needs (not mentioned numerically in the doc)

| Parameter (planned) | Proposed starting value | Grade | Origin of the value | Effect |
|---|---|---|---|---|
| `pogoLength` (L0, unloaded length) | 1.2 m (= current `comHeight`) | C | the character's proportions (U-16) | the axis height and the body's lever |
| `maxCompression` (x_max) / compressed length L0−x_max | ≈ 0.5 m (initial estimate `v_max/ω_n`) | C, ESTIMATED | DERIVED-from-C: `v ≈ x·√(k/m)` before gravity/damping losses | how far the rider "sinks" while charging; the launch envelope |
| `landingDamping` (rider absorption) | ζ ≈ 0.48 | **DESIGN + C** — not in the doc | DERIVED from the current restitution 0.18 | non-bouncy landing (decision D1) |
| `inertia` I = m·r_g² | r_g calibrated | C | calibrated so that `τ_max/I` reproduces 1600°/s² | angular acceleration |
| `controlTorque` τ_max | — | C | as above | how strongly tilt controls the angle |
| `gravityTorque` (on/off) | on (decision D3) | B (equation 21) + C (whether the original does it) | inverted pendulum about the tip | the need to "hold" the tilt on the ground |
| `rayLength` | L0 + 0.3 m | C | design: sees the ground just before contact | Debug + approach-to-ground detection |
| `frictionModel` | `linear` | B (doc equation 9 primary) | — | slide shape: exponential (linear) vs constant (Coulomb) |
| `maxStepDistance` (currently `MAX_STEP`) | 0.12 m | DESIGN | anti-tunnelling | numerical stability only |
| `maxSubsteps` / `contactPasses` | 4 / 3 | DESIGN | performance + stability | — |
| `standAngleGain` (currently the ×12 factor) | will be replaced by the PD torque gains | C | — | — |

---

## 3) Existing `TUNE_ME` parameters (unchanged by the document — still unknown)

These values are our working starting points from earlier stages (U-xx refers to the "unknown" sheet in the XLSX). **None of them is an original value.**

| Parameter | Value | Unit | XLSX ref | Note on the rebuild |
|---|---|---|---|---|
| gravity | 26 | m/s² | U-01 | ↔ doc `g` |
| maxFallSpeed | 32 | m/s | U-02 | ↔ `max_speed` |
| maxHorizontalSpeed | 22 | m/s | U-15 | ↔ `max_speed` |
| launchSpeedMin / Max | 8.5 / 19 | m/s | U-03/U-04 | will become **calibration targets** rather than inputs |
| chargeTicksMax | 84 | tick | U-05 | becomes the time to reach full preload |
| chargeCurve | 1 | exp | U-15 | preload curve |
| tiltRateGround / Air | 150 / 380 | °/s | U-06/U-07 | ↔ ground/air control |
| tiltMaxAngle | 65 | ° | U-08 | ↔ `jump_degrees` (assumption) |
| turnSpeed | 1600 | °/s² | U-09 | becomes `τ_max/I` |
| angularDamping | 1.6 | 1/s | U-09 | air angular damping |
| floorRestitution / wallRestitution | 0.18 / 0.45 | — | U-10/U-11 | ↔ `e` |
| energyLoss | 0.15 | — | U-12 | will be replaced by friction (9) on impact or kept with a flag |
| slideFriction | 0.3 | μ | U-13 | ↔ `μ` (Coulomb option) |
| deceleration | 16 | m/s² | U-13 | replaced by the linear μ |
| steepSlopeAngle | 52 | ° | U-14 | static-friction threshold on slopes |
| stableLandingAngle | 72 | ° | U-15 | maximum landing angle between the stick and the normal |
| plantSpeed / restSpeed | 3.5 / 1.0 | m/s | U-10 | rest thresholds (may become unnecessary with the spring) |
| acceleration | 22 | m/s² | U-15 | boost surface push |
| landingResponse | 0.55 | — | U-15 | converts tangential momentum into rotation on landing |
| launchMomentumRetain | 0.75 | — | U-15 | slide retained at launch |
| hardImpactSpeed | 17 | m/s | U-12 | threshold for the hard-impact event (sound/haptics/camera) |
| impactSpin | 0.18 | rad/s per m/s | U-09 | **will be replaced** by the angular impulse `(r×J)/I` |
| boostThreshold / boostRotation / boostPower | 140°/s / 330° / 9 m/s | — | U-25 | boost is our design (DEC-014) |
| tipRadius / bodyRadius / headRadius | 0.14 / 0.5 / 0.38 | m | U-16 | collision shape |
| comHeight / headHeight | 1.2 / 1.95 | m | U-16 | becomes `pogoLength` |

`DESIGN` (not in the original, our choice): `coyoteTicks` 6, `pressBufferTicks` 8, `safeTicks` 60, `bounceAimMax` 35°.
`SOURCE_A` (confirmed): `tickRate` 120, `fixedPointStep` 1/1024.

---

## 4) Uncertain interpretations (not numbers, but they affect the design)

| Item | What is known (A) | Our interpretation | Grade | How to confirm |
|---|---|---|---|---|
| The original's engine | names `c_trace`, `vec_bounce`, `ang_rotate`, `vec_accelerate`, `data.wrs`, `.wmb/.mdl/.fxo`, `pogo.c` | **3D GameStudio (Acknex / Lite-C)** | C | the official manual (`conitec.net` — **blocked from this environment**) or the binary headers |
| `vec_bounce` | name only | pure reflection `v − 2(v·n)n` (the doc's "simplified" form), with no `e` | C | engine manual + measuring a bounce in a recording |
| `vec_accelerate`/`accelerate` | name only | velocity with speed-proportional friction (matches the linear form 9) | C | manual + measurement |
| `c_rotate` / `ang_rotate` | names | rotation by explicit angle with a collision check (not torque) | C | disassembly |
| `Rays`, `tickRays`, `bRays` | names in the exe + `monolithRays.mdl`, `map3StickRays.mdl` in data.wrs | per-tick ground rays; the `*Rays.mdl` assets may be visual (light rays) rather than physical | C | inspect the `.mdl` contents + disassembly |
| `gravity3` | name | a 3D gravity vector (possibly variable, e.g. in "gravity zones" `gravityPull.fxo`, `gravArrow.fxo`) | C | disassembly |
| `SpringLoad` + `forceUp` | names | the pogo stores a "spring load" then pushes up — supports the spring model, but the formula is unknown | A (existence) / B (model) | — |
| `jump_high` / `jump_degrees` | names in the dll | height/angle parameters for the jump | C | disassembly |
| `turnSpeedVis.fxo` | asset name | a visualisation of "turn speed" ⇒ rotation speed is a gameplay variable | C | — |
| Wind (`fallWind`, `TurbineWind`) | names | wind zones that add an external acceleration (equation 3) | C | not implemented; outside the current scope |

---

## 5) Decisions that cannot be resolved from the documents (they need your choice)

| # | Question | Why the documents don't settle it | Recommendation |
|---|---|---|---|
| D1 | One damping value or two (launch / landing)? | the doc gives a single `c` without a value; a single value can't give a strong launch and a non-bouncy landing together | two values; the second marked `DESIGN` |
| D2 | Linear or Coulomb friction? | the doc offers both | linear (primary in the doc) |
| D3 | Gravity torque on the planted player? | the doc gives the general form α = τ/I without stating the torques | include, calibrated |
| D4 | Keep the speed caps? | `max_speed` "if used" | keep |
| D5 | Bounce pad (minimum speed + aiming)? | not in the doc | keep as DESIGN |
| D6 | If LEVEL_01 breaks after calibration? | — | calibration first, then ask you before changing the geometry |

---

## 6) What would turn any value here into "confirmed"

1. Disassemble `Pogostuck.exe`/`pogoMain.dll` around the found symbols and recover the floating-point literals (the doc's "next stage" steps 1–4). **Done later, once the binaries were supplied:** see `Pogostuck_Original_Physics_Extraction.md` and `Pogostuck_Physics_Constants.csv`. Several assumptions in this file are corrected there (§12 of the extraction): `jump_high`/`jump_degrees` are achievements; `vec_bounce` is never called; there is no restitution/spring k/c/mass; the step is variable per frame.
2. Or measure from recordings: apex height, air time, landing slide distance, wall bounce, rotation rate. The method is in PHYSICS_MASTER.md §5.
3. Every value confirmed later changes from `C` to `MEASURED` in `PhysicsConfig`, with its source cited, and is recorded in this file.
