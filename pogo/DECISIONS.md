# DECISIONS

Every decision that the (missing) analysis data could not answer is recorded here, with the reason.
Format: **ID – decision** · *why* · *how to change it*.

## D-001 – The analysis inputs listed in the task do not exist in this repository
The task assumes the project contains Pogostuck analysis files (GDD/Blueprint/Game-DNA report, Physics
Master Table, `data.wrs` / `tex.wrs` extractions, model/texture indexes, the 19 level datasets, audio data,
UI/UX analysis, CSV/JSON/PDF/Excel files).
I searched the working tree, every git branch/ref, the whole filesystem (`*pogo*`, `*.wrs`, `*GDD*`,
`*physics*master*`) and Google Drive (`Pogostuck`, `GDD`, `physics`). **Nothing was found.** The repository
contains only an unrelated Android clipboard-manager app ("ClipFlow", `app/`).
* Consequence: **no value in this project can be graded A, B or C.** Every physics number is grade **D**
  (design default / unverified) and is labelled so in `PHYSICS_MASTER.md` and in the code registry.
* The one number the task itself mentions, **285°** for the boost rotation, is recorded as grade **D**
  with source "task prompt example – no data file", because a number quoted in a prompt is not evidence.
* Nothing was invented *and presented as measured*. When the real tables arrive, replace the values in
  `core/src/main/resources/data/physics_config.json`, update the `source`/`confidence` fields in
  `PhysicsParams.kt`, run `./gradlew :core:generateDocs`, and re-tune.

## D-002 – Technology: Kotlin, pure-JVM game core + thin Android layer (platform APIs only)
No game engine exists in the repo (the existing project is Native Kotlin + Compose). Unity/Godot cannot be
installed or run in this environment and would need licences/export templates, so they were rejected.
* `:core` – 100 % game logic in pure Kotlin/JVM (physics, player, camera, levels, save, audio/haptic
  logic, UI models, touch mapping, render-scene generation). It runs and is unit-tested anywhere.
* `:android` – the app: `GLSurfaceView` (OpenGL ES 3.0) renderer, programmatic Views for UI, `AudioTrack`,
  `Vibrator`. **No AndroidX / Compose / Material dependency** → smallest APK, nothing to resolve from
  Google Maven except the Android Gradle Plugin itself.
* Priorities used (from the task): Android performance → stable physics → easy level editing → fast
  iteration → decent 3D → small APK → touch → Play build. A custom fixed-step physics core scores best on
  all of them; a general physics engine (Box2D/Bullet) would cost APK size, determinism and tunability.

## D-003 – Gameplay is simulated in 2D, rendered in 3D
Gameplay plane = X (horizontal) / Y (up). Levels are extruded in Z for a third-person 3D look
(perspective camera, lit geometry, parallax scenery). Reason: vertical climbing with a pogo stick is a
planar problem; 2D physics is cheap, deterministic and easy to tune. Whether the original is truly planar
is unknown (D) and irrelevant for an independent game.

## D-004 – The game is a new product with its own name and content
Working title **Pogo Ascent**, package `com.pogoascent`. No Pogostuck binaries, code, models, textures,
audio or map layouts are used or referenced at runtime. Levels are original; audio is synthesised at
runtime from event definitions (no sample files are shipped).

## D-005 – Location and coexistence with the existing clipboard app
The game lives in its own standalone Gradle build, `pogo/`, so the existing `app/` module is untouched and
still builds exactly as before. Docs (`GAME_IMPLEMENTATION_PLAN.md`, `PHYSICS_MASTER.md`,
`FINAL_BUILD_REPORT.md`, this file) are in `pogo/`.

## D-006 – Android SDK is unreachable in this environment
`dl.google.com` and Google Maven (`maven.google.com`) are blocked by the session egress policy (HTTP 403 on
CONNECT), so the Android SDK, the Android Gradle Plugin and AndroidX cannot be downloaded here – the
pre-existing `app/` cannot be built here either. Consequently **an APK cannot be produced in this
session**. What is done instead (and reported honestly in `FINAL_BUILD_REPORT.md`):
* the Android layer is compiled against the real Android 14 framework classes
  (`org.robolectric:android-all`, from Maven Central) in `:android-check`, and exercised with Robolectric;
* `:android` (the APK module) and a GitHub Actions workflow are provided so the APK can be built on any
  machine/CI that has the SDK. They are **not verified** here.

## D-007 – Unit system and scale
SI units: metres, seconds, radians (degrees only in config/UI). Rider+stick ≈ 1.8 m tall. Mass is 1 (all
"forces" are expressed as velocity change). All of this is a design choice, not a measurement (D).

## D-008 – Physics Hz
Fixed step 120 Hz with adaptive sub-stepping for continuous collision (max 0.08 m of travel per sub-step).
Rendering interpolates between the last two physics states, so game speed never depends on FPS.

## D-009 – Fall is not death
No hit-points. Falling just loses height. "Hazard" surfaces and the world's kill-floor send the player back
to the last checkpoint (or start). This matches the task's "Fall"/"Reset" wording and keeps the climb
loop the core of the game.

## D-010 – Leaderboard is local only
A global leaderboard needs a backend that does not exist. The Leaderboard screen shows the player's own
top-10 times per level (stored in the save file). Online ranking is listed as *not implemented*.

## D-011 – Levels delivered
The task says to build the Prototype first, then Levels 02–03, then the Worlds system, and not to build
all 19. Delivered: Prototype Level 01, Levels 02 and 03, and a Worlds system with four themed worlds;
Worlds 2–4 contain a starter level each, authored with the same data format and checked by the automatic
reachability validator. The remaining levels are future content (no 19-level dataset was available).
