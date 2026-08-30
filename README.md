# Tower (Android 16 build)

Tower is a Ground Control Station (GCS) Android app for UAVs running ArduPilot,
built on top of [DroneKit-Android](https://github.com/dronekit/dronekit-android).

This repository is a maintained fork of the original
[DroidPlanner/Tower](https://github.com/DroidPlanner/Tower) project, updated to
build and run on current Android versions and with a number of functional fixes
and improvements.

## What's different in this fork

- **Toolchain modernised** — Gradle 8, Android Gradle Plugin 8, Kotlin 1.9,
  Java 17, AndroidX, `compileSdk 36`, `minSdk 24`, `targetSdk 34`. Builds an
  ABI-universal APK.
- **Runtime fixes for Android 8-16** — notification channels, `FLAG_IMMUTABLE`
  / `FLAG_MUTABLE` pending intents, `RECEIVER_NOT_EXPORTED`, foreground-service
  types, runtime permission requests, and a fix for the main-thread location
  call that caused a touch/ANR freeze.
- **Connection stability** — fixes for the crashes seen when connecting a
  vehicle and when changing flight mode.
- **MAVLink 2** — the bundled MAVLink stack now parses `0xFD` frames (2 flag
  bytes, 3-byte message id, signature skipping) and auto-negotiates MAVLink 2 on
  transmit once the peer speaks it. MAVLink 1 links keep working.
- **Improved GUIDED follow-me** — the operator's position and ground velocity
  are filtered, setpoints are streamed at 5 Hz with dead-reckoning between GPS
  fixes, position **and velocity** are sent (no more braking at every point),
  and the target leads the operator slightly. The location source was moved to
  the modern `FusedLocationProviderClient`.
- **Scoped storage** — missions and parameters are stored in the app's own
  external files directory; no storage permission required.
- **"Reboot flight controller"** action in the connected menu (slide-to-confirm).
- **Dead integrations removed** — Baidu Maps, the Droneshare upload service and
  account UI, Google Analytics, and the Weather Underground widget. The local
  telemetry-log (TLog) viewer is kept.

See [`CHANGES.md`](CHANGES.md) for the full list.

## Building

Requirements: JDK 17+, Android SDK with platform 34/36 and recent build-tools.

1. Create `local.properties` in the repo root (copy from
   [`local.properties.example`](local.properties.example)):

   ```
   sdk.dir=/path/to/Android/Sdk
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_ANDROID_API_KEY
   ```

   The Google Maps key needs the **Maps SDK for Android** enabled and an Android
   app restriction for `org.droidplanner.android` /
   `org.droidplanner.android.debug` with your signing certificate's SHA-1. Map
   display itself is free; a billing account is only required to activate the
   key.

2. Build the debug APK:

   ```
   ./gradlew :Android:assembleDevDebug
   ```

   Output: `Android/build/outputs/apk/dev/debug/`.

The `dev` flavor builds `org.droidplanner.android.debug`; the `prod` flavor
builds the release `org.droidplanner.android`.

## Notes / known limitations

- Connectivity has been tested on real hardware over **UDP** and **USB serial**
  and works; Bluetooth (SiK-style radios) has not been re-checked yet.
- MAVLink 2 message signing: received signed frames are parsed (signature
  ignored); outgoing frames are not signed.
- `targetSdk` is 34; moving to 35/36 is straightforward (no native libraries
  remain in the APK).
- The improved follow-me is verified at the command-stream level; the flight
  tuning constants may still need adjusting against a real vehicle.

Planned work is tracked in [`ROADMAP.md`](ROADMAP.md).

## License

GNU General Public License v3.0 — see [`LICENSE.md`](LICENSE.md).

This is a modified version of DroidPlanner/Tower. Modifications 2026 by
Ramón José Moreno and Alejandro Moreno. Original work by Arthur Benemann,
3D Robotics and the Tower/DroidPlanner contributors. See [`AUTHORS`](AUTHORS)
and [`NOTICE`](NOTICE).
