# Android build — validation record

This documents the toolchain, exact commands, and verification results for the
debug and release APK builds. Written from a real build performed in this
environment, not from expected/typical output.

## Toolchain used

| Component | Source | Version |
|---|---|---|
| Godot editor (headless export) | GitHub release asset | `4.3-stable` (`Godot_v4.3-stable_linux.x86_64`) |
| Godot export templates | GitHub release asset (`Godot_v4.3-stable_export_templates.tpz`) | `4.3.stable`, installed to `~/.local/share/godot/export_templates/4.3.stable/` |
| Android build-tools (`aapt`, `aapt2`, `zipalign`, `apksigner`) | Ubuntu 24.04 `noble` apt repository (`android-sdk-build-tools`) | 29.0.3 |
| Android platform-tools (`adb`) | Ubuntu apt (`adb`, `android-sdk-platform-tools`) | 34.0.4 / 28.0.2 |
| Java | Pre-installed OpenJDK | 21.0.10 |

Google's own distribution points (`dl.google.com`, `sourceforge.net`) are
blocked by this session's egress policy (403 at the proxy). `maven.google.com`
and `storage.googleapis.com` were reachable, but Android's SDK Manager
packages (build-tools, platforms) are not distributed as plain files at
predictable paths on either, so the direct download did not work. **Ubuntu's
own `android-sdk-build-tools` package supplied everything the non-Gradle
export path needs** (`aapt`, `aapt2`, `zipalign`, `apksigner`), so no SDK
Manager / `dl.google.com` access was actually required. This was checked and
used before considering the build blocked, per the request not to stop at the
first missing-template state.

Editor configuration (`~/.config/godot/editor_settings-4.3.tres`):
```
export/android/android_sdk_path = "/usr/lib/android-sdk"
export/android/java_sdk_path = "/usr/lib/jvm/java-21-openjdk-amd64"
export/android/debug_keystore = "/root/.local/share/godot/keystores/debug.keystore"
```

## What was NOT available, and why it didn't block the build

- **No Android emulator, no physical device.** The container has no
  `/dev/kvm` and no `vmx`/`svm` CPU flags, so the Android emulator (which
  needs hardware-accelerated virtualization to run at a usable speed, and
  whose system images are themselves only distributed via the blocked
  `dl.google.com`) cannot run here. This is an environment limit, not a
  project issue — install/run verification on real Android needs to happen on
  a machine with a device or a KVM-capable host.
- **Gradle-based build was not used.** The export preset uses
  `gradle_build/use_gradle_build=false`, Godot's precompiled-template export
  path. This needs only `zipalign`/`apksigner`/`aapt`, all supplied by apt. A
  Gradle build would additionally need the Android Gradle Plugin and an
  `android.jar` for `compileSdk`, both normally fetched from
  `maven.google.com` / `dl.google.com` — not attempted, since the simpler path
  already produces a correctly signed, installable APK.

## Bug found and fixed: invalid export preset

The committed `export_presets.cfg` set `gradle_build/min_sdk="24"` and
`gradle_build/target_sdk="34"` while `gradle_build/use_gradle_build=false`.
Godot 4.3 rejects this combination outright:

```
ERROR: Cannot export project with preset "Android" due to configuration errors:
"Min SDK" can only be overridden when "Use Gradle Build" is enabled.
"Target SDK" can only be overridden when "Use Gradle Build" is enabled.
```

These two fields only apply to Gradle builds; on the template-based path they
were dead configuration that an older Godot silently ignored, and 4.3 now
validates and refuses to export. Fixed by clearing both to `""`, which is the
only change made to `export_presets.cfg` beyond adding (and, before commit,
blanking again — see below) the release keystore fields. This is a
configuration correction for the export path the preset already specifies,
not an architecture or build-path change.

One consequence worth stating plainly: with the Gradle overrides inactive, the
resulting **minSdkVersion is 21** (baked into Godot's precompiled template),
not the 24 the preset used to (invalidly) request. targetSdkVersion is
correctly **34**, matching Godot's template. Android 21 is Lollipop
(2014) — a strictly wider floor than 24, so this does not exclude any device
the game was designed for; it only means the manifest's stated minimum is
lower than originally written. Only a Gradle build can raise minSdk above what
the template bakes in, and that path was not attempted (see above).

## Commands used

```bash
# One-time toolchain setup (already covered above)
apt-get install -y --no-install-recommends \
    zipalign apksigner android-sdk-build-tools android-sdk-platform-tools adb

# Debug keystore already existed from project setup; if it did not:
keytool -genkeypair -v -keystore ~/.local/share/godot/keystores/debug.keystore \
    -storepass android -alias androiddebugkey -keypass android \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Android Debug,O=Android,C=US"

# Release keystore (generated locally; NOT committed — see "Signing keys" below)
keytool -genkeypair -v -keystore ~/.local/share/godot/keystores/release.keystore \
    -storepass <password> -alias dtym_release -keypass <password> \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=Don't Trust Your Mind,OU=Release,O=DTYM,C=US"

cd dont-trust-your-mind

# Debug build
godot --headless --export-debug "Android" build/dtym-debug.apk

# Release build (needs keystore/release, keystore/release_user,
# keystore/release_password filled in export_presets.cfg — see below)
godot --headless --export-release "Android" build/dtym-release.apk
```

## Signing keys

`export_presets.cfg` ships with `keystore/release`, `keystore/release_user`
and `keystore/release_password` **blank on purpose** — a real signing
password does not belong in version control. To reproduce the release build,
either:

- fill those three fields locally before running `--export-release`, and
  revert the file afterward (what was done here), or
- open the project in the Godot editor once and enter the release keystore
  under Editor → Export → Android → Keystores, which Godot stores in editor
  settings, never in the project.

The release keystore used for this validation pass is a fresh, throwaway
self-signed key generated in this session (`CN=Don't Trust Your Mind,
OU=Release, O=DTYM, C=US`), stored outside the repository at
`~/.local/share/godot/keystores/release.keystore`, and not committed anywhere.
**It is not a production signing key.** Before a real Play Store submission,
generate a new release keystore, store it somewhere durable and secret (a
secrets manager or an offline backup — losing it means losing the ability to
publish updates to the same app listing), and never commit it.

## Verification performed

Both APKs were checked directly, not assumed correct because the export
command exited 0.

### Signing and integrity

```
$ apksigner verify --verbose --print-certs build/dtym-debug.apk
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true

$ apksigner verify --verbose --print-certs build/dtym-release.apk
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Signer #1 certificate DN: CN=Don't Trust Your Mind, OU=Release, O=DTYM, C=US

$ zipalign -c -v 4 build/dtym-debug.apk    # Verification successful
$ zipalign -c -v 4 build/dtym-release.apk  # Verification successful

$ unzip -t build/dtym-debug.apk    # No errors detected
$ unzip -t build/dtym-release.apk  # No errors detected
```

### Manifest (`aapt dump badging`)

| | Debug | Release |
|---|---|---|
| package | `com.dtym.dontrustyourmind` | same |
| versionCode / versionName | `1` / `1.0.0` | same |
| minSdkVersion | 21 | 21 |
| targetSdkVersion | 34 | 34 |
| `android:debuggable` | present (`true`) | **absent** (correct — release must not be debuggable) |
| permissions | `android.permission.VIBRATE` only | same |
| screenOrientation | `portrait` (`0x1`) | same |
| supports-screens | small, normal, large, xlarge | same |

No `INTERNET` permission in either build, confirmed by inspection —
consistent with the game's offline-first design (nothing in
`export_presets.cfg` requests it, and nothing in the manifest grants it).

### Asset completeness inside the APK

Every content file the game loads at runtime was confirmed present by listing
the archive, and the files excluded by the export filter were confirmed
absent:

- `content/puzzles/chapter_01.json` … `chapter_05.json`, `daily.json` — present
- `content/locale/ui_strings.json` — present
- The three Cairo font weights — present as Godot's compiled `.fontdata`
  import cache (the format the engine actually loads; Godot converts fonts at
  export time, so the *original* `.ttf` bytes are correctly not duplicated
  in the package)
- `res/mipmap-{h,m,x,xx,xxx}dpi/icon.png` + adaptive icon XML — present, all
  five density buckets
- `lib/arm64-v8a/libgodot_android.so`, `lib/armeabi-v7a/libgodot_android.so`
  (+ matching `libc++_shared.so`) — present; `x86`/`x86_64` correctly absent,
  matching the two architectures enabled in the preset
- `assets/tests/*`, `assets/docs/*` — **absent**, confirming the
  `exclude_filter` in `export_presets.cfg` is doing its job in the actual
  shipped artifact, not just in theory

### Functional regression check

Fixing the export preset touches build configuration, not game code, but the
full verification suite was re-run after the fix to be sure:

```
PASS  4417 checks, 0 failed   (content, session rules, localization/RTL,
                                save system, rendering, playthrough, responsive)
PASS  smoke test, 21/21 assertions   (cold start → language → every screen →
                                       solve stage 1 → save → reload)
```

Both runs are identical to the pre-fix results — the preset fix changed
nothing about how the game plays.

## Final artifacts

```
build/dtym-debug.apk      47 MB   signed, zipaligned, installable, debuggable
build/dtym-debug.apk.idsig
build/dtym-release.apk    44 MB   signed, zipaligned, installable, NOT debuggable
build/dtym-release.apk.idsig
```

Neither file nor `build/` is committed (`.gitignore` already excluded
`build/` and `*.apk` from the previous session). They are build outputs,
reproducible with the commands above.

## What still needs a real device

Everything checkable without hardware has been checked. What remains is
inherently device-only:

- Actual install (`adb install`) and launch on a physical phone or a
  KVM-capable emulator host.
- On-device touch latency and gesture-navigation-bar overlap.
- Arabic text rendering at the device's largest accessibility font scale.
- Battery/thermal behavior over a longer play session.
- OEM-specific back-gesture and notch/cutout behavior beyond what
  `DisplayServer.get_display_safe_area()` reports in this environment.
