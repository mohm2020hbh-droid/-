#!/usr/bin/env bash
# Builds a debug-signed APK WITHOUT Gradle/AGP/Android SDK, using Ubuntu's packaged tools:
#   apt install aapt apksigner zipalign dalvik-exchange libandroid-23-java default-jdk-headless
# Use this where dl.google.com (Google Maven + SDK downloads) is blocked. For the Play Store AAB use Gradle
# (android/ project, `./gradlew bundleRelease`) on a machine with the Android SDK.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/android/app/src/main"
OUT="$ROOT/android/build-legacy"
PKG="app.pogosummit.game"
VERSION_CODE="${VERSION_CODE:-1}"
VERSION_NAME="${VERSION_NAME:-0.1.0}"
MIN_SDK=24
TARGET_SDK="${TARGET_SDK:-34}"
AJ="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"

for t in aapt apksigner zipalign dalvik-exchange javac keytool; do command -v "$t" >/dev/null || { echo "missing tool: $t"; exit 1; }; done
[ -f "$AJ" ] || { echo "android.jar not found at $AJ"; exit 1; }

echo "==> building web bundle"
( cd "$ROOT/game" && npm run build -- --android >/dev/null )

rm -rf "$OUT"; mkdir -p "$OUT/classes"
# Legacy aapt needs package/uses-sdk inside the manifest (AGP injects them from build.gradle instead).
sed -e "s|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">|<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$PKG\" android:versionCode=\"$VERSION_CODE\" android:versionName=\"$VERSION_NAME\">\n    <uses-sdk android:minSdkVersion=\"$MIN_SDK\" android:targetSdkVersion=\"$TARGET_SDK\" />|" \
    "$APP/AndroidManifest.xml" | sed -e '/android:roundIcon/d' > "$OUT/AndroidManifest.xml"   # roundIcon is API 25; android-23.jar cannot resolve it

echo "==> compiling Java (release 8)"
javac --release 8 -Xlint:-options -cp "$AJ" -d "$OUT/classes" $(find "$APP/java" -name '*.java') 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
echo "==> dexing"
dalvik-exchange --dex --output="$OUT/classes.dex" "$OUT/classes" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
echo "==> packaging resources + assets"
aapt package -f -M "$OUT/AndroidManifest.xml" -S "$APP/res" -A "$APP/assets" -I "$AJ" -F "$OUT/base.apk"
( cd "$OUT" && aapt add base.apk classes.dex >/dev/null )
zipalign -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

KS="${KEYSTORE:-$ROOT/android/debug.keystore}"
if [ ! -f "$KS" ]; then
  echo "==> creating DEBUG keystore (not for release!)"
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android -alias pogodebug -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Pogo Summit Debug,O=Debug" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
fi
APK="$OUT/PogoSummit-$VERSION_NAME-debug.apk"
apksigner sign --ks "$KS" --ks-pass pass:android --key-pass pass:android --out "$APK" "$OUT/aligned.apk" 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
echo "==> verifying"
apksigner verify -v "$APK" 2>&1 | grep -v JAVA_TOOL_OPTIONS | head -6
aapt dump badging "$APK" | grep -E "^(package|sdkVersion|targetSdkVersion|uses-permission|launchable-activity|application-label)"
ls -la "$APK"
echo "APK: $APK"
