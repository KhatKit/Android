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

# ---- R8 映射校验（有 mapping.txt 时）----
# 卡片脚本按方法名反射调用 bridge，实际注入的是 Scoped*Bridge 包装器；
# 这些类和方法在 release 里必须保持原名，否则卡片全挂（RustBridgeDispatcher 同理）。
MAPPING="app/build/outputs/mapping/release/mapping.txt"
if [ -f "$MAPPING" ]; then
  echo "checking $MAPPING"
  python3 - "$MAPPING" <<'PY_CHECK'
import re
import sys

mapping = sys.argv[1]
targets = re.compile(r'heizige\.kk\.khatkit\.(bridge\.Scoped[A-Za-z]*Bridge|engine\.RustBridgeDispatcher)$')
class_re = re.compile(r'^(\S+) -> (\S+):$')
bad, checked, members = [], set(), 0
cur = None
for line in open(mapping, encoding='utf-8', errors='ignore'):
    line = line.rstrip('\n')
    if line and not line.startswith(' '):
        m = class_re.match(line)
        if m and targets.match(m.group(1)):
            cur = m.group(1)
            checked.add(cur)
            name = m.group(2).rstrip(':')
            if name != cur:
                bad.append(f'R8 改名了反射目标类：{cur} -> {name}')
        else:
            cur = None
        continue
    if cur and ' -> ' in line:
        left, right = line.split(' -> ', 1)
        left = left.strip()
        if '(' not in left:      # 字段，允许改名（反射只按方法名找）
            continue
        method = left.split('(')[0].split()[-1]
        if method in ('<init>', '<clinit>') or '$' in method:  # 构造器/lambda 不参与反射
            continue
        members += 1
        if right.strip() != method:
            bad.append(f'R8 改名了反射方法：{cur}.{method} -> {right.strip()}')

for b in bad:
    print(f'::error::{b}')
if not checked:
    print('::error::mapping.txt 里找不到反射目标类（R8 配置可能被改坏）')
    sys.exit(1)
if members < 10:
    print(f'::error::mapping.txt 里解析到的反射方法太少（{members}），检查 keep 规则')
    sys.exit(1)
if bad:
    sys.exit(1)
print(f'R8 mapping OK：{len(checked)} 个反射目标类、{members} 个方法均未被改名')
PY_CHECK
  [ "$missing" -eq 0 ] || exit 1
else
  echo "::warning::未找到 $MAPPING，跳过 R8 映射校验（仍已校验 dex 符号）"
fi
echo "card runtime reflection symbols OK"
