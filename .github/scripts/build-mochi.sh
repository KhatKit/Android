#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."
version="$(sed -nE 's/^mochi[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' gradle/libs.versions.toml)"
revision=70636705c25b170d9424c9ea6669752ab8f6ece1
source="${1:-https://github.com/feice11/mochi-compose.git}"
checkout="$(mktemp -d)"

git -C "$checkout" init -q
git -C "$checkout" fetch --depth 1 "$source" "$revision"
git -C "$checkout" checkout --detach FETCH_HEAD
python3 .github/scripts/patch-mochi.py "$checkout"
"$checkout/gradlew" -p "$checkout" \
    :mochi-compose:publishToMavenLocal :mochi-ios:publishToMavenLocal :mochi-icons:publishToMavenLocal \
    :mochi-nothing:publishToMavenLocal \
    -PVERSION_NAME="$version" --stacktrace
