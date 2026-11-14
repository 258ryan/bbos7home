#!/system/bin/sh
# Runs inside Magisk's installer. $MODPATH is the module folder, $API the Android API level.
ui_print "- Slate Skin v0.1"
[ "$API" -ne 30 ] && ui_print "! Built for Android 11 (API 30); this device is API $API. Continuing anyway."

# Overlays live in /product/overlay when the device has a product partition, else /system/overlay.
if [ -d /product ] || [ -d /system/product ]; then
  DEST=$MODPATH/system/product/overlay
else
  DEST=$MODPATH/system/overlay
fi
mkdir -p "$DEST"
for f in "$MODPATH"/overlays/*.apk; do
  [ -f "$f" ] && cp "$f" "$DEST/" && ui_print "- Added $(basename "$f")"
done
rm -rf "$MODPATH/overlays"
set_perm_recursive "$MODPATH/system" 0 0 0755 0644
ui_print "- Installed to ${DEST#$MODPATH}"
ui_print "- Reboot to apply. If the phone fails to boot 3 times, the module turns itself off."
