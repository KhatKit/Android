#!/usr/bin/env bash
# 打包远程 SSH 原生插件（market kind=dependency, plugin_id=ssh）
#
# 产物：plugins/ssh/build/ssh-plugin.zip
# 结构：manifest.json + classes.dex（插件自身 + sshj + asn-one + BouncyCastle）
# 限制：压缩 ≤8MiB、解压 ≤32MiB（与 SshPluginInstaller / extension-market 一致）
#
# 基础 APK 不再依赖 sshj/BouncyCastle：它们全部打进这个 dex，只有装了插件才会被加载；
# slf4j-api 与 Kotlin 标准库由宿主 classloader 提供，不打进 dex。
#
# 用法：plugins/ssh/buildPlugin.sh [--skip-gradle]
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$MODULE_DIR/../.." && pwd)"
cd "$ROOT"

SKIP_GRADLE=0
[[ "${1:-}" == "--skip-gradle" ]] && SKIP_GRADLE=1

if [[ $SKIP_GRADLE -eq 0 ]]; then
  ./gradlew :plugins:ssh:assembleRelease
fi

AAR="$MODULE_DIR/build/outputs/aar/ssh-release.aar"
[[ -f "$AAR" ]] || { echo "AAR not found: $AAR" >&2; exit 1; }

# 依赖版本以 Gradle 解析结果为准（缓存里可能并存多个 BouncyCastle 版本）。
CLASSPATH_LIST="$(./gradlew -q :plugins:ssh:printReleaseRuntimeClasspath --console=plain | tr -d '\r')"
pick() {
  printf '%s\n' "$CLASSPATH_LIST" | grep -E "$1" | head -1 || true
}
SSHJ="$(pick '/sshj/')"
ASN_ONE="$(pick '/asn-one/')"
BCPROV="$(pick '/bcprov-jdk18on/')"
BCPKIX="$(pick '/bcpkix-jdk18on/')"
BCUTIL="$(pick '/bcutil-jdk18on/')"
for jar in "$SSHJ" "$ASN_ONE" "$BCPROV" "$BCPKIX" "$BCUTIL"; do
  [[ -f "$jar" ]] || { echo "missing dependency jar: ${jar:-<empty>}" >&2; exit 1; }
done

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
ANDROID_JAR="$(ls -1 "$SDK_ROOT"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
BUILD_TOOLS="$(ls -1d "$SDK_ROOT"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
D8="${BUILD_TOOLS}d8"
[[ -f "${ANDROID_JAR:-}" ]] || { echo "android.jar not found under $SDK_ROOT" >&2; exit 1; }
[[ -x "$D8" ]] || { echo "d8 not found: $D8" >&2; exit 1; }

BUILD="$MODULE_DIR/build/plugin"
rm -rf "$BUILD"; mkdir -p "$BUILD/aar" "$BUILD/dex" "$BUILD/pkg"

unzip -q -o "$AAR" classes.jar -d "$BUILD/aar"

# D8：插件类 + sshj + asn-one + BouncyCastle → 单个 classes.dex
"$D8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD/dex" \
  "$BUILD/aar/classes.jar" "$SSHJ" "$ASN_ONE" "$BCPROV" "$BCPKIX" "$BCUTIL"

# manifest（必须与市场提交的 manifest_json 完全一致）
cat > "$BUILD/pkg/manifest.json" <<'JSON'
{
  "kind": "dependency",
  "plugin_id": "ssh",
  "version": "1.0.1",
  "entry_class": "heizige.kk.khatkit.plugins.ssh.SshEngineImpl",
  "abi": []
}
JSON
cp "$BUILD/dex/classes.dex" "$BUILD/pkg/classes.dex"

OUT="$MODULE_DIR/build/ssh-plugin.zip"
rm -f "$OUT"
(cd "$BUILD/pkg" && zip -qr9 "$OUT" .)
SIZE=$(stat -c%s "$OUT")
UNZ=$(unzip -l "$OUT" | awk '/files$/ {print $(NF-1)}' | tail -1)
[[ "$SIZE" -le $((8*1024*1024)) ]] || { echo "zip too large: $SIZE > 8MiB" >&2; exit 1; }
[[ "$UNZ" -le $((32*1024*1024)) ]] || { echo "unzipped too large: $UNZ > 32MiB" >&2; exit 1; }
echo "ok: $OUT ($SIZE bytes compressed, $UNZ bytes uncompressed)"
