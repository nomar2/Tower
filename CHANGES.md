# Changes in this fork

Relative to DroidPlanner/Tower 4.0.0. Modifications 2026 by Ramón José Moreno
and Alejandro Moreno.

## 4.0.0.2

### App identity

- The app is presented as **Tower GCS**; the About screen shows
  `Tower GCS v4.0.0.2`.

### Build system

- Gradle wrapper 8.7, Android Gradle Plugin 8.5.2, Kotlin 1.9.24, Java 17.
- `android.support.*` migrated to AndroidX (+ Jetifier for legacy dependencies).
- `compileSdk 36`, `minSdk 24`, `targetSdk 35`; namespace-based manifest.
- Removed Fabric/Crashlytics and the dead jcenter/bintray/3DR repositories.
- Baidu Maps SDK removed entirely (SDK, native libs, map provider, preferences).
- Google Maps key read from `local.properties` (`MAPS_API_KEY`) via a manifest
  placeholder.
- Release signing via a gitignored `keystore.properties` (or CI secrets);
  `./gradlew :Android:assembleProdRelease` produces a ~25% smaller APK with the
  file logging compiled out. A stable key lets updates install over a previous
  version without uninstalling.

### Android 8-16 runtime

- Notification channels for all foreground-service and status-bar notifications.
- `PendingIntent` `FLAG_IMMUTABLE` / `FLAG_MUTABLE` where required.
- `RECEIVER_NOT_EXPORTED` on runtime-registered receivers.
- `foregroundServiceType="connectedDevice"` and immediate `startForeground()`.
- Runtime permission requests (location, notifications, Bluetooth 12+).
- Location acquisition moved off the main thread (`FusedLocationProviderClient`)
  — fixes the touch/ANR freeze.
- `AppService` is now a proper foreground service tied to the vehicle connection.
- Scoped storage: missions/parameters stored under the app's external files dir;
  storage permissions removed; `content://` missions copied in before loading.
- Edge-to-edge (forced by `targetSdk 35`): the theme opts out on Android 15, and
  the sliding flight-action panel and the editor's bottom bar are padded by the
  navigation-bar inset on Android 16, so the connection bar is no longer hidden
  behind the system navigation bar.

### Connection / MAVLink

- Fixed the crash on **Connect** (Wi-Fi / USB connection handlers).
- Fixed the crash on **flight-mode change** (selection dialog rewritten as a
  concrete class for AndroidX).
- **MAVLink 2**: parser accepts `0xFD` frames (incompat/compat flags, 3-byte
  message id, 13-byte signature skipped); transmit auto-negotiates to MAVLink 2
  once a v2 frame is received from the peer. MAVLink 1 links are unaffected.
- Connecting to a real vehicle over **UDP** and **USB serial** was tested and
  works.
- **Flown on hardware** — a MINI Pix running ArduCopter 4.7.0, over a 433 MHz
  telemetry radio, over Wi-Fi and over a TCP link, with MAVLink 1 and MAVLink 2:
  connection and telemetry, flight-mode changes, arm, takeoff, mission edit /
  upload / download / run, parameter read-write, reboot, and clearing the vehicle
  mission. Not yet flown: follow-me and the dronie.
- **Mission transfer reliability**: the bundled `WaypointManager` used a 15 s
  watchdog with 3 retries, so one lost packet stalled an upload/download for
  15 s and three misses (45 s) aborted it — uploads and downloads over radio or
  Wi-Fi regularly needed a manual retry. The watchdog is now 3 s with 6 retries,
  and the app itself re-issues a stalled transfer once and reports a clear
  failure if the link is genuinely down.
- **Mission transfer progress**: uploading, downloading and clearing the
  vehicle mission now show a progress dialog for the duration of the transfer.
- **Dronie feedback**: pressing "Dronie" without a GPS fix now shows a message
  instead of silently doing nothing, the dronie upload gets the same progress
  bar and stall-retry as a normal upload, and the confirmation text spells out
  that the dronie is only uploaded (arm and switch to Auto to fly it).

### Features

- **Reboot flight controller** — menu action (shown when connected), confirmed
  with a slide-to-unlock, sends `MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN`.
- **Clear vehicle mission** — third option in the editor trash tool; erases the
  mission stored on the flight controller (`MISSION_CLEAR_ALL`), enabled only
  while a vehicle is connected and confirmed with a dialog.
- **Shut down & exit** — overflow-menu action that disconnects from the vehicle,
  closes every screen and stops the background service.
- **Improved GUIDED follow-me**:
  - alpha/beta filter over the operator's position and ground velocity;
  - setpoints streamed at 5 Hz with dead-reckoning between GPS fixes
    (instead of ~1 Hz);
  - position **and velocity** setpoints (`SET_POSITION_TARGET_GLOBAL_INT`,
    yaw / yaw-rate left uncontrolled);
  - short lead-ahead prediction; clean decay to a hold when the operator stops.
  - Leash and Above modes reworked onto the same path.

### Removed dead integrations

- Droneshare: background upload service, network client and account UI removed.
  The local telemetry-log (TLog) viewer and its session database are kept.
- Google Analytics (`play-services-analytics`) removed; the "usage statistics"
  preference removed.
- Weather Underground widget removed (API discontinued; widget was disabled).
- LeakCanary removed from debug builds (removed the extra "Leaks" launcher icon
  and the heap-dump overhead); re-enable with one line in `Android/build.gradle`.
