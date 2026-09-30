#!/usr/bin/env bash
# NbtGen 打包脚本 —— 带版本号，归档到 包发布/
set -e
cd "$(dirname "$0")"

VERSION_FILE="VERSION"
if [ -n "$1" ]; then
  VER="$1"
else
  CUR="0.0.15"
  [ -f "$VERSION_FILE" ] && CUR="$(cat "$VERSION_FILE")"
  MAJOR="${CUR%%.*}"; REST="${CUR#*.}"; MINOR="${REST%%.*}"; PATCH="${REST##*.}"
  PATCH=$((PATCH + 1))
  VER="$MAJOR.$MINOR.$PATCH"
fi
echo "$VER" > "$VERSION_FILE"

# 同步版本到 build.gradle.kts
sed -i "s/versionName = \".*\"/versionName = \"$VER\"/" app/build.gradle.kts
VC=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts | head -1)
sed -i "s/versionCode = [0-9]*/versionCode = $((VC + 1))/" app/build.gradle.kts
echo ">>> 版本 -> v$VER"

export ANDROID_HOME="${ANDROID_HOME:-/workspace/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

# 本环境需要指定 aapt2（沙箱里跑不了 maven 版）；文件存在才加
AAPT2="$ANDROID_HOME/build-tools/34.0.4/aapt2"
PFLAG=""
[ -f "$AAPT2" ] && PFLAG="-Pandroid.aapt2FromMavenOverride=$AAPT2"

./gradlew assembleDebug --no-daemon -q $PFLAG 2>&1 | tail -8

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="包发布/NbtGen_v${VER}_${STAMP}.apk"
mkdir -p 包发布
cp app/build/outputs/apk/debug/app-debug.apk "$OUT"
echo ">>> 已归档: $OUT"
