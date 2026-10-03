# PHYSICS_MASTER

> **Generated file – do not edit by hand.** Source of truth: `PhysicsParams.kt` (metadata) and
> `core/src/main/resources/data/physics_config.json` (values). Regenerate: `./gradlew :core:generateDocs`.

## Evidence statement (read this first)

The task asked for values from a *Physics Master Table* built from Pogostuck analysis data. **That table, and every
other analysis file, is absent from this repository** (see `DECISIONS.md` D-001). Therefore:

* **Every value below is grade D** (unconfirmed design default). No value is grade A, B or C.
* "Source" says where the number came from; none comes from a measurement. Nothing here is a claim
  that the game matches Pogostuck.
* All values live in one file (`physics_config.json`) and can be edited without touching movement code.
  When real data is available: replace the value, change `source`/`confidence` in `PhysicsParams.kt`,
  regenerate this file, re-tune, and re-run `:core:test` (level reachability is re-validated automatically).

Grades: **A** game files / extracted data · **B** developer / documentation · **C** gameplay measurement ·
**D** unconfirmed.

## Parameters

| Category | Parameter | Value | Unit | Source | Confidence | Editable | Notes |
|---|---|---|---|---|---|---|---|
| Timestep | `fixedTimestepHz` | 120 | Hz | Engineering decision – not a game measurement | D | Yes – JSON (applies on level restart) | Physics tick rate. Game speed never depends on FPS; the renderer interpolates. |
| Timestep | `maxFrameDelta` | 0.1 | s | Engineering decision – not a game measurement | D | Yes – JSON / debug scene (live) | A frame longer than this is clamped (prevents the spiral of death after a pause). |
| Timestep | `maxStepsPerFrame` | 8 | ticks | Engineering decision – not a game measurement | D | Yes – JSON / debug scene (live) | Hard cap of physics ticks per rendered frame; backlog beyond it is dropped. |
| Timestep | `maxSubstepTravel` | 0.08 | m | Engineering decision – not a game measurement | D | Yes – JSON / debug scene (live) | Continuous-collision guard: a tick is split so nothing moves further than this per sub-step (so thin platforms are never tunnelled). |
| Gravity | `gravity` | 22 | m/s² | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Downward acceleration (positive number). Higher = snappier, shorter jumps. |
| Gravity | `fallGravityMultiplier` | 1 | × | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Extra gravity while moving downward (1.0 = symmetric arc). |
| Terminal Velocity | `terminalVelocity` | 42 | m/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Maximum downward speed. |
| Maximum Velocity | `maxSpeed` | 48 | m/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Magnitude clamp on the velocity vector in any direction. |
| Fall Speed | `fallSpeed` | 12 | m/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Downward speed above which the player is flagged as falling (fall camera, fall audio). |
| Jump Charge | `jumpChargeTime` | 0.85 | s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Time to charge the spring from 0 to 100 % while the jump input is held. |
| Jump Charge | `jumpChargeCurve` | 1 | exp | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Launch speed = min + (max-min)·charge^curve. 1 = linear. |
| Jump Charge | `chargeCompression` | 0.35 | m | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | How far the body sinks toward the pinned tip at 100 % charge (visual + head clearance). |
| Jump Power | `jumpPowerMin` | 6.5 | m/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Launch speed of a tap (0 % charge). Mass = 1, so launch 'force' is expressed as speed. |
| Jump Force | `jumpPower` | 17 | m/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Launch speed at 100 % charge (= the full-charge jump power). Apex height ≈ v²/2g. |
| Jump Release | `jumpBufferTime` | 0.08 | s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Input forgiveness: a press this long before touchdown still counts as 'held at landing'. |
| Jump Release | `maxLaunchAngleFromNormalDeg` | 80 | deg | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | A launch is rotated back inside this cone around the ground normal (stops launching into the ground). |
| Jump Release | `launchNormalBlend` | 0 | 0..1 | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | 0 = launch exactly along the stick; 1 = along the surface normal. |
| Momentum | `momentumInherit` | 0.85 | 0..1+ | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Fraction of the pre-launch velocity (tip slide + moving-platform velocity) added to the launch – launch depends on the state before release. |
| Ground Control | `maxLeanAngleDeg` | 75 | deg | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Largest tilt from vertical while standing on the tip (lean input 1.0 = this angle). |
| Turn Speed | `turnSpeed` | 3.2 | rad/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Maximum lean rate while grounded. |
| Acceleration | `acceleration` | 18 | rad/s² | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Angular acceleration of the lean toward the target angle (ground). |
| Deceleration | `deceleration` | 26 | rad/s² | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Angular braking of the lean when it overshoots / input is released (ground). |
| Ground Contact | `groundContactNormalMinY` | 0.35 | n.y | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | A foot contact whose surface normal has y ≥ this is standable ground (0.35 ≈ 69° slope); steeper = wall. |
| Friction | `friction` | 1 | × | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Global multiplier on every surface's friction coefficient. |
| Friction | `slideKineticRatio` | 0.8 | 0..1 | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Kinetic friction = this × static friction (used while the tip slides). |
| Air Control | `airControl` | 1 | × | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Scales air rotation authority (target rate and acceleration). 0 = no steering in the air. |
| Air Control | `airHorizontalAccel` | 0 | m/s² | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Optional direct horizontal push in the air (0 = steering only by rotation). |
| Momentum | `airLinearDrag` | 0.02 | 1/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Linear air drag (velocity *= 1 - drag·dt). |
| Rotation | `rotationSpeed` | 7 | rad/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Target angular speed in the air at full input. |
| Rotation | `airTurnAccel` | 40 | rad/s² | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | How fast the air spin reaches the target (before the airControl multiplier). |
| Angular Velocity | `airAngularDrag` | 0.6 | 1/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Spin decay in the air when there is no rotate input. |
| Angular Velocity | `maxAngularVelocity` | 12 | rad/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Hard clamp of angular speed (collisions can kick spin above rotationSpeed). |
| Bounce | `bounce` | 0.55 | 0..1+ | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Spring rebound: share of the landing speed stored as pre-charge when the jump is HELD at touchdown (bounce chain). |
| Bounce | `passiveBounce` | 0 | 0..1+ | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Same, when the jump is NOT held (0 = the tip just sticks). |
| Bounce | `bounceMinSpeed` | 2 | m/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Landing speeds below this store no charge (lets the stick settle). |
| Landing Response | `landingTangentialRetention` | 0.55 | 0..1 | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Share of sideways speed kept as tip slide after a good landing. |
| Landing Response | `maxLandingTiltDeg` | 55 | deg | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Angle between stick axis and surface normal beyond which the tip slips (bad landing → tumble). |
| Landing Response | `slipRestitution` | 0.25 | 0..1+ | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Restitution of a slipped (bad) landing. |
| Landing Response | `slipSpinFactor` | 0.8 | × | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Angular kick of a slipped landing, proportional to the sideways speed. |
| Collision Response | `collisionRestitutionScale` | 1 | × | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Global multiplier on every surface's bounce for BODY collisions (head, torso, spring). |
| Collision Response | `inertiaFactor` | 0.35 | ×L² | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Moment of inertia = factor·tipOffset² (mass 1). Lower = body spins more when it hits something. |
| Collision Response | `hardCollisionSpeed` | 8 | m/s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Impact speed at which a body hit counts as 'hard' (camera shake, haptic, particles). |
| Collision Response | `penetrationSlop` | 0.005 | m | Engineering decision – not a game measurement | D | Yes – JSON / debug scene (live) | Allowed overlap before positional correction (avoids jitter). |
| Boost Rotation | `boostThreshold` | 285 | deg | Task prompt example – no data file | D | Yes – JSON / debug scene (live) | Rotation accumulated in one flight that arms a boost. The task text quotes 285° as an example; no data file backs it, so it stays grade D. |
| Boost | `boostPower` | 1.35 | × | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Launch-speed multiplier of an armed boost jump (clamped by maxSpeed). |
| Boost | `boostWindow` | 1.2 | s | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | After the boost-arming landing the player has this long to release the jump or the boost expires. |
| Boost | `boostAngularBonus` | 0.15 | × | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON / debug scene (live) | Extra boost multiplier proportional to \|angular velocity\| at touchdown (skill reward). |
| Ground Contact | `tipOffset` | 0.9 | m | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON (applies on level restart) | Centre of mass → pogo tip, along the stick axis. |
| Ground Contact | `tipRadius` | 0.1 | m | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON (applies on level restart) | Radius of the foot circle (the only circle that can 'land'). |
| Ground Contact | `springOffset` | 0.45 | m | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON (applies on level restart) | Centre of mass → spring collision circle. |
| Ground Contact | `springRadius` | 0.12 | m | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON (applies on level restart) | Radius of the spring circle. |
| Ground Contact | `torsoRadius` | 0.28 | m | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON (applies on level restart) | Radius of the torso circle (at the centre of mass). |
| Ground Contact | `headOffset` | 0.7 | m | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON (applies on level restart) | Centre of mass → head circle. |
| Ground Contact | `headRadius` | 0.3 | m | Design default – no analysis data in repo (DECISIONS D-001) | D | Yes – JSON (applies on level restart) | Radius of the head circle. |

## Coverage of the categories required by the task

| Required category | Parameters |
|---|---|
| Gravity | `gravity`, `fallGravityMultiplier` |
| Jump Force | `jumpPower` |
| Jump Power | `jumpPowerMin` |
| Bounce | `bounce`, `passiveBounce`, `bounceMinSpeed` |
| Friction | `friction`, `slideKineticRatio` |
| Acceleration | `acceleration` |
| Deceleration | `deceleration` |
| Air Control | `airControl`, `airHorizontalAccel` |
| Ground Control | `maxLeanAngleDeg` |
| Angular Velocity | `airAngularDrag`, `maxAngularVelocity` |
| Rotation | `rotationSpeed`, `airTurnAccel` |
| Turn Speed | `turnSpeed` |
| Momentum | `momentumInherit`, `airLinearDrag` |
| Collision Response | `collisionRestitutionScale`, `inertiaFactor`, `hardCollisionSpeed`, `penetrationSlop` |
| Landing Response | `landingTangentialRetention`, `maxLandingTiltDeg`, `slipRestitution`, `slipSpinFactor` |
| Boost | `boostPower`, `boostWindow`, `boostAngularBonus` |
| Boost Rotation | `boostThreshold` |
| Maximum Velocity | `maxSpeed` |
| Terminal Velocity | `terminalVelocity` |
| Fall Speed | `fallSpeed` |
| Jump Charge | `jumpChargeTime`, `jumpChargeCurve`, `chargeCompression` |
| Jump Release | `jumpBufferTime`, `maxLaunchAngleFromNormalDeg`, `launchNormalBlend` |
| Ground Contact | `groundContactNormalMinY`, `tipOffset`, `tipRadius`, `springOffset`, `springRadius`, `torsoRadius`, `headOffset`, `headRadius` |

## Derived quantities (computed, not stored)

| Quantity | Formula | Value with current config |
|---|---|---|
| Full-charge vertical apex height | v²/2g | 6.57 m |
| Tap (no charge) vertical apex height | v²/2g | 0.96 m |
| Full-charge flight time (same-height landing) | 2v/g | 1.55 s |
| Moment of inertia (mass 1) | inertiaFactor·tipOffset² | 0.284 |
| Physics tick | 1 / fixedTimestepHz | 8.333 ms |
