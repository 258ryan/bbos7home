# Slate Skin (Magisk module)

> **No root?** The Magisk module needs root. If your build has none, use the build-time version in [`../rom/`](../rom/README.md): a patch for the device tree that bakes the same widgets into the ROM.

Skeuomorphic system widgets for **LineageOS 18.1 (Android 11)**, as runtime resource overlays.
The ROM's framework (`andr36oid/android_frameworks_base`, branch `lineage-18.1`) declares no `<overlayable>` restrictions,
so every framework drawable can be replaced.

## v0.1 contents (framework overlay `com.slate.skin.android`)
Gel buttons (theme colour, gloss, bevelled outline, drop shadow) · recessed switch groove · recessed progress bar and
slider track with a glossy gel fill · glass dialogs and popup menus · pressed-in text-field underline.
Colours and fonts are untouched. It restyles system apps and anything that uses Android's standard widgets; Material /
Compose apps (Chrome, YouTube, ...) draw their own interface and are not affected.

## Install
1. Download `slate-skin-v0.1.zip` from the **Magisk module** workflow run (Actions tab → latest run → Artifacts).
2. Magisk app → Modules → Install from storage → pick the zip → reboot.
3. After the first boot, `service.sh` enables the overlay with `cmd overlay enable`; the screen may flicker once.
   Look at `/data/adb/modules/slate-skin/log.txt` if nothing changes.

## If something goes wrong
* **Boot guard:** if Android fails to finish booting 3 times in a row, the module stops mounting its overlays and disables itself.
* **Manually (recovery / adb):** `touch /data/adb/modules/slate-skin/disable` (or Magisk app → Modules → switch it off), then reboot.
* **Remove completely:** `touch /data/adb/modules/slate-skin/remove`, reboot.
* Switch the look off without removing it: `su -c "cmd overlay disable --user 0 com.slate.skin.android"`.

## Build
`AAPT2=<path>/aapt2 ANDROID_JAR=<path>/android.jar ./build.sh` produces `out/slate-skin-<version>.zip`.
CI does this on every push to this branch.

## Status
Built and checked with `aapt2` (all resource references resolve). **Not tested on a device.** Next steps, once this works:
colours / fonts / icon pack for the status bar and quick settings, SystemUI (shade, tiles, lock screen), switch thumb and slider knob.
