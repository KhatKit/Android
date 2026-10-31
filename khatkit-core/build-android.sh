#!/usr/bin/env bash
set -euo pipefail
khatkit_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
khatkit_ndk="${ANDROID_NDK_HOME:-${ANDROID_HOME:?Set ANDROID_HOME or ANDROID_NDK_HOME}/ndk/28.2.13676358}"
khatkit_llvm="$khatkit_ndk/toolchains/llvm/prebuilt/linux-x86_64"
khatkit_resources="$("$khatkit_llvm/bin/clang" --print-resource-dir)"
export PATH="$khatkit_llvm/bin:$PATH"
export AR="$khatkit_llvm/bin/llvm-ar"
# Bindgen's host libclang also needs NDK clang's builtin headers (stdbool.h, etc.).
export BINDGEN_EXTRA_CLANG_ARGS="--sysroot=$khatkit_llvm/sysroot -isystem $khatkit_resources/include"
for khatkit_abi in arm64-v8a x86_64; do
  case "$khatkit_abi" in
    arm64-v8a) khatkit_target=aarch64-linux-android; khatkit_cc=aarch64-linux-android26-clang ;;
    x86_64) khatkit_target=x86_64-linux-android; khatkit_cc=x86_64-linux-android26-clang ;;
  esac
  export CC="$khatkit_llvm/bin/$khatkit_cc"
  export CXX="${CC}++"
  khatkit_linker_key="CARGO_TARGET_$(tr '[:lower:]-' '[:upper:]_' <<< "$khatkit_target")_LINKER"
  env "$khatkit_linker_key=$CC" cargo build --manifest-path "$khatkit_root/khatkit-core/Cargo.toml" \
    --release --target "$khatkit_target" "$@"
  mkdir -p "$khatkit_root/khatkit/src/main/jniLibs/$khatkit_abi"
  cp "$khatkit_root/khatkit-core/target/$khatkit_target/release/libkhatkit_core.so" \
    "$khatkit_root/khatkit/src/main/jniLibs/$khatkit_abi/libkhatkit_core.so"
done
