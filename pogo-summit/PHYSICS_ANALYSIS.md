# PHYSICS_ANALYSIS.md — Physics analysis before rebuilding (no code changes)

> **Status:** analysis only. No code file was changed. Waiting for the **IMPLEMENT** command.
> **The new source:** `Pogostuck_Physics_Master_Merged_DataWRS.docx` (read in full: 26 equation/evidence sections + a data.wrs analysis + an index of 2,225 entries).
> **Previous sources that remain valid:** `Pogostuck_Physics_Master.xlsx` + the analysis PDF (see PHYSICS_MASTER.md).
> **This file's classification scheme (as the request specifies):**
> **A** = value/behaviour confirmed from the files · **B** = reconstructed value/equation (RECONSTRUCTED) · **C** = unknown value that needs calibration.
> (Mapping to the old scheme in PHYSICS_MASTER.md: A here = A/B there for existence; C here = `TUNE_ME`/grade D there.)

---

## 0) The most important finding up front

**The document does not contain one single numeric value for movement physics.** It says so itself in three places:
- "**The exact numeric constants and the exact original source-code expressions were not fully decompiled yet.**" (§3, Important accuracy note)
- §25 "Parameters still required for an exact Pogostuck clone": `g, μ, e, k, c, m, v_jump, jump_high, jump_degrees, max_speed, air_control, ground_control, Δt, collision_offset` — **all not established**.
- "Final status → Not yet exact: The actual Pogostuck numeric constants … have not yet been fully recovered."

So the document gives us:
1. **The names of the mechanisms present in the original files (A — existence only).**
2. **24 standard textbook equations, all marked [RECONSTRUCTED] (B).** The document states clearly that they are "not claimed to be byte-for-byte identical to the original source".
3. **An index of data.wrs assets (A — names/sizes/offsets only)**, with no physics values.

**Practical consequence:** following the rule "don't invent values when the document has a suitable one" leaves no document value to adopt. Every number will either stay as it is (calibrated `TUNE_ME`) or be calibrated against measurable targets (section 5). All of these are listed in `PHYSICS_UNCERTAINTIES.md`.

---

## 1) Current Physics Architecture

### 1.1 The engine used in **our** project
- **TypeScript + Three.js (WebGL2) inside an Android WebView shell** (DECISIONS DEC-002). **No third-party physics engine** (no Box2D/Rapier/RigidBody). All the physics is **our own deterministic code** in `game/src/sim/`.
- `src/sim` is a pure module (no DOM/Three/`Math.random`/`Date.now`). This is enforced by `tests/architecture.test.ts`.
- Coordinates: 2D (x right, y up), metres/seconds; angles in radians internally.

### 1.2 The engine of the **original** game (inference, not confirmed)
The symbols in the document (`c_trace`, `c_rotate`, `vec_bounce`, `vec_accelerate`, `accelerate`, `ang_rotate`, `vec_rotateaxis`), the file types (`data.wrs`, `.wmb`, `.mdl`, `.fxo`) and `pogo.c` match the API naming of the **Conitec 3D GameStudio (Acknex / Lite-C)** engine.
**Grade C (inference):** I couldn't check the manual because `conitec.net` is blocked in this environment. If confirmed, the original's ground detection and collisions are **trace-based** (`c_trace`), rotation is an **explicit angle with a collision check** (`c_rotate`), and `vec_bounce` is a **pure reflection**. All of that would still need validating.

### 1.3 The current system's structure (files and roles)

| Role | File / function | How it currently works |
|---|---|---|
| **Fixed timestep** | `game/Game.ts` → `tick(dt)` | Accumulator: `acc += dt`; `while (acc ≥ DT)` → `pogo.step(input)`; cap of 12 ticks/frame; `dt` clamped to ≤ 0.1 s; render interpolation `alpha = acc/DT`. **The physics runs at 120 Hz regardless of FPS.** |
| **Tick rate** | `sim/math.ts` | `TICK_RATE = 120`, `DT = 1/120` (from XLSX V-001, A) |
| **Determinism** | `PogoPhysicsController.quantize()` | Rounds x,y,vx,vy,angle,ω,lx,ly,slideV to a 1/1024 grid (XLSX F-003, A) |
| **Player controller (state machine)** | `sim/PogoPhysicsController.ts` → `stepPogo` | Modes: `GROUNDED / CHARGING / AIR / SLIDING / FINISHED` |
| **Planted phase** | `stepPlanted` (lines 76–165) | The tip is fixed in the platform's frame. **The body's position = tip + axis·lc** (kinematic constraint, l.137–138). Slide along the tangent with constant deceleration (l.85–93) |
| **Air phase** | `stepFree` (lines 170–205) | Gravity `vy −= g·DT` (l.190) → speed caps (l.191–192) → semi-implicit integration over 1–4 substeps ≤ 0.12 m (l.195–202) → collisions |
| **Gravity** | `stepFree` l.190 | `vy ← vy − g·Δt`, `g = 26` (`TUNE_ME`). Not applied in the planted phase (the body is constrained) |
| **Jump** | `sim/JumpSystem.ts` | `charge` = **tick counter** (≤ 84). On release: `power = (charge/84)^curve`; `speed = lerp(8.5, 19, power)·surface.mult`; **`v = v_platform + slide·0.75 + axis·speed`** (an instantaneous velocity assignment) |
| **Ground detection** | `stepPlanted` l.95–105 + `handleTip` + `plant` | **Not raycast:** circle-vs-convex-polygon overlap (`geometry.circleVsConvex`) for contact, `closestOnPoly` + tolerance `SUPPORT_TOL = 0.05` (hardcoded, l.31) to keep support |
| **Collision** | `resolveContacts` / `handleTip` / `handleBody` | 3 circles (tip 0.14, torso 0.5, head 0.38) vs convex polygons, up to 3 passes; penetration correction `x += n·depth`; split into vn/vt |
| **Bounce** | `CollisionResponse.reboundSpeed` | `vn' = e·|vn|` if `|vn| ≥ restSpeed`, otherwise 0; e = 0.18 floor / 0.45 wall |
| **Friction** | 3 different forms (see Problems) | constant deceleration on the planted slide; Coulomb `μ·g·n_y·h` on a sliding contact; `vt·(1−energyLoss)` on impact |
| **Slopes** | `SurfacePhysics.classifyNormal / isStable` | classes floor/slope/steep/wall/ceiling from the normal; stable if `tan α ≤ tan(52°)·friction` |
| **Rotation** | ground l.122–136, air l.183–187, `impactKick`, `plant` l.337 | **kinematic target tracking:** ω approaches a desired rate with `turnSpeed` as an angular-acceleration cap. Before rotating on the ground, check that the body doesn't enter a solid (`bodyPenetration`) |
| **Surfaces** | `sim/SurfacePhysics.ts` | 7 materials (normal/bounce/slippery/sticky/hazard/boost/goal) with friction/restitution/velocityMultiplier |
| **Boost** | `sim/BoostSystem.ts` | rotation accumulation ⇒ ready ⇒ adds `boostPower` along the axis (our design DEC-014) |
| **Prediction** | `sim/prediction.ts` | `simulateJump` runs the same real `stepPogo` (aim guide + bots) |
| **Parameters** | `sim/PhysicsConfig.ts` | **42 parameters** in one place (36 `TUNE_ME`, 2 `SOURCE_A`, 4 `DESIGN`) + live sliders in the Lab |
| **Input** (separate) | `input/TouchControls.ts`, `InputSource.ts` | produces `PogoInput {tilt, jumpHeld, boostPressed, pull, cancel}` once per tick; the physics never sees raw touch |
| **Visual presentation** (separate) | `render/Character.ts` | spring compression is **cosmetic**, computed from `charge01` and `landK` (not from the physics) |
| **Camera** (separate) | `render/CameraRig.ts` + `GameRenderer.onEvent` | only reads `x,y,vx,vy,grounded,charging,charge01` + events (land/hard_impact/boost) |
| **Debug** | `ui/Lab.ts` | text readouts (mode, velocity, angle, ω, charge, contact, normal, jump cadence) + scripted tests (Jump/Bounce/Boost/Fall). **No vector drawing** |

**The separation the request asks for is already in place:** Physics State (`src/sim`) ⟂ Input (`src/input`) ⟂ Visual (`src/render`) ⟂ Camera (`render/CameraRig`). This will be kept.

### 1.4 Current behaviour measured (vertical jump from flat ground in the physics_test level, actual stepPogo run)

| Charge | Launch speed | Measured apex (foot point) | Theory H = v²/2g | Measured air time | Theory T = 2v/g |
|---|---|---|---|---|---|
| 0 | 8.50 m/s | 1.283 m | 1.389 m | 78 ticks | 78 ticks |
| 0.5 | 13.75 m/s | 3.464 m | 3.636 m | 126 ticks | 127 ticks |
| 1 | 19.00 m/s | 6.702 m | 6.942 m | 175 ticks | 175 ticks |

The air time matches equation (17) almost exactly. The small apex gap (0.1–0.24 m) comes from the measurement point (the foot, not the centre of mass) plus the semi-implicit Euler bias (≈ −v·Δt/2). **So the current air integrator already obeys equations (1), (2), (14), (16), (17).**

---

## 2) Current Problems (against the requested model + the document)

| # | Problem | Evidence in the code | Effect |
|---|---|---|---|
| P1 | **There is no physical pogo spring.** "Charging" is a tick counter, and the jump is an instantaneous velocity assignment (`v = axis·speed`) | `JumpSystem.performLaunch`; `stepPlanted` l.144–159 | The jump is not "a result of the pogo system". There is no k, c, L0, L or spring force |
| P2 | **The planted phase is a geometric constraint:** the position is set each tick as `tip + axis·lc` | `stepPlanted` l.137–138 | No real compression and no axial oscillation; landing absorbs all normal velocity at once (`plant`) |
| P3 | **No mass or forces:** everything is at velocity level | the whole of `src/sim` | Equations F/m, a = F/m (13, 14, 24) can't be expressed as written |
| P4 | **Ground detection uses overlap, not rays:** no ground distance, no "slope angle under the player" before contact | `handleTip`, `stepPlanted` (SUPPORT_TOL) | Doesn't match equation (5) (c_trace/Rays); no data for the Debug ray drawing |
| P5 | **Friction in 3 inconsistent forms:** constant deceleration `approach(slideV,0,16·f·Δt)` · Coulomb `μ·g·n_y·h` · `vt·(1−0.15)` on impact | `stepPlanted` l.91, `CollisionResponse.frictionStep`, `keptTangential` | None of them is the document's primary form `v_t(1−μΔt)` (9) |
| P6 | **Rotation is kinematic:** a target angle ×12 and ω approaching it with an acceleration cap; the impact spin uses an arbitrary lever table (`LEVER`) | l.122–136, l.183–187, `CollisionResponse.LEVER/impactKick` | Doesn't match α = τ/I (21). No torque from gravity or from the contact point |
| P7 | **Magic numbers outside PhysicsConfig** | `MAX_STEP=0.12`, `SUPPORT_TOL=0.05`, angle gain `12`, `impact > 3` (wall_hit event), `hardImpactSpeed*1.2`, `vt*0.5` on a bounce pad, split `ny > 0.7` floor/wall in `handleBody`, `6°` floor, `MAX_PLANT_ANGLE=75°`, `spinIdle > 30`, tolerance `0.004`, slide stop `0.01`, substeps `≤4`, passes `3` | Contradicts the "one parameter centre" requirement |
| P8 | **Hard speed caps** clamp v every tick: `maxHorizontalSpeed=22`, `maxFallSpeed=32` | l.191–192 + `performLaunch` | Hidden non-physical behaviour; the document says max_speed is "if used — unknown" |
| P9 | **No vector debug drawing** (gravity/velocity/normal/tangent/rays/contact point/spring) | `ui/Lab.ts` text only | Doesn't meet the requested Debug Physics Mode |
| P10 | **No test proving independence from 30/60/120 FPS** at the game-loop level (`Game.tick`), only at tick level | `tests/determinism.test.ts` | The claim is correct by design but not proven by a test |
| P11 | The visual spring compression is **cosmetic**, disconnected from the physics | `Character.ts` (crouch/landK) | When the physics spring is added, the visual must read the real compression |
| P12 | **Coupling risk:** `simulateJump`, `analysis.ts`, `routeBot.ts`, `tests/fixtures/level01.route.json` and `TrajectoryGuide` all depend on the current launch shape | — | Any change to the launch path changes the recorded route and the solvability results (see the plan) |

**Things that are correct and will stay:** fixed 120 Hz step + accumulator; determinism + quantisation; vn/vt split on contact (6); bounce `vn' = −e·vn` (8)/(22); penetration correction (18); removing normal velocity on a non-bouncy contact (19) (in `plant`); slope classification from the normal; the separation between physics and input/visuals/camera; momentum conservation in the air.

---

## 3) Document-derived Parameters (A — confirmed from the files)

### 3.1 Confirmed from the new document (existence of mechanism/name only — **no numbers**)
| Mechanism | Evidence (exact name in the document) | File | Grade | What we take from it |
|---|---|---|---|---|
| Gravity | `gravity3`, `gravity`, `rGravity` | Pogostuck.exe | A (existence) | a gravity vector exists; its value is unknown |
| Friction | `friction` | exe | A | there is friction; its form/value is unknown |
| Ground contact | `GroundContact` | exe | A | an explicit contact flag |
| Surface normal | `surfaceNormal` | exe | A | the normal is computed and used |
| Spring load | `SpringLoad` | exe | A | **a spring-load concept** in the pogo (supports building a spring model) |
| Upward force | `forceUp` | exe | A | an upward/along-axis force |
| Acceleration | `Accelerating`, `accelerate`, `vec_accelerate` | exe + dll | A | an acceleration function |
| Speed | `speed`, `rootSpeed`, `Speed` | exe | A | speed is calculated |
| Jump | `Jump`, `jump`, `jumpH`, `jump_high`, `jump_degrees` | exe + dll | A | jump height and **jump angle** are parameters |
| Slopes | `slope`, `slopeT`, `sloped` | exe | A | separate slope handling |
| Rays | `Rays`, `tickRays`, `bRays`, `c_trace` | exe + dll | A | **detection by rays/trace**, apparently every tick (`tickRays`) |
| Bounce | `vec_bounce` | dll | A | a vector-reflection function |
| Rotation | `c_rotate`, `ang_rotate`, `vec_rotate`, `vec_rotateaxis` | dll | A | rotation functions (including about an axis) |
| Pogo code | `pogo.c` | exe | A | a dedicated pogo source module |

### 3.2 Confirmed from data.wrs (names only)
| Family | Count | Example | What it suggests |
|---|---|---|---|
| Collision meshes | 151 | `mainCol0…67.mdl`, `appA_COL_*`, `map3COL_*` | **simplified collision meshes separate from the visuals** (matches XLSX F-008) |
| Slippery surface | — | `map3COL_slippery.mdl`, `appA_COL_Ice.mdl`, `appA_COL_slime*.mdl` | separate surface types (slippery/ice/sticky) by name |
| Rays | 7 | `monolithRays.mdl`, `map3StickRays.mdl` | assets named "Rays" (their meaning is unknown) |
| Wind | — | `fallWind.mdl`, `map3TurbineWind.mdl`, `slopeBwind.mdl` | wind zones (we haven't implemented them) |
| Extra jump | — | `monolithExtraJump.mdl` | extra jump (matches "Double jump" XLSX F-016) |
| Rotation speed | — | `turnSpeedVis.fxo` | **a visualisation of "turn speed"** ⇒ rotation speed is a parameter in the original |

### 3.3 Confirmed from the previous sources (still valid, not contradicted by the new document)
| Value | Source | Grade |
|---|---|---|
| **Δt = 1/120 s (120 tick/s)** | XLSX V-001/V-002/V-003, read from the replay debug panel | A |
| Fixed-point grid 1/1024 (22.10) | XLSX F-003/F-004 | A |
| Mean interval between two jumps ≈ 150 ticks (1.249 s) | XLSX V-025/V-026 (116 jumps in 144.9 s) | A (measured) |
| Launch on button **release** | XLSX V-017..V-019 | B in XLSX (meaning) |
| Control = **stick angle + jump timing**, not velocity | XLSX F-011 + PDF p.10/p.19 | B |
| Charge then launch (`pogoLoad2` → `pogoLaunch2`) | PDF p.3 / XLSX F-036/037 | B |

> **Conflict 1:** the document lists `Δt` among the unknowns (§25), but XLSX V-001 read **120 tick/s** from the original's debug panel (A). **Decision:** keep 120 Hz, because the XLSX evidence (a direct reading) is stronger than the document's "unknown" (which only means it wasn't extracted from the binary).

---

## 4) Reconstructed Parameters & Equations (B — RECONSTRUCTED)

Every equation in the document is [RECONSTRUCTED]. The table shows **each equation → its current status in the code → what we'll do**:

| # in doc | Equation | Current status | Planned decision |
|---|---|---|---|
| 1 | `x ← x + v·Δt` (explicit) | we use semi-implicit `x ← x + v'·Δt` | **Keep semi-implicit**, which is the doc's own form in (14) and (23, step 7). Conflict 2 below |
| 2 | `v_y ← v_y − g·Δt` | ✔ `stepFree` l.190 | Move into `Gravity.ts` and also apply it in the planted phase (into the spring equation) |
| 3 | `v' = v + a·d̂·Δt` | partial (boost pad `acceleration`) | `Movement.ts`: a general acceleration (control, pad) |
| 4 | `s = ‖v‖` | ✔ (l.195) | a general `speed()` + Debug readout |
| 5 | GroundContact via ray + n̂ | ✘ (overlap) | **New `GroundDetection.ts`**: ray along the pogo axis + vertical ray (`c_trace` analogue) |
| 6 | `v_n=(v·n)n`, `v_t=v−v_n` | ✔ (handleTip/handleBody) | a unified `SurfaceMath.decompose` |
| 7 | `d_s = d − (d·n)n` | partial (slide on the tangent) | used for the planted slide and control on slopes |
| 8 | `v' = v − (1+e)(v·n)n` | equivalent (reboundSpeed) | `Bounce.ts` with the exact form + `restSpeed` threshold |
| 9 | `v_t' = v_t(1 − μΔt)` | ✘ (3 other forms) | **`Friction.ts`: primary linear form**, Coulomb kept as an option |
| 10 | `x = L0 − L`, `F = kx` | ✘ | **New `PogoSpring.ts`** |
| 11 | `F = kx − c·v_axis` | ✘ | in `PogoSpring.ts` |
| 12 | `F_up = F_spring·p̂`, `a = F/m` | ✘ | in `PogoSpring.ts` |
| 13–14 | `a = g + F_spring/m + a_f + a_ctrl` | ✘ | integrating the planted phase as forces |
| 15 | `v' = v + J/m·n̂` or `v' = v_jump` | ✔ second form (set) | **Replaced** by a launch that results from the spring (10–12). The document doesn't say which form the original uses |
| 16–17 | `H = v0²/2g`, `T = 2v0/g` | ✔ measured (table 1.4) | used as **calibration and test targets** |
| 18 | `x' = x + d·n̂` | ✔ | `Penetration.ts` + `collisionOffset` in Config |
| 19 | `v' = v − (v·n)n` when `v·n<0` | ✔ in `plant` | kept for non-bouncy contact |
| 20–21 | `θ' = θ+ωΔt`, `α = τ/I` | partial (no τ/I) | **New `Rotation.ts`** in torque/inertia form |
| 22 | `J = −m(1+e)v_n` | equivalent | in `Bounce.ts` + **angular impulse** `Δω = (r × J)/I` replacing the LEVER table |
| 23 | 7 steps (gravity → detect → split → bounce → friction → recombine → integrate) | different order | **Becomes the official tick order** (with the planted phase separate) |
| 24 | the full pogo model | ✘ | the reference for implementing `PogoSpring` + `stepPlanted` |

> **Conflict 2 (integration):** (1) is explicit while (14)/(23) are semi-implicit. **Decision:** semi-implicit (more stable, already in use, and the doc's own form in the full model).
> **Conflict 3 (friction):** (9) offers linear damping as the primary form and Coulomb as an alternative; the current code uses neither exactly. **Decision:** linear by default per the document, with Coulomb as a selectable option (`frictionModel`), and μ calibrated.
> **Conflict 4 (jump):** (15) offers impulse or speed assignment, (10–12/24) offer a spring. The document doesn't settle which the original uses. **Decision:** spring, as the request requires; we record that this is a design choice and not proven to be the original.
> **Conflict 5 (rotation):** (21) is torque/inertia, but the engine names (`c_rotate`/`ang_rotate`, if it is Gamestudio) suggest a direct angle rotation with a collision check (C). **Decision:** torque/inertia per the request and the document, keeping the collision check before rotating on the ground (current behaviour, a `c_rotate` analogue).

---

## 5) Unknown Parameters (C — need calibration)

Full details (value, unit, source, calibration method, effect on movement) are in **`PHYSICS_UNCERTAINTIES.md`**. Summary:

| Parameter | Current value | Status | Calibration target |
|---|---|---|---|
| `g` | 26 m/s² | C | air time = 2v/g; 150-tick jump rhythm (XLSX A) |
| `μ` (linear friction) | — (new) | C | the same slide distance as today for normal ground (≈ stops within 0.2 s) and ice (keeps momentum) |
| `e` floor/wall | 0.18 / 0.45 | C | unchanged unless needed |
| `k`, `c` spring | — (new) | C | launch envelope 8.5–19 m/s + extension time ≤ 5–6 ticks + landing that settles (equivalent e ≈ 0.18 ⇒ ζ ≈ 0.48) |
| `m` | — (new) | **not identifiable from trajectories** | only the ratios k/m, c/m, τ/I affect motion ⇒ normalise `m = 1` |
| `L0` (pogo length) | 1.2 (comHeight) | C | the character's proportions |
| `x_max` / compressed length | — (new) | C | from k/m and the top speed: v ≈ x·√(k/m) |
| `I` (inertia) | — (new) | C | I = m·r_g²; calibrated so that τ_max/I reproduces the current angular acceleration (1600°/s²) |
| `max_speed` | 22 horizontal / 32 fall | C ("if used") | keep, flagged as a non-original gameplay constraint |
| `air_control`, `ground_control` | 1 / 1 | C | unchanged |
| `jump_high`, `jump_degrees` | — | C (the document doesn't know their meaning) | mapped as: jump_high ↔ top launch speed via H=v²/2g; jump_degrees ↔ tiltMaxAngle (assumption C) |
| `collision_offset` | 0.05 (hardcoded) | C | moved into Config |
| `rayLength` | — (new) | C | L0 + a small margin (sees the ground before contact) |
| `Δt` | 1/120 | **A from XLSX** (unknown in the doc) | not calibrated |

**Inferred calibration values (not final, for transparency, computed from undamped equations):**
- Extension time ≈ a quarter period `(π/2)/ω_n`. For it to be ≈ 5 ticks (42 ms): `ω_n ≈ 37.7 rad/s ⇒ k/m ≈ 1420 s⁻²`.
- Ideal top launch speed `v ≈ x_max·ω_n` ⇒ for 19 m/s: `x_max ≈ 0.50 m` before gravity/damping losses.
- From the current landing restitution 0.18: `ζ = −ln e / √(π² + ln² e) ≈ 0.48 ⇒ c = 2ζ√(k·m)`.
- **Conflict 6 (consequence of the above):** a single damping `c` cannot give both a strong launch and a non-bouncy landing. See decision D1 in section 9.

---

## 6) Physics Equations (planned implementation — mapped to the document)

```
Each tick (Δt = 1/120 s, A):
AIR:
  (2)  v ← v + g·Δt                                  g = (0, −g)
  (3)  v ← v + a_ctrl·Δt                              (air: angular only — see 20/21)
  (21) α = τ_ctrl/I − c_ω·ω ;  ω ← ω + α·Δt ;  θ ← θ + ω·Δt
  (1/14) x ← x + v·Δt  (semi-implicit, substeps ≤ maxStepDistance)
  (5)  rays: from the body centre along −p̂ (length rayLength) + a vertical ray ⇒ GroundContact, n̂, distance, slope angle
  collision (circles vs polygons):
       (18) x ← x + d·n̂                              penetration correction
       (6)  v_n = (v·n̂)n̂ ;  v_t = v − v_n
       tip landing on a surface: becomes PLANTED (the spring absorbs v·p̂) — see below
       otherwise: (8/22) v_n' = −e·v_n  (0 if |v_n| < restSpeed) ;  (9) v_t' = v_t(1 − μΔt)
                  (22) Δω = (r × J)/I                angular impulse from the contact point instead of the LEVER table
PLANTED (tip fixed by static friction; degrees of freedom: L and θ around the tip):
  (10) x_s = L0 − L
  (11) F_s = k·x_s − c·L̇                            (c depends on the phase — decision D1)
  (charge) F_rider = k·x_pre(power)                  the rider presses the spring (preload) while the button is held
  (12–14) m·L̈ = F_s − F_rider − m·g·cos(θ−θ_n)·…   axial component of gravity ; L̇ ← L̇ + L̈Δt ; L ← L + L̇Δt
  (21) I·θ̈ = τ_ctrl + m·g·L·sin(θ)                 balance torque + gravity torque (inverted pendulum)
  position of the centre of mass = tip + p̂(θ)·L       (generalised coordinates — no teleport: L and θ are integrated)
  (9)  slide of the tip along the tangent: v_slide' = v_slide(1 − μ_surface·Δt) ; (7) movement projected onto the surface
  takeoff: when L ≥ L0 and L̇ > 0 ⇒ v = v_platform + p̂·L̇ + (ω×r) + v_slide·retain  ⇒ the jump emerges from the spring
```
- **Fixed timestep:** unchanged (120 Hz + accumulator); a test is added for 30/60/120 FPS.
- **Determinism:** the 1/1024 quantisation also applies to the new variables (L, L̇).

---

## 7) Files to Modify

| File | What changes | Why |
|---|---|---|
| `game/src/sim/PhysicsConfig.ts` | add mass, springStiffness, springDamping (+ landing damping per D1), pogoLength, maxCompression, inertia, controlTorque, groundFriction μ, frictionModel, rayLength, collisionOffset, maxStepDistance, substep/pass counts, all the P7 magic numbers; each with a status (`TUNE_ME`/`RECONSTRUCTED`/`SOURCE_A`/`DESIGN`) and a source | one parameter centre |
| `game/src/sim/PogoState.ts` | new fields: `springLen`, `springVel`, `springForce`, `compression`, `ground ray` (distance/normal/hit), `debug` | spring state + debug |
| `game/src/sim/PogoPhysicsController.ts` | `stepPlanted` → integrate L and θ with forces; `stepFree` → the (23) order + rays; `handleTip` → hand the axial velocity to the spring; `handleBody` → angular impulse; remove the magic numbers | the core of the rebuild |
| `game/src/sim/JumpSystem.ts` | `performLaunch` becomes "takeoff from the spring"; the charge becomes spring preload | P1 |
| `game/src/sim/CollisionResponse.ts` | becomes a facade over `Bounce`/`Friction`; remove `LEVER` | P5, P6 |
| `game/src/sim/SurfacePhysics.ts` | friction values become per-surface linear μ; MAX_PLANT_ANGLE / floor 6° move into Config | P5, P7 |
| `game/src/sim/BoostSystem.ts` | only adapted to the new ω (logic unchanged) | — |
| `game/src/sim/geometry.ts` | add `rayVsConvex` (ray vs convex polygon: t, point, normal) | (5) |
| `game/src/sim/prediction.ts` | simulate the charge as spring preload | aim guide / bots |
| `game/src/sim/analysis.ts`, `routeBot.ts` | adapt to the new launch (same API) | solvability |
| `game/src/game/Game.ts` | use the extracted pure `FixedStepper` | FPS test |
| `game/src/render/Character.ts` | the visual spring compression reads `state.compression` | P11 |
| `game/src/render/GameRenderer.ts` | attach `PhysicsDebugDraw` | P9 |
| `game/src/ui/Lab.ts` | new readouts + Debug Draw toggle + scenario buttons | P9 |
| `game/tests/physics.test.ts`, `determinism.test.ts` | update expectations that depend on the instantaneous launch | — |
| `game/tests/fixtures/level01.route.json` | **regenerate** with the bot after verifying solvability (not edited by hand) | P12 |
| `PHYSICS_MASTER.md`, `DECISIONS.md`, `GAME_IMPLEMENTATION_PLAN.md` | document the change | — |

**Will not be touched:** UI, input (`TouchControls` — only its output interface is used), the camera (it reads the same fields), the level system (`LevelData`, `level01` geometry — unless solvability breaks; that would be reported first), the save system, audio, haptics. Animations: only the spring compression's data source changes.

## 8) Files to Create

| File | Contents |
|---|---|
| `game/src/sim/physics/Gravity.ts` | (2) |
| `game/src/sim/physics/Integrator.ts` | (1/14) semi-implicit + `speed()` (4) |
| `game/src/sim/physics/SurfaceMath.ts` | (6) split, (7) projection, slope angle |
| `game/src/sim/physics/GroundDetection.ts` | (5) rays: `c_trace` analogue (along the axis + vertical) → `GroundHit` |
| `game/src/sim/physics/Bounce.ts` | (8) (19) (22) linear + angular impulse |
| `game/src/sim/physics/Friction.ts` | (9) linear (primary) / Coulomb (option) |
| `game/src/sim/physics/PogoSpring.ts` | (10)(11)(12)(13)(14)(24) compression, force, damping, preload, takeoff |
| `game/src/sim/physics/Rotation.ts` | (20)(21) τ/I, control torque, gravity torque, angular damping |
| `game/src/sim/physics/Penetration.ts` | (18) + `collisionOffset` |
| `game/src/sim/physics/index.ts` | gathers the modules |
| `game/src/game/FixedStepper.ts` | a testable pure accumulator |
| `game/src/render/PhysicsDebugDraw.ts` | lines/arrows: gravity, velocity, normal, tangent, rays + hit point, contact point, spring axis coloured by compression; readouts: slope angle, compression, spring force, speed, ω |
| `game/tools/calibrate-physics.ts` | computes k/m, c, x_max, I, μ from the targets (section 5) and prints a report — **every number it produces is marked ESTIMATED/CALIBRATED** |
| `game/tests/physics-model.test.ts` | the 13 required scenarios (section 9) |
| `PHYSICS_UNCERTAINTIES.md` | **created now** with this analysis |
| `PHYSICS_IMPLEMENTATION_REPORT.md` | after implementation |

## 9) Implementation Plan (after IMPLEMENT)

**Step 0 — Baseline:** record the current measurements (table 1.4, jump rhythm, slide distances, wall bounce, the LEVEL_01 route) as the "before" reference for the report.

**Step 1 — Physics modules (no behaviour change):** extract the existing functions into `sim/physics/*` with **identical** results, plus PhysicsConfig and the magic numbers. Gate: all 78 tests pass with no change in values (bit-for-bit determinism).

**Step 2 — Rays (5):** `rayVsConvex` + `GroundDetection` used for detection/display first, then as the support condition for the planted phase in place of `SUPPORT_TOL`. Gate: tests + Debug Draw screenshot.

**Step 3 — Friction (9) + bounce (8/22):** unify the friction; angular impulse from the contact point. Calibrate μ to keep slide distances. Gate: friction/ice/wall tests.

**Step 4 — Rotation (20/21):** torque/inertia + control torque + gravity torque in the planted phase. Calibrate I and τ_max to reproduce the current rotation response. Gate: tilt/rotation tests + boost.

**Step 5 — Pogo spring (10–14, 24):** the planted phase as integrated L and θ; charge = preload; takeoff from the spring; landing compresses the spring. Calibrate k/m, c, x_max with `calibrate-physics.ts` to the targets: envelope 8.5–19 m/s (±3%), extension ≤ 6 ticks, non-bouncy landing, jump rhythm near 150 ticks. Gate: launch/landing/cadence tests.

**Step 6 — LEVEL_01 compatibility:** run `analysis.ts` (solvability) and `routeBot` → regenerate `level01.route.json` → the route replay in the real app (E2E). If a hop fails: report first, before touching the geometry.

**Step 7 — Debug Physics Mode:** `PhysicsDebugDraw` + Lab readouts (gravity, velocity, normal, tangent, ground contact, slope angle, compression, spring force, speed, ω, contact point, ray drawing). Gate: an in-game screenshot.

**Step 8 — Tests (13 scenarios):**
1. Standing on flat ground (stable; compression settles at the static value `m·g/k`).
2. Descending a slope (the slide follows the tangent; `v·n ≈ 0`).
3. Vertical jump (`H ≈ v0²/2g`, `T ≈ 2v0/g`).
4. Jumping from a slope (direction = pogo axis; takeoff speed at the same charge ≈ flat ground).
5. Hitting a wall (`v_n' = −e·v_n`, `v_t` reduced by friction).
6. Hard impact (`hard_impact` event, no tunnelling).
7. Friction (`v_t(t) = v_t0·(1−μΔt)^n` matches the analytic formula).
8. Losing ground contact (leaving the edge ⇒ `GroundContact = 0` from the ray).
9. Returning to the ground (landing ⇒ compression > 0 then settling, no jitter).
10. Repeated jumping (10 jumps in a row, stable, rhythm close to 150 ticks).
11. Player rotation (`α = τ/I` matches; ω/θ integration).
12. Different speeds (landing at 5/15/30 m/s with no tunnelling or explosion).
13. Different FPS: the same input script through `FixedStepper` at 30/60/120 FPS ⇒ **bit-identical** final state.
Plus the existing tests (determinism, solvability, route, touch, systems).

**Step 9 — Build and run:** tsc + vitest + E2E route in the real app + screenshots of Debug Draw + APK build; then write `PHYSICS_IMPLEMENTATION_REPORT.md` with every value and its source/grade/effect.

### Decisions that need your approval (I'll proceed with the recommendation if you send IMPLEMENT without notes)
| # | Decision | Options | Recommendation |
|---|---|---|---|
| D1 | Spring damping | (a) a single `c` per the document ⇒ either bouncy landings or a weak launch · (b) **two-phase damping:** `springDamping` small during launch + `landingDamping` (rider absorption) larger on landing — **the second value is not in the document** | **(b)**, and record `landingDamping` as `DESIGN` + C |
| D2 | Friction form | linear (doc, primary) / Coulomb (alternative) | **linear** + Coulomb option |
| D3 | Gravity torque in the planted phase | include (more physical: an inverted pendulum the control torque must hold) / exclude (identical to the current behaviour) | **include**, calibrated so that the maximum tilt 65° can be held |
| D4 | Speed caps 22/32 | keep / remove | **keep**, flagged as non-original |
| D5 | Bounce pad (minimum speed + tilt aiming) | keep as a design feature / turn it into pure doc bounce | **keep** (DESIGN), flagged |
| D6 | If a LEVEL_01 hop breaks after calibration | adjust the calibration first / adjust the level geometry | **calibration first**; geometry only after asking you |
