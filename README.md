# bbos7home — BlackBerry OS 7-style launcher for gamepad devices

An Android home-screen replacement styled after BlackBerry OS 7 (glossy icon grid, notification
indicator row, focused-item caption, folder strip), built to be driven entirely by a controller.

| Button | Action |
|---|---|
| D-pad / left stick | Move focus |
| A | Open app / notification |
| B | Back → "All" folder → top |
| X | Options (favorite, app info, uninstall) / clear notifications |
| Y or Start | Search (a hardware keyboard also starts search when you type) |
| L1 / R1 | Previous / next folder (All, Favorites, Media, Games, Downloads, Frequent) |
| Select | System settings |

Touch works too (tap, long-press for options, tap folder names).

## Build
The **Build APK** GitHub Action produces `app-debug.apk` on every push (Actions tab → latest run → Artifacts).
Locally: `./gradlew assembleDebug` (needs the Android SDK, JDK 17).

## Install
Install the APK, press Home, choose **Slate Launcher** → Always. For the notification row, press A on
the prompt in the top row and grant notification access.
