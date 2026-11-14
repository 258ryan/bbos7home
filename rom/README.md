# Slate Skin as a ROM change (no root needed)

`0001-overlay-skeuomorphic-framework-widgets-*.patch` is a patch for **andr36oid/android_device_gameconsole_r36s**
(branch `lineage-18.1`). It adds eight files under `overlay/frameworks/base/core/res/res/`, the folder the device
already pulls into the build with `DEVICE_PACKAGE_OVERLAYS += $(LOCAL_PATH)/overlay` (see `device.mk`).

Because this is a *build-time* resource overlay, the new drawables are compiled into framework-res itself:
no root, no Magisk, no runtime overlay rules, nothing to enable.

## What changes
Gel buttons · recessed switch groove · recessed progress / slider tracks with a glossy fill · glass dialogs and popup menus ·
pressed-in text-field underline. Everything still follows the theme colours (`colorButtonNormal`, `colorControlActivated`,
`colorBackground`). Replaced files: `btn_default_mtrl_shape`, `switch_track_material`, `progress_horizontal_material`,
`seekbar_track_material`, `dialog_background_material`, `popup_background_material`, `edit_text_material`;
new file: `color/slate_switch_track`.

## Apply
```
cd android_device_gameconsole_r36s     # branch lineage-18.1
git am /path/to/0001-overlay-skeuomorphic-framework-widgets-*.patch
```
Then rebuild the ROM as usual (the build also needs the framework-res target rebuilt; a normal `mka` does that).

## Status
The XML is well-formed and was compiled and linked with aapt2 as a runtime overlay (all references resolve). It has **not**
been built into a ROM or run on the device.

## Next
The same `overlay/` folder can restyle SystemUI (`overlay/frameworks/base/packages/SystemUI/res/`) and Settings
(`overlay/packages/apps/Settings/res/`): notification shade, quick settings tiles, status bar, lock screen.
