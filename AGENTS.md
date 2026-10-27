# Setting up Simple TV launcher on a box (for AI coding agents)

You are helping someone install this launcher on their own Android TV or Google TV box. Work through the steps below in order, from a clone of this repository on their computer. Say in one line what you are about to do before each step. **Ask before every step that changes something on the box** (steps 4 to 7), and wait for a yes. Everything here can be undone; the undo is given at each step.

Do not switch off or uninstall system apps, change system settings for the person, or root anything. Some steps can only be done by the person with the remote; tell them which keys to press.

## 0. What to ask the person first

- The box's IP address (on the box: Settings → Network → the connection's details). Call it `BOX` below.
- Is network debugging on? If not, tell them how: Settings → System → About → press *Android TV OS build* seven times, then Settings → System → Developer options → *USB debugging*. The menu names differ a little between boxes.
- Optional, for later steps: Google Calendar secret iCal links (step 6), and pictures for the wallpaper (step 7).

Never write the calendar links, the box's IP address or any password into a file in this repository, a commit, an issue or a log you share.

## 1. Check the computer

```bash
adb version
java -version     # 17 or newer
```

If `adb` is missing, it comes with Android's [SDK Platform Tools](https://developer.android.com/tools/releases/platform-tools) (ask before installing anything).

## 2. Reach the box

```bash
adb connect BOX:5555
adb devices
```

- The first time, the box shows *Allow USB debugging?*: the person ticks *Always allow from this computer* and chooses *Allow*. Until then `adb devices` says `unauthorized`.
- `failed to connect` or `No route to host` while the box answers ping: debugging is off on the box, or (on a Mac) the computer has not let `adb` reach the local network yet; `adb kill-server` and connecting again brings up that question.

## 3. Build

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

This makes `app/build/outputs/apk/debug/app-debug.apk`.

## 4. Install (ask first)

```bash
adb -s BOX:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s BOX:5555 shell am start -n tld.my.hearth/.MainActivity
```

The person should now see the launcher. Undo: `adb -s BOX:5555 uninstall tld.my.hearth`.

## 5. Make it the home screen (the person does it on the box)

1. In the launcher: the last tile on the row (*Launcher settings*) → Home screen → *Launcher by default* → OK opens the box's choice of home app; pick *Simple TV launcher*.
2. On Google TV the Home button still goes to Google's own home screen. OK on *Launcher by default* again opens Accessibility; the person switches on *Simple TV launcher* there (it is the Home button helper; it reads nothing on screen).

Check with `adb -s BOX:5555 shell settings get secure enabled_accessibility_services` (it should name `tld.my.hearth/tld.my.hearth.HomeWatchService` on Google TV), then ask the person to press Home. *Launcher by default* shows *Yes* when the Home button opens the launcher.

Tell the person that Android switches the helper off whenever the launcher is updated (step 4 again), so it has to be switched on again after an update.

Do not switch off the stock home screen (`pm disable-user … launcherx`) on your own: on some Google TV boxes the set-up wizard then takes over and the screen stays black. If the person asks for it anyway, give them the undo first: `adb shell pm enable com.google.android.apps.tv.launcherx`.

## 6. Calendars (optional; ask first)

Ask for the links: in Google Calendar on a computer, Settings → the calendar → *Secret address in iCal format*. They start with `https://calendar.google.com/calendar/ical/` and end in `basic.ics`. Hand them to the launcher straight from the shell, without saving them in a file:

```bash
adb -s BOX:5555 shell am start -n tld.my.hearth/.MainActivity --esa calendars "<link 1>,<link 2>"
```

The calendar card appears within a few seconds. Undo: Settings → Widgets → Calendars → OK on a calendar → *Remove the calendar*.

## 7. Wallpapers (optional; ask first)

Thirteen pictures are built in. The person's own (JPG, PNG or WebP, 1920×1080 or larger) go to the launcher's folder:

```bash
adb -s BOX:5555 push <pictures> /sdcard/Android/data/tld.my.hearth/files/wallpapers/
```

Undo: `adb -s BOX:5555 shell rm /sdcard/Android/data/tld.my.hearth/files/wallpapers/<picture>`.

## 8. Finish

Tell the person what was done, what is left for them on the box (the city for the weather: Settings → Widgets → City; the language: Settings → Home screen → Language), and how to undo each step.

## Updating later

`git pull`, then steps 3 and 4 again, and remind the person to switch the Home button helper on again on Google TV.
