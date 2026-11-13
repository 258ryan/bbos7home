# bbos7home — BlackBerry OS 7-style launcher for gamepad devices

An Android home-screen replacement styled after BlackBerry OS 7 (big clock, phone/message/mail counters,
labelled icon grid, folder pager and dock) using the BB OS 7 theme icons in `app/src/main/assets/bb`, built to be driven entirely by a controller.

| Button | Action |
|---|---|
| D-pad / left stick | Move focus |
| A | Open app / counter |
| B | Back → "All" folder → top |
| X | Options (favourite, app info, uninstall) / clear a counter (or enable notification access) |
| Y or Start | Search (a hardware keyboard also starts search when you type) |
| L1 / R1 | Previous / next folder (All Apps, Favourites, Media, Games, Downloads, Frequent) |
| Select | System settings |

Touch works too (tap, long-press for options, tap folder names).

## Build
The **Build APK** GitHub Action produces `app-debug.apk` on every push (Actions tab → latest run → Artifacts).
Locally: `./gradlew assembleDebug` (needs the Android SDK, JDK 17).

## Install
Install the APK, press Home, choose **Slate Launcher** → Always. For the notification row, press A on
the prompt in the top row and grant notification access.
