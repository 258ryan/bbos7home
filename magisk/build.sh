#!/usr/bin/env bash
# Builds the overlay APKs with aapt2 and zips the Magisk module.
#   AAPT2=/path/to/aapt2 ANDROID_JAR=/path/to/android.jar ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
: "${AAPT2:=aapt2}"
: "${ANDROID_JAR:?set ANDROID_JAR to an android.jar (API 23 or newer)}"
VERSION=$(grep '^version=' module/module.prop | cut -d= -f2)
rm -rf build out && mkdir -p build/overlays out
for dir in overlays/*/; do
  name=$(basename "$dir")
  "$AAPT2" compile --dir "$dir/res" -o "build/$name-res.zip"
  "$AAPT2" link -o "build/overlays/SlateSkin-$name.apk" -I "$ANDROID_JAR" \
    --manifest "$dir/AndroidManifest.xml" --min-sdk-version 30 --target-sdk-version 30 \
    -R "build/$name-res.zip" --auto-add-overlay
done
cp -r module/. build/
(cd build && rm -f "../out/slate-skin-$VERSION.zip" && zip -qr "../out/slate-skin-$VERSION.zip" . -x "*-res.zip")
echo "built magisk/out/slate-skin-$VERSION.zip"; unzip -l "out/slate-skin-$VERSION.zip" | tail -15
