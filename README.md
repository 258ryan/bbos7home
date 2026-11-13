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

## Build
The **Build APK** GitHub Action produces `app-debug.apk` on every push (Actions tab → latest run → Artifacts).
Locally: `./gradlew assembleDebug` (needs the Android SDK, JDK 17).

## Install
Install the APK, press Home, choose **Slate Launcher** → Always. For the notification row, press A on
the prompt in the top row and grant notification access.

## Slate Music (standalone app, `music/`)
A separate skeuomorphic music player (iOS 6 / BB OS 7 look: black nav bars, pinstripe lists, linen Now Playing
screen with glossy art + reflection, chrome transport buttons). Uses the vendored
[Pwnapple](third_party/pwnapple/NOTICE.md) component kit (MIT).

Songs / Artists / Albums / Now Playing, search, shuffle / repeat, background playback with a media notification.

| Button | Action |
|---|---|
| A / B | Select / back |
| X | Play / pause |
| Y | Shuffle |
| Select | Repeat |
| L1 / R1 | Previous / next track |
| L2 / R2 | Previous / next tab |
| Start | Jump to / from Now Playing |
| D-pad on Now Playing | left / right = seek 10 s, up / down = volume |

CI builds both APKs: `slate-launcher-debug` and `slate-music-debug`.
