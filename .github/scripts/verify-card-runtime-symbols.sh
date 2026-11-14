#!/usr/bin/env bash
# 卡片运行时回归防线。
#
# 卡片引擎在 khatkit-core(Rust) 里，通过 JNI 按「方法名 + 签名」回调 Kotlin 的
# RustBridgeDispatcher（dispatch / interruptReason / wasmAllowed，见 khatkit-core/src/host.rs）。
# R8 只看得见 Kotlin 调用点，会把「只被 native 调用」的这几个方法裁掉或改名，
# 后果是正式包（release）里所有 Lua/JS/WASM 卡片都跑不起来，而 debug 包完全正常。
#
# 本脚本在 release APK 的 dex 里校验这些符号仍在；配套 keep 规则见 khatkit/consumer-rules.pro。
set -euo pipefail

APK="${1:-}"
if [ -z "$APK" ]; then
  APK="$(ls app/build/outputs/apk/release/*arm64-v8a-release.apk 2>/dev/null | head -1 || true)"
  [ -n "$APK" ] || APK="$(ls app/build/outputs/apk/release/*-release.apk 2>/dev/null | head -1 || true)"
fi
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "::error::release APK 未找到，无法校验卡片运行时符号"
  ls -R app/build/outputs/apk 2>/dev/null || true
  exit 1
fi

echo "checking $APK"
missing=0
for sym in RustBridgeDispatcher wasmAllowed interruptReason; do
  if ! unzip -p "$APK" 'classes*.dex' | grep -a -q "$sym"; then
    echo "::error::release dex 缺少 JNI 回调符号 $sym（R8 裁掉/改名了 RustBridgeDispatcher？检查 khatkit/consumer-rules.pro）"
    missing=1
  fi
done
[ "$missing" -eq 0 ] || exit 1
echo "card runtime JNI symbols OK"
