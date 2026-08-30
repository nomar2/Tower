# Changes in this fork

Relative to DroidPlanner/Tower 4.0.0. Modifications 2026 by Ramón José Moreno
and Alejandro Moreno.

## 4.0.1

### Build system

- Gradle wrapper 8.7, Android Gradle Plugin 8.5.2, Kotlin 1.9.24, Java 17.
- `android.support.*` migrated to AndroidX (+ Jetifier for legacy dependencies).
- `compileSdk 36`, `minSdk 24`, `targetSdk 34`; namespace-based manifest.
- Removed Fabric/Crashlytics and the dead jcenter/bintray/3DR repositories.
- Baidu Maps SDK removed entirely (SDK, native libs, map provider, preferences).
- LeakCanary updated to 2.x (debug only).
- Google Maps key read from `local.properties` (`MAPS_API_KEY`) via a manifest
  placeholder.

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

### Connection / MAVLink

- Fixed the crash on **Connect** (Wi-Fi / USB connection handlers).
- Fixed the crash on **flight-mode change** (selection dialog rewritten as a
  concrete class for AndroidX).
- **MAVLink 2**: parser accepts `0xFD` frames (incompat/compat flags, 3-byte
  message id, 13-byte signature skipped); transmit auto-negotiates to MAVLink 2
  once a v2 frame is received from the peer. MAVLink 1 links are unaffected.
- Connecting to a real vehicle over **UDP** and **USB serial** was tested and
  works.

### Features

- **Reboot flight controller** — menu action (shown when connected), confirmed
  with a slide-to-unlock, sends `MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN`.
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
