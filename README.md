# bbos7home — BlackBerry OS 7-style launcher for gamepad devices

An Android home-screen replacement styled after BlackBerry OS 7 and built to be driven by a controller.

* Big clock, phone / messages / mail counters, search.
* **App bar**: the current folder is one row of icons that scrolls with the D-pad. Press **Down** to
  expand it into the full grid (folders such as Multimedia / Applications); **B** or **Up** from the
  top row collapses it again. Your system wallpaper shows through (dimmed when expanded).
* Folders: All, Favourites, Frequent, Media, Games, Downloads (**L1 / R1** or tap the names).
  The first bar of "All" is your pinned Favourites (X on an app to add / reorder).
* **Alarm indicator** in the banner (next alarm; opens the clock app).
* **Widgets** folder: hosts real Android widgets (X on the folder to add / remove; widgets themselves are touch-only).
* Icons come from the BB OS 7 theme in `app/src/main/assets/bb`; other apps keep their own icon.

| Button | Action |
|---|---|
| D-pad / left stick | Move focus (Down on the bar expands) |
| A | Open app / counter |
| B | Collapse, then back to "All" |
| X | Options (favourite, move, app info, uninstall) / clear a counter (or enable notification access) |
| Y or Start | Search (a hardware keyboard also starts search when you type) |
| L1 / R1 | Previous / next folder |
| Select | System settings |

## Slate Keyboard (system keyboard for gamepads)
Open **Slate Keyboard** from the app list, turn it on, then choose it as the current keyboard.

| Button | Action |
|---|---|
| D-pad / stick | Move the highlighted key |
| A | Type |
| B | Backspace (also when the device sends B as Back; can be turned off in the setup screen) |
| X | Space |
| Y | Shift (twice = caps lock) |
| Start | Enter / editor action (Go, Search, Send…) |
| Select | Hide keyboard |
| L1 / R1 | Move cursor left / right |
| L2 / R2 | Letters / symbols / more symbols / emoji |
| Up from the top row | Word suggestions (bundled 10k-word list + words you type), A to accept |

Touch works too. Auto-capitalisation, number/phone layouts and password fields are handled.

## Games page, settings, icon packs
* **Games** (folder strip): categories on the left (Recent, Favourites, All, Android, one per system), cover grid on the right,
  details above it. A plays, X for options. Android games plus ROMs found in `<ROMs>/<system>/` (gba, snes, psx, psp, ...).
  Cover art: `<game>.png` next to the ROM, or in `images/`, `covers/`, `media/box2dfront/` or `<root>/art/<system>/`.
  Emulator launch recipes (RetroArch cores, PPSSPP, custom) are editable in settings; the defaults are untested starting points.
* **Launcher settings** (Select, or the Quick Settings tile): master-detail layout with Appearance, Icons, Apps, Folders,
  Controls, Games, Backup, About. Wallpaper picker (built-in generated wallpapers or your own picture), dim level,
  textures on/off, grid columns, hidden apps, swap A/B, export / import of all settings.
* **Icons**: X on an app -> Change icon (icon pack, full BB OS 7 set, Tango / elementary, your own image), Rename, Hide.
  Nova / ADW style icon packs are supported (`appfilter.xml`); unmatched icons can sit on the glossy plate, stay original or use the pack background.
* UI glyphs (Quick Settings, menus, settings) use Tango (public domain) and elementary (GPL-3.0-or-later) icons;
  apps keep the BB OS 7 icons, so the two roles look different.

## Build
The **Build APK** GitHub Action produces `app-debug.apk` on every push (Actions tab → latest run → Artifacts).
Locally: `./gradlew assembleDebug` (needs the Android SDK, JDK 17).

## Install
Install the APK, press Home, choose **Slate Launcher** → Always. For the notification row, press A on
the prompt in the top row and grant notification access.

## Slate Music (standalone app, `music/`)
A separate music player in the same BB OS 7 look as Slate Home: navy banner with the clock, folder strip
(Songs / Artists / Albums / Now Playing), solid-blue selection, button-hint bar, glossy art with reflection and
chrome transport buttons. The BB OS 7 theme icons are shared with the launcher.

* **Landscape** is a split screen: library on the left, a permanent Now Playing panel on the right.
* **Portrait** uses folders plus a mini player; Now Playing is its own folder.
* Search, shuffle / repeat, background playback with a media notification.
* Selection is tracked by the app itself, so the highlight is always visible with a D-pad.

| Button | Action |
|---|---|
| D-pad | Move selection (landscape: right = into the Now Playing panel, left / B = back to the list) |
| A | Play / activate |
| B | Back |
| X | Play / pause |
| Y | Search (use Slate Keyboard or any keyboard) |
| Select | Shuffle |
| L1 / R1 | Previous / next folder |
| L2 / R2 | Previous / next track |
| Start | Now Playing |
| In the Now Playing panel | up / down = row, left / right = adjust (seek, buttons, volume), A = press |

CI builds both APKs: `slate-launcher-debug` and `slate-music-debug`.

## Materials
Both apps (and the keyboard) share one skeuomorphic "materials" layer, drawn at runtime with no image assets:
pebbled leather with stitched edges, brushed-steel bands, bevelled chrome keycaps and buttons with drop shadows,
blue gel highlights with a gloss band and glow, recessed text fields and slider tracks, glossy navy glass banners.
See `Materials.kt` in `app/` (Views) and `music/` (Compose).
