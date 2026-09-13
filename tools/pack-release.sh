#!/bin/bash
# BiliForge 版本化打包（不覆盖旧版本，便于回档）
# 用法: bash tools/pack-release.sh [tag]
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
TAG="${1:-$(grep -oP 'versionName = "[^"]*' app/build.gradle.kts | sed 's/versionName = "//')}"
if [ -z "$TAG" ]; then TAG="official"; fi
TS=$(date +%Y%m%d-%H%M)
OUT="$ROOT/releases"
mkdir -p "$OUT"

SRC_APK=app/build/outputs/apk/release/app-release.apk
[ -f "$SRC_APK" ] || SRC_APK=app/build/outputs/apk/debug/app-debug.apk
VARIANT=$(basename "$SRC_APK" | sed 's/app-//;s/\.apk//')
APK_NAME="BiliForge-${TAG}-${VARIANT}-${TS}.apk"
cp "$SRC_APK" "$OUT/$APK_NAME"
cp "$SRC_APK" "$OUT/BiliForge-latest.apk"

SRC_NAME="BiliForge-src-${TAG}-${TS}.tar.gz"
tar czf "$OUT/$SRC_NAME" \
  --exclude=./build --exclude=./app/build --exclude=./.gradle \
  --exclude=./.idea --exclude=./releases -C "$ROOT" .
echo "已打包 [$VARIANT]:"
ls -la "$OUT/$APK_NAME" "$OUT/$SRC_NAME"
