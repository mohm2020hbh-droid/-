# Pogo Ascent

A mobile 3D pogo-stick climbing game (Android, Kotlin, OpenGL ES 3.0). Original game, **inspired by** the pogo-climbing genre — no
assets, code or data from any other game are used (see `DECISIONS.md` D-001/D-004).

> **Read first:** the Pogostuck analysis files the task refers to are **not present in this repository**, so every physics number is a
> *grade D* design default that lives in one editable file. See `PHYSICS_MASTER.md` and `FINAL_BUILD_REPORT.md` for exactly what is
> verified and what is not.

## Documents

| File | What it is |
|---|---|
| `GAME_IMPLEMENTATION_PLAN.md` | The 15-area plan and the phase list |
| `PHYSICS_MASTER.md` | Every physics value with unit / source / confidence / editable / notes (**generated** from code) |
| `DECISIONS.md` | Every decision the missing data forced, and why |
| `FINAL_BUILD_REPORT.md` | Honest status: implemented / partial / estimated / unknown / limitations / build status |

## Layout

```
pogo/
  core/          pure Kotlin/JVM: ALL game logic (physics, player, camera, levels, save, audio/haptic logic, UI models, touch, render data)
    src/main/kotlin/com/pogoascent/{core,physics,player,camera,levels,ui,audio,haptics,save,settings,customization,
                                   touch,render,particles,feedback,debug,app,tools}
    src/main/resources/data/   physics_config.json  camera_config.json  surfaces.json  worlds.json  items.json
                               audio_events.json  haptics.json  particles.json  levels/level_0N.json  demo_level_01.json
  android/       the app: GLSurfaceView renderer, programmatic Views, SoundPool/AudioTrack, Vibrator (platform APIs only – no AndroidX)
  android-check/ compiles the android/ sources against the real Android 14 framework jar and tests them with Robolectric (no SDK needed)
  devtools/      JVM tools: level validator, level generator, software debug renderer, scenario runner, probe-jumps
```

The task's `/Scripts /Data /Scenes …` layout maps onto this (table in `GAME_IMPLEMENTATION_PLAN.md`).

## Build & test

```bash
cd pogo
./gradlew :core:test                      # game logic, physics, level reachability, save, audio synth, touch, UI models (JDK 17+)
./gradlew :android-check:test             # Android layer under Robolectric (downloads android-all from Maven Central)
./gradlew :devtools:validateLevels        # structural + reachability-by-simulation check of every level
./gradlew :devtools:renderLevels          # PNG frames in devtools/build/debug-frames/
./gradlew :devtools:runScenarios          # Test Jump / Boost / Bounce / Fall measurements
./gradlew :android:assembleDebug          # APK – needs the Android SDK (ANDROID_HOME or local.properties sdk.dir)
```

`.github/workflows/pogo-android.yml` runs all of the above in CI and uploads the APK.

## Tuning without touching code

* **Physics:** edit `core/src/main/resources/data/physics_config.json` (any subset of keys). Run `./gradlew :core:generateDocs`
  to refresh `PHYSICS_MASTER.md`, then `:core:test` and `:devtools:validateLevels` (levels re-prove themselves with the new physics).
* **Camera:** `camera_config.json`. **Surfaces** (friction/bounce/hazard/launch pad): `surfaces.json`.
* **Levels:** hand-edit `levels/*.json` (platforms, obstacles, hazards, movers, bounce objects, checkpoints, decor) or regenerate with
  `./gradlew :devtools:generateLevels -Plevels=level_02` after changing a profile in `devtools/.../GenerateLevels.kt`.
* **Worlds / palettes / music style:** `worlds.json`. **Cosmetics:** `items.json`. **Sounds:** `audio_events.json` (recipes are
  synthesised at runtime; there are no audio files). **Haptics / particles:** `haptics.json`, `particles.json`.

## Controls (touch)

Left thumb: drag sideways to **lean** (floating slider; lift to return upright). Right thumb: hold **JUMP** to charge the spring, release
to launch. In the air, drag to **spin**. Land holding JUMP to store the landing speed in the spring (bounce chain). Spin more than the
boost threshold in one flight, land while holding JUMP, then release → **Boost jump**. Layouts: slider+button, swipe, full-screen gesture;
sensitivity, deadzone, button size/opacity, left-handed mode and haptics are in Settings → Controls.
