#!/usr/bin/env bash
# 准备 release 签名文件（CI 用）。
#
# 优先用仓库 secrets：
#   KEY_BASE64     = base64 -w0 khatkit-release.jks
#   SIGNING_CONFIG = storeFile/storePassword/keyAlias/keyPassword 四行
#
# 现实里 secrets 会漂移（换过密钥、粘错密码），这里做三级兜底：
#   1. 用 SIGNING_CONFIG 里的 storePassword 试 keystore；
#   2. 失败则用 docs/release-signing.md 里记录的密码 khatkit 再试；
#   3. 还不行就生成一次性临时密钥，并输出 ::warning::（只为产出可安装包，
#      更新通道必须换回正式密钥，否则会 INSTALL_FAILED_UPDATE_INCOMPATIBLE）。
#
# 输出 local.properties 到仓库根目录（app/build.gradle.kts 读取）。
set -eo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

EXPECTED_FP="32:3C:87:93:FE:29:7F:CB:E9:DC:EA:FB:B3:30:BF:40:DC:03:C7:44:81:A5:E6:67:A2:AD:9D:B2:8F:1B:C4:53"
KEY_B64="${KEY_BASE64:-}"
CFG="${SIGNING_CONFIG:-}"

trim() { printf '%s' "$1" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'; }
cfg_value() { # $1 = key
  printf '%s\n' "$CFG" | tr -d '\r' | sed -nE "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*(.*)$/\1/p" | head -1 | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'
}

normalize_b64() { printf '%s' "$1" | tr -d '[:space:]'; }

keystore_ok() { # $1 = keystore path, $2 = store password
  keytool -list -keystore "$1" -storepass "$2" >/dev/null 2>&1
}

keystore_fp() { # $1 = path, $2 = pass
  keytool -list -v -keystore "$1" -storepass "$2" 2>/dev/null \
    | sed -n 's/^[[:space:]]*SHA256:[[:space:]]*//p' | head -1 | tr -d '\r'
}

if [ -n "$KEY_B64" ]; then
  B64="$(normalize_b64 "$KEY_B64")"
  if printf '%s' "$B64" | base64 -d > app/app.key 2>/dev/null; then
    CFG_STORE_PASS="$(cfg_value storePassword)"
    CFG_ALIAS="$(cfg_value keyAlias)"
    CFG_KEY_PASS="$(cfg_value keyPassword)"
    [ -n "$CFG_ALIAS" ] || CFG_ALIAS="khatkit"

    FOUND_PASS=""
    for cand in "$CFG_STORE_PASS" "khatkit"; do
      [ -n "$cand" ] || continue
      if keystore_ok app/app.key "$cand"; then FOUND_PASS="$cand"; break; fi
    done

    if [ -n "$FOUND_PASS" ]; then
      FP="$(keystore_fp app/app.key "$FOUND_PASS" || true)"
      echo "keystore fingerprint: ${FP:-<unknown>}"
      if [ -n "$FP" ] && [ "$(printf '%s' "$FP" | tr 'a-f' 'A-F' | tr -d ':')" = "$(printf '%s' "$EXPECTED_FP" | tr -d ':')" ]; then
        echo "::notice::release keystore matches docs fingerprint (official key)"
      else
        echo "::warning::release keystore fingerprint != docs (32:3C:87...), check docs/release-signing.md"
      fi
      {
        echo "storeFile=app.key"
        echo "storePassword=$FOUND_PASS"
        echo "keyAlias=$CFG_ALIAS"
        echo "keyPassword=${CFG_KEY_PASS:-$FOUND_PASS}"
      } > local.properties
      echo "release signing: using secrets keystore (storeFile=app.key, alias=$CFG_ALIAS)"
      exit 0
    fi
    echo "::warning::KEY_BASE64 keystore 的密码与 SIGNING_CONFIG/文档都不匹配，退化到临时密钥"
  else
    echo "::warning::KEY_BASE64 不是合法 base64，忽略"
  fi
fi

# 兜底：一次性临时密钥（fork 或 secrets 失效时仍能产出可安装的包）
keytool -genkeypair -v \
  -keystore "$ROOT/app/ci-release.jks" \
  -storepass android -keypass android \
  -alias khatkit-ci -keyalg RSA -keysize 2048 -validity 36500 \
  -dname "CN=KhatKit CI, OU=CI, O=KhatKit, L=CI, S=CI, C=CN" >/dev/null 2>&1
printf 'storeFile=%s/app/ci-release.jks\nstorePassword=android\nkeyAlias=khatkit-ci\nkeyPassword=android\n' "$ROOT" > local.properties
echo "::warning::KEY_BASE64/SIGNING_CONFIG 不可用，已退化到一次性临时密钥；正式发布前请更新 secrets（见 docs/release-signing.md）"
