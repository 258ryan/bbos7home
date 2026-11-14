#!/system/bin/sh
# Reapplied on every start. Keep this script idempotent.
#
# files/0 is a *static* overlay on framework-res: Android applies it by itself at boot, with nothing to enable.
# The system image is read-only and overlays are only trusted when they sit in a system overlay folder, so the
# overlay APK is bound over one of the optional theme overlays (icon shape, accent colour, font, display cutout).
# Hotfixes run before zygote, so the package manager sees our APK at that path when it scans for overlays.
set -e
R=${HOTFIX_ROOT:-}
# A readable report (adb shell cat /data/local/tmp/slate-skin.txt), since the applier's own log needs root.
OUT=${HOTFIX_OUT:-/data/local/tmp/slate-skin.txt}
{
	echo "slate-skin hotfix report"
	for dir in /system/product/overlay /product/overlay /system/overlay /vendor/overlay /system/system_ext/overlay /system_ext/overlay; do
		[ -d "$R$dir" ] || continue
		echo "== $dir"
		ls -1 "$R$dir" 2>&1 | head -80
	done
} > "$OUT" 2>&1 || true
chmod 0644 "$OUT" 2>/dev/null || true
target=
for pattern in 'IconShape*Overlay' 'AccentColor*Overlay' 'IconPack*LauncherOverlay' 'IconPack*ThemePickerOverlay' \
               'DisplayCutoutEmulation*Overlay' 'FontNotoSerifSourceOverlay'; do
	for dir in /system/product/overlay /product/overlay /system/overlay /vendor/overlay /system/system_ext/overlay /system_ext/overlay; do
		for f in "$R$dir"/$pattern.apk "$R$dir"/$pattern/$pattern.apk; do
			if [ -f "$f" ]; then
				target=${f#"$R"}
				break 3
			fi
		done
	done
done
if [ -z "$target" ]; then
	log -t slate-skin "no optional theme overlay found to use as a slot; nothing applied"
	echo "slate-skin: no optional theme overlay found" >&2
	echo "RESULT: nothing applied (no optional theme overlay found)" >> "$OUT" 2>/dev/null || true
	exit 1
fi
log -t slate-skin "binding the overlay over $target"
echo "RESULT: overlay bound over $target" >> "$OUT" 2>/dev/null || true
hotfix_bind files/0 "$target"
