#!/usr/bin/env bash
# 打包 OCR 原生插件（market kind=dependency, plugin_id=ocr）
#
# 产物：plugins/ocr-mlkit/build/ocr-plugin.zip
# 结构：manifest.json + classes.dex + lib/<abi>/libmlkit_google_ocr_pipeline.so + assets/mlkit-google-ocr-models/**
# 限制：压缩 ≤8MiB、解压 ≤32MiB、条目 ≤1024（与 OcrPluginInstaller / extension-market 一致）
#
# 用法：plugins/ocr-mlkit/buildPlugin.sh [--skip-gradle]
#   --skip-gradle  复用已有 build/outputs/aar/ocr-mlkit-release.aar（CI 已构建过时）
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$MODULE_DIR/../.." && pwd)"
cd "$ROOT"

SKIP_GRADLE=0
[[ "${1:-}" == "--skip-gradle" ]] && SKIP_GRADLE=1

if [[ $SKIP_GRADLE -eq 0 ]]; then
  ./gradlew :plugins:ocr-mlkit:assembleRelease
fi

AAR="$MODULE_DIR/build/outputs/aar/ocr-mlkit-release.aar"
[[ -f "$AAR" ]] || { echo "AAR not found: $AAR" >&2; exit 1; }

ANDROID_JAR="${ANDROID_HOME:-$HOME/Android/Sdk}/platforms/android-36/android.jar"
D8="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/36.0.0/d8"
[[ -f "$ANDROID_JAR" ]] || { echo "android.jar not found" >&2; exit 1; }
[[ -x "$D8" ]] || { echo "d8 not found" >&2; exit 1; }

CACHE="$HOME/.gradle/caches/modules-2/files-2.1"
find_aar() { find "$CACHE" -path "*/$1/*/*/*/*.aar" | head -1; }

CHINESE_AAR="$(find_aar 'com.google.mlkit/text-recognition-chinese')"
BUNDLED_AAR="$(find_aar 'com.google.mlkit/text-recognition-bundled-common')"
[[ -n "$CHINESE_AAR" && -n "$BUNDLED_AAR" ]] || { echo "ML Kit AARs missing from Gradle cache; run assembleRelease first" >&2; exit 1; }

BUILD="$MODULE_DIR/build/plugin"
rm -rf "$BUILD"; mkdir -p "$BUILD/aar" "$BUILD/dex" "$BUILD/pkg/lib/arm64-v8a"

# 1) 插件自身 classes → dex；ML Kit 依赖类一并打进 dex（基础包不再带 ML Kit）
unzip -q -o "$AAR" classes.jar -d "$BUILD/aar"
mapfile -t DEP_JARS < <(find "$CACHE" \( \
  -path '*/com.google.mlkit/text-recognition-chinese/*/*/*/*.aar' -o \
  -path '*/com.google.mlkit/text-recognition-bundled-common/*/*/*/*.aar' -o \
  -path '*/com.google.mlkit/common/*/*/*/*.aar' -o \
  -path '*/com.google.mlkit/vision-common/*/*/*/*.aar' -o \
  -path '*/com.google.mlkit/vision-interfaces/*/*/*/*.aar' -o \
  -path '*/com.google.android.gms/play-services-mlkit-text-recognition*/*/*/*.aar' -o \
  -path '*/com.google.android.gms/play-services-tasks/*/*/*/*.aar' -o \
  -path '*/com.google.android.gms/play-services-base/*/*/*/*.aar' -o \
  -path '*/com.google.android.gms/play-services-basement/*/*/*/*.aar' \) -print | head -30)
DEP_CLASSES=()
i=0
for aar in "${DEP_JARS[@]}"; do
  i=$((i+1))
  unzip -q -o "$aar" classes.jar -d "$BUILD/aar/dep$i" 2>/dev/null || continue
  [[ -s "$BUILD/aar/dep$i/classes.jar" ]] && DEP_CLASSES+=("$BUILD/aar/dep$i/classes.jar")
done
"$D8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD/dex" "$BUILD/aar/classes.jar" "${DEP_CLASSES[@]}"

# 2) 原生库（arm64-v8a；x86_64 会超 8MiB 压缩上限，模拟器请自行按 ABI 打）
unzip -p "$BUNDLED_AAR" jni/arm64-v8a/libmlkit_google_ocr_pipeline.so > "$BUILD/pkg/lib/arm64-v8a/libmlkit_google_ocr_pipeline.so"

# 3) ML Kit 模型 assets
unzip -q "$CHINESE_AAR" 'assets/*' -d "$BUILD/assets"
mkdir -p "$BUILD/pkg/assets"
cp -r "$BUILD/assets/assets/." "$BUILD/pkg/assets/"

# 4) manifest（必须与市场提交的 manifest_json 完全一致）
cat > "$BUILD/pkg/manifest.json" <<'JSON'
{
  "kind": "dependency",
  "plugin_id": "ocr",
  "version": "1.0.0",
  "entry_class": "heizige.kk.khatkit.ocr.mlkit.MlKitOcrEngine",
  "abi": ["arm64-v8a"]
}
JSON
cp "$BUILD/dex/classes.dex" "$BUILD/pkg/classes.dex"

# 5) zip + 限制校验
OUT="$MODULE_DIR/build/ocr-plugin.zip"
rm -f "$OUT"
(cd "$BUILD/pkg" && zip -qr9 "$OUT" .)
SIZE=$(stat -c%s "$OUT")
UNZ=$(unzip -l "$OUT" | awk '/files$/ {print $(NF-1)}' | tail -1)
[[ "$SIZE" -le $((8*1024*1024)) ]] || { echo "zip too large: $SIZE > 8MiB" >&2; exit 1; }
echo "ok: $OUT ($SIZE bytes compressed, $UNZ bytes uncompressed)"
