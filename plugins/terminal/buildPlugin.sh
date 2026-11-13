#!/usr/bin/env bash
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$MODULE_DIR/../.." && pwd)"
cd "$ROOT"

[[ "${1:-}" == "--skip-gradle" ]] || ./gradlew :plugins:terminal:assembleRelease
AAR="$MODULE_DIR/build/outputs/aar/terminal-release.aar"
[[ -f "$AAR" ]] || { echo "AAR not found: $AAR" >&2; exit 1; }

CLASSPATH_LIST="$(./gradlew -q :plugins:terminal:printReleaseRuntimeClasspath --console=plain | tr -d '\r')"
TERMUX="$(printf '%s\n' "$CLASSPATH_LIST" | grep '/terminal-view-' | head -1)"
EMULATOR="$(printf '%s\n' "$CLASSPATH_LIST" | grep '/terminal-emulator-' | head -1)"
[[ -f "$TERMUX" && -f "$EMULATOR" ]] || { echo "Termux AARs not found" >&2; exit 1; }

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ANDROID_JAR="$(ls -1 "$SDK_ROOT"/platforms/android-*/android.jar | sort -V | tail -1)"
BUILD_TOOLS="$(ls -1d "$SDK_ROOT"/build-tools/*/ | sort -V | tail -1)"
BUILD="$MODULE_DIR/build/plugin"
rm -rf "$BUILD"; mkdir -p "$BUILD/aar" "$BUILD/dex" "$BUILD/pkg" "$BUILD/termux" "$BUILD/emulator"
unzip -q -o "$AAR" 'classes.jar' 'jni/*' -d "$BUILD/aar"
unzip -q -o "$TERMUX" 'classes.jar' -d "$BUILD/termux"
unzip -q -o "$EMULATOR" 'classes.jar' -d "$BUILD/emulator"
"${BUILD_TOOLS}d8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD/dex" \
  "$BUILD/aar/classes.jar" "$BUILD/termux/classes.jar" "$BUILD/emulator/classes.jar"
cp "$BUILD/dex/classes.dex" "$BUILD/pkg/classes.dex"
mkdir -p "$BUILD/pkg/lib"
cp -R "$BUILD/aar/jni/." "$BUILD/pkg/lib/"
cat > "$BUILD/pkg/manifest.json" <<'JSON'
{
  "kind": "dependency",
  "plugin_id": "terminal",
  "version": "1.0.0",
  "entry_class": "heizige.kk.khatkit.plugins.terminal.TerminalPlugin",
  "abi": ["arm64-v8a", "x86_64"]
}
JSON
OUT="$MODULE_DIR/build/terminal-plugin.zip"
rm -f "$OUT"
(cd "$BUILD/pkg" && zip -qr9 "$OUT" .)
SIZE=$(stat -c%s "$OUT")
[[ "$SIZE" -le $((8*1024*1024)) ]] || { echo "zip too large: $SIZE" >&2; exit 1; }
echo "ok: $OUT ($SIZE bytes)"
