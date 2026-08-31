# Tower (Android 16 build)

Tower is a Ground Control Station (GCS) Android app for UAVs running ArduPilot,
built on top of [DroneKit-Android](https://github.com/dronekit/dronekit-android).

This repository is a maintained fork of the original
[DroidPlanner/Tower](https://github.com/DroidPlanner/Tower) project, updated to
build and run on current Android versions and with a number of functional fixes
and improvements.

## What's different in this fork

- **Toolchain modernised** — Gradle 8, Android Gradle Plugin 8, Kotlin 1.9,
  Java 17, AndroidX, `compileSdk 36`, `minSdk 24`, `targetSdk 35`. Builds an
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

### Release build

`./gradlew :Android:assembleDevDebug` produces a debug APK. For a real
(smaller, no debug logging) build you want a **signed** release, so that
updates install over a previous version without uninstalling.

1. Create a signing key once and keep the `.jks` file and its passwords backed
   up somewhere safe — losing them means you can never update the same app id
   again without a full uninstall:

   ```
   keytool -genkeypair -v -keystore tower-release.jks -alias tower \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Copy [`keystore.properties.example`](keystore.properties.example) to
   `keystore.properties` (repo root, gitignored) and fill in `storeFile`,
   `storePassword`, `keyAlias`, `keyPassword`.

3. Build:

   ```
   ./gradlew :Android:assembleProdRelease
   ```

   Output: `Android/build/outputs/apk/prod/release/`.

Without `keystore.properties` the release build still succeeds but is signed
with the debug key — fine for a quick test, not for distribution.

CI signs the release automatically if the repository has the secrets
`RELEASE_KEYSTORE_BASE64` (`base64 -w0 tower-release.jks`),
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`.

## Hardware testing

Flown against a **MINI Pix running ArduCopter 4.7.0**, over a **433 MHz SiK
telemetry radio on USB serial** (OTG cable to the ground radio), over **Wi-Fi**,
and over **TCP** and **UDP** links, with MAVLink 1 and MAVLink 2. Confirmed
working:

- Connection and live telemetry on both links and both protocol versions
- Flight-mode changes, arming, takeoff
- Missions — editing, upload, download, and running the mission
- Reboot flight controller
- Clear the mission stored on the vehicle
- Reading and writing parameters
- Shutting the app down cleanly

Tested on phones from **Android 10 to Android 16** (a TCP connection on Android 10
works fine).

Not yet exercised on hardware: follow-me, the dronie, Bluetooth telemetry
adapters, and the sensor-calibration screens (compass / accelerometer /
radio / ESC).

## Known limitations

- MAVLink 2 message signing: received signed frames are parsed (signature
  ignored); outgoing frames are not signed.
- The improved follow-me is verified at the command-stream level against a
  simulator; the flight tuning constants may still need adjusting on a vehicle.

Planned work is tracked in [`ROADMAP.md`](ROADMAP.md).

## License

GNU General Public License v3.0 — see [`LICENSE.md`](LICENSE.md).

This is a modified version of DroidPlanner/Tower. Modifications 2026 by
Ramón José Moreno and Alejandro Moreno. Original work by Arthur Benemann,
3D Robotics and the Tower/DroidPlanner contributors. See [`AUTHORS`](AUTHORS)
and [`NOTICE`](NOTICE).
