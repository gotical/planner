#!/usr/bin/env bash
# Сборка APK без внешних зависимостей (Android SDK + JDK).
# Требует: aapt2, javac, d8, apksigner (из Android SDK build-tools).
set -e

P="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
BT="$(ls -d "$ANDROID_HOME"/build-tools/* 2>/dev/null | sort -V | tail -1)"
ANDROID_JAR="$(ls -d "$ANDROID_HOME"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
PKG=ru.rybinsklab.planner
PKGPATH=${PKG//./\/}

echo "SDK: $ANDROID_HOME"
echo "Build tools: $BT"

rm -rf "$P/build"
mkdir -p "$P/build/classes" "$P/build/gen"

"$BT/aapt2" compile --dir "$P/res" -o "$P/build/res.zip"
"$BT/aapt2" link -o "$P/build/base.apk" -I "$ANDROID_JAR" --manifest "$P/AndroidManifest.xml" --java "$P/build/gen" "$P/build/res.zip"

javac -source 8 -target 8 -nowarn -classpath "$ANDROID_JAR" -d "$P/build/classes" \
  "$P/build/gen/$PKGPATH/R.java" \
  "$P/java/$PKGPATH"/*.java

java -Xmx2G -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$ANDROID_JAR" --output "$P/build" \
  "$P/build/classes/$PKGPATH"/*.class

"$BT/aapt2" link -o "$P/build/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$P/AndroidManifest.xml" "$P/build/res.zip" --auto-add-overlay

cp "$P/build/unsigned.apk" "$P/build/app.apk"
(cd "$P/build" && zip -qj app.apk classes.dex)

if [ -n "$KEYSTORE" ] && [ -n "$KEYSTORE_PASS" ]; then
  java -cp "$BT/lib/apksigner.jar" com.android.apksigner.ApkSignerTool sign \
    --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" \
    --out "$P/build/planner.apk" "$P/build/app.apk"
else
  cp "$P/build/app.apk" "$P/build/planner.apk"
  echo "WARNING: KEYSTORE не задан — APK не подписан. Задайте KEYSTORE и KEYSTORE_PASS."
fi

echo "Build OK: $P/build/planner.apk"
