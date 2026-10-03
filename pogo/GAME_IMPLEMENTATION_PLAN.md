# GAME_IMPLEMENTATION_PLAN — Pogo Ascent

> **الخلاصة بالعربية:** ملفات تحليل Pogostuck المذكورة في المهمة (GDD، جداول الفيزياء، `data.wrs`/`tex.wrs`، فهارس
> النماذج والتكستشر، بيانات المراحل الـ19، الصوت، الـUI) **غير موجودة في المستودع** (راجع `DECISIONS.md` D-001).
> لذلك كل قيم الفيزياء هنا من الدرجة **D** (قيم تصميم افتراضية غير موثقة)، وهي كلها في ملف إعدادات مركزي واحد
> قابل للتعديل بدون لمس الكود. اللعبة مستقلة بالكامل ولا تستخدم أي ملف من Pogostuck.

## 0. Evidence inventory (what the task promised vs. what exists)

| # | Requested input | Found in repo / Drive? | Grade it can support |
|---|---|---|---|
| 1 | Original Pogostuck files | No | – |
| 2 | GDD + Blueprint + FULL GAME DNA report | No | – |
| 3 | Gameplay/Physics analysis tables ("Physics Master Table") | No | – |
| 4 | `data.wrs` / `tex.wrs` extractions | No | – |
| 5 | Model indexes | No | – |
| 6 | Texture / colour indexes | No | – |
| 7 | 19 level datasets | No | – |
| 8 | Audio data | No | – |
| 9 | UI/UX analysis | No | – |
| 10 | Gameplay analysis results | No | – |
| 11 | Any CSV / JSON / PDF / Excel | No (only the unrelated ClipFlow app + its `.arb` strings) | – |

Evidence grades (from the task): **A** confirmed from game files/extracted data · **B** confirmed by
developer/documentation · **C** measured/estimated from gameplay · **D** unconfirmed.
Everything in this project is **D**. It is labelled D wherever it appears and is never promoted.

Rule used for conflicting/missing values: *no source → design default, stored as an editable constant in
the central config, recorded in `PHYSICS_MASTER.md`, never presented as measured.*

## 1. Core Gameplay
Vertical-climb skill game. The player balances on a pogo stick, **leans** to aim, **holds** to charge the
spring, **releases** to launch, **rotates** in the air, **lands**, and repeats. Climbing from the start to
the goal platform is the objective; falling loses progress but is never fatal.
Loop: `Input → Charge → Release → Calculate Launch → Apply Force → Air Control → Collision →
Calculate Landing Velocity → Calculate Next Jump → Repeat` (implemented in `PogoPlayer`, see §3).
Skill expression: lean accuracy, charge timing, landing with the spring pre-loaded (bounce chain), and
**Boost Jump** (rotate far enough in the air, land while holding, release). A boost is never automatic.

## 2. Player Controller (`player/`)
`PogoPlayer` owns the simulation state (position, velocity, angle, angular velocity, charge, rotation
accumulator, boost state, ground contact) and a small explicit state machine:
`GROUNDED_IDLE → CHARGING → (release) → AIRBORNE → (foot contact) → GROUNDED_* / SLIP_TUMBLE`.
It consumes one `PlayerInput(lean: -1..1, jumpHeld)` per physics tick and emits `GameEvent`s
(Launch, Land, HardCollision, Boost, Bounce, Fall, Hazard, Checkpoint, Goal). No tuning constant lives
in the controller — everything comes from `PhysicsConfig`.

## 3. Physics (`physics/`)
* Fixed step (default 120 Hz) with accumulator + clamp; rendering interpolates. Game speed is FPS-independent.
* Continuous collision by adaptive sub-stepping (≤ 0.08 m travel / sub-step).
* Rigid-body style response with real angular dynamics (impulses with arm vector, inertia factor, Coulomb
  friction) for body collisions; analytic spring/landing model for the foot (pogo tip).
* Everything numeric is in `PhysicsConfig` (data driven, JSON-loadable, hot-swappable). See `PHYSICS_MASTER.md`.
* Deterministic: same inputs → same trajectory (unit-tested), which also makes replays and the
  reachability validator possible.

## 4. Camera (`camera/`)
Third-person, perspective, looking down −Z at the gameplay plane with a slight pitch. Components:
smooth follow (critically damped), separate vertical/horizontal tracking with dead-zone, velocity
look-ahead, landing dip, trauma-based hard-collision shake (capped, user-scalable, off option), boost
FOV/zoom kick, fall camera (zoom-out + look-down), user zoom. Output is plain numbers + 4×4 matrices so it
is unit-testable and shared by the GL renderer and the JVM debug renderer.

## 5. Collision (`physics/`)
Convex polygons (boxes, ramps, arbitrary convex shapes), optional kinematic movers, one-way platforms,
sensors (goal, checkpoint, kill-floor). Player = five sample circles along the stick (foot, spring, torso,
head…). Surface classification per contact: Normal Ground, Slope, Wall (incl. ceilings), Platform (one-way),
Bounce Surface, Moving Platform, Hazard, Special Surface (ice/sticky/boost-pad via multipliers), Goal.
Each `SurfaceDef` carries friction, bounce, hazard flag, velocity multiplier, special effect, sound id,
particle id, haptic id.

## 6. Level System (`levels/`)
JSON `LevelData` (id, world, theme, start, goal, platforms, obstacles, hazards, movers, bounce objects,
checkpoints, difficulty, decoration, par time). `LevelLoader` builds colliders + render meshes;
`LevelValidator` checks structure and **reachability by simulating real jumps** with the current
`PhysicsConfig`, so retuning physics re-validates all levels automatically.
Worlds (`WorldData`) group levels with theme, palette, obstacle language, music style, ambience and a
difficulty curve: *basic movement → precision → timing → momentum → boost → complex surfaces → moving
obstacles → high-risk sections*.

## 7. UI (`ui/` model in core, Views in `android/`)
Screens: Main Menu, Play, World Select, Level Select, Pause, Settings (Audio / Controls / Graphics /
Gameplay), Wardrobe, Leaderboard (local), How To Play, Credits, Level Complete. HUD: progress bar, timer,
jump count, boost count, pause, optional FPS; the whole HUD can be hidden. Programmatic `View`s (no XML,
no AndroidX) driven by presenter/state classes in core that are unit-tested.

## 8. Audio (`audio/`)
`AudioManager` (pure logic) over an `AudioBackend`. Data-driven `AudioEvent`s: category (Music, SFX,
Player, Collision, Jump, Landing, Boost, Fall, UI, Environment, Ambient, Goal, Failure), volume, pitch,
random variation, cooldown, spatial (pan/attenuation), priority, voice limiting. The Android backend
**synthesises** every sound from a recipe (no sample files, no licensing issues; original-game audio is
never shipped). Music/ambience are procedurally generated per world style.

## 9. Haptics (`haptics/`)
`HapticManager` with per-event intensity, duration, cooldown and a global scale/off switch; never per-frame.
Events: Jump, Landing, Hard Collision, Boost, Goal, Fall, UI click.

## 10. Progression (`levels/`, `save/`)
Per-level best time / best jumps / boosts / completion / best height, world unlocks (finish N levels of the
previous world), coins earned per completion, item unlocks, lifetime stats, local top-10 times.

## 11. Save System (`save/`)
Versioned JSON, atomic write (tmp + rename), `.bak` recovery, corruption fallback, migration hook, autosave
on level complete / pause / app stop. Stores progress, settings (audio, graphics, controls, gameplay),
unlocked/equipped items, coins, stats.

## 12. Settings (`settings/`)
Video: quality preset, resolution scale, FPS cap, effects, particles, shadows. Audio: master, music, SFX,
ambient. Controls: sensitivity, deadzone, layout (joystick / slide), jump button size/side, haptics.
Gameplay: HUD, camera shake/zoom, tutorial hints. Presets map to concrete renderer/particle budgets.

## 13. Performance targets (`render/`, pools)
Stable 60 FPS on mid-range devices. Low-poly geometry, one static merged level mesh, instanced dynamic
props, no per-frame allocation in the physics/render hot path (pre-allocated float buffers, pooled
particles with fixed capacity), frustum culling, resolution scale, particle budget by quality preset,
fake blob shadows, no textures (flat shading + vertex colour → tiny APK, no texture memory). Benchmarks
in unit tests guard the physics cost per tick.

## 14. Mobile Controls (`touch/`)
Designed for phones, not ported from keyboard: left zone = **lean slider/joystick** (or full-screen horizontal
slide in *Slide* mode), right zone = large **JUMP** button (hold to charge, release to launch). Adjustable
sensitivity, deadzone, button size/opacity, left-handed swap, haptic feedback, multi-touch safe, input
buffering for forgiveness.

## 15. Build System
* `./gradlew :core:test` — game logic and data validation (runs anywhere with a JDK).
* `./gradlew :android-check:test` — compiles the Android layer against the real Android 14 framework jar
  and runs Robolectric tests (no SDK needed).
* `./gradlew :android:assembleDebug` — real APK; **requires the Android SDK** (not available in the authoring
  environment — see `DECISIONS.md` D-006). `.github/workflows/pogo-android.yml` builds it in CI.

## Architecture map (task layout → this repo)

| Task layout | Here |
|---|---|
| `/Scripts/{Player,Physics,Camera,Levels,UI,Audio,Haptics,Save,Settings,Customization,Core}` | `pogo/core/src/main/kotlin/com/pogoascent/<same names>` (+ `render`, `touch`, `particles`, `debug`) and `pogo/android/src/main/kotlin/…` |
| `/Data/{PhysicsConfig,LevelData,ItemData,AudioEvents,WorldData}` | `pogo/core/src/main/resources/data/{physics_config.json, levels/*.json, items.json, audio_events.json, worlds.json, …}` |
| `/Scenes/{MainMenu,Prototype,Level01,Level02,Level03}` | screens in `android/…/screens/` + level JSON `level_01..03` (+ `PhysicsTestScene`) |
| `/Prefabs,/Materials,/Textures,/Models,/Audio,/UI` | procedural: character/props built from primitives in `render/`; materials = per-world palettes; audio synthesised; UI programmatic |

## Phases (each ends with: build → run tests → fix → only then continue)

| Phase | Content | Exit criterion |
|---|---|---|
| 1 | Project setup, `PhysicsConfig` + registry, docs | `:core:test` green, `PHYSICS_MASTER.md` generated & in sync |
| 2 | Player state machine, input, events | scripted-input unit tests |
| 3 | Physics: collision, spring, landing, boost, surfaces | determinism, energy, tunnelling, boost tests |
| 4 | Camera | convergence/bounds/NaN tests |
| 5 | Prototype Level 01 + debug/physics test scene + JVM renderer | level validates reachable; PNG frames inspected |
| 6 | Touch controls | mapper tests |
| 7 | UI models + Android Views | Robolectric screen tests |
| 8 | Audio / haptics | cooldown/priority tests, synthesis sanity |
| 9 | Level system (Levels 02/03, validator) | all levels validate |
| 10 | Worlds | world unlock tests |
| 11 | Customization | item/inventory tests |
| 12 | Save system | round-trip/corruption tests |
| 13 | Optimization | benchmarks, allocation checks |
| 14 | Android build | APK if SDK exists, otherwise honest status |
