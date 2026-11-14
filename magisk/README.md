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

## Install without root (andr36oid hotfix) - `slate-skin.fix.zip`
The andr36oid ROM can apply `.fix.zip` hotfixes from the SD card before Android starts, so no root and no recompiling.
1. Take `slate-skin.fix.zip` from the workflow artifact (the artifact itself is a zip: unzip it once to get the two files).
2. If your release's applier is older than `build=any`, also install **enable-any.fix.zip** from
   https://github.com/andr36oid/hotfixes (its README says to install it once and leave it).
3. SD card in a computer -> **BOOT** drive -> folder `hotfix` (make it if missing) -> copy the zip(s) in. Do not rename or extract them.
4. Put the card back and start the console. Look at **Settings -> System -> Hotfixes -> Results from this start**:
   `slate-skin` should say *Applied*. Then open Settings and look at a switch, a slider or a dialog.
5. Undo: delete the zip from the `hotfix` folder. Skip it once: hold **Start+Select** while powering on.
   The ROM's own boot protection pauses all hotfixes after two starts that do not finish.

How it works: `files/0` is a *static* overlay on framework-res. The system image is read-only and overlays are only trusted
inside a system overlay folder, so `hotfix.sh` binds the overlay APK over one of the optional theme overlays (icon shape,
accent colour, icon pack, cutout, font) with the applier's `hotfix_bind`. Hotfixes run before zygote, so the package manager sees it
when it scans for overlays. That one theme choice stops working while the hotfix is installed.
If Settings shows *Failed*, run `adb shell cat /data/local/tmp/slate-skin.txt` (no root needed): it lists the overlay files
on your build and says what the script did.

## Install with root (Magisk module) - `slate-skin-v0.1.zip`
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
