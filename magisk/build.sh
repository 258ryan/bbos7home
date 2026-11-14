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
  # The manifest carries android:required as a stand-in for android:isStatic (see tools/patch_isstatic.py).
  "$AAPT2" link -o "build/$name-raw.apk" -I "$ANDROID_JAR" \
    --manifest "$dir/AndroidManifest.xml" --min-sdk-version 29 --target-sdk-version 29 \
    -R "build/$name-res.zip" --auto-add-overlay
  python3 tools/patch_isstatic.py "build/$name-raw.apk" "build/overlays/SlateSkin-$name.apk"
done

# 1) Magisk module (needs root)
cp -r module/. build/
(cd build && rm -f "../out/slate-skin-$VERSION.zip" && zip -qr "../out/slate-skin-$VERSION.zip" . -x "*-res.zip" -x "*-raw.apk")

# 2) andr36oid hotfix (no root): BOOT/hotfix/slate-skin.fix.zip. Layout as the ROM's applier expects:
#    manifest.txt, hotfix.sh, files/0 (the overlay APK).
rm -rf build/fix && mkdir -p build/fix/files
cp hotfix/manifest.txt hotfix/hotfix.sh build/fix/
cp build/overlays/SlateSkin-android.apk build/fix/files/0
(cd build/fix && zip -qX -D ../../out/slate-skin.fix.zip manifest.txt hotfix.sh files/0)

echo "built:"; ls -la out/
unzip -l "out/slate-skin.fix.zip"
