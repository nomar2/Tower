# Tower GCS — Android ground control station for ArduPilot 

**Tower GCS** is a Ground Control Station (GCS) app for Android that flies and
monitors drones (multicopters, planes and rovers) running **ArduPilot**, over
**MAVLink 1 and MAVLink 2**. It is a maintained fork of the original
[DroidPlanner/Tower](https://github.com/DroidPlanner/Tower) — the "DroidPlanner"
Android GCS — brought back to life on **Android 10 through Android 16**.

It connects to a vehicle over a **USB serial telemetry radio** (SiK, 433 / 915
MHz, on an OTG cable), **Wi-Fi**, **TCP** or **UDP**, and is built on
[DroneKit-Android](https://github.com/dronekit/dronekit-android).

**[Download the latest APK →](https://github.com/nomar2/Tower/releases/latest)**

## Screenshots

<!-- Drop PNGs into docs/img/ and they render here and on the Pages site. -->

| Flight screen | Mission editor | Parameters |
|---|---|---|
| ![Tower GCS flight telemetry screen](docs/img/flight.png) | ![Tower GCS mission editor](docs/img/editor.png) | ![Tower GCS parameter editor](docs/img/params.png) |

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

The `dronekit-android-3.0.2.aar` under `Android/libs/` is a locally patched copy
(the upstream repo is offline). The MAVLink 2 patch is documented, as source and
with a unit test, under [`patches/mavlink2/`](patches/mavlink2/).

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

**Validated against SITL, through the app** (ArduCopter 4.7.1, MAVLink 2,
phone GPS, simulated vehicle):

- GUIDED follow-me in every sub-mode — Follow / Lead, Circle, Leash (radius in
  and out), Look-at-Me (holds position, yaws to track)
- GUIDED go-to (tap the map to fly there)
- Flight modes — RTL, Land, Brake
- Auto missions — upload, run, waypoint navigation, including survey-grid, ROI
  and spline items
- Automatic reconnection after the link drops (kill and restart SITL, the app
  picks the vehicle back up)

Not the same as a real flight (no wind, the vehicle's GPS is noise-free, the
link is local Wi-Fi rather than a telemetry radio), but the end-to-end
behaviour through Tower is exercised.

## FAQ

**Does Tower work on Android 14, 15 or 16?**
Yes. This fork is built and tested on Android 10 through Android 16. The original
DroidPlanner/Tower (2016) does not install or run on modern Android; this
repository is the fix.

**Is this the same as DroidPlanner?**
Yes — "DroidPlanner" was the project's original name and "Tower" is what it was
renamed to. This is a maintained fork of that codebase.

**How does it connect to the drone?**
USB serial telemetry radio (SiK 433 / 915 MHz through a USB-OTG cable), Wi-Fi,
TCP or UDP. Bluetooth telemetry adapters are not tested yet.

**Does it support MAVLink 2?**
Yes. The link auto-negotiates MAVLink 2 once the vehicle sends a v2 frame;
MAVLink 1 links keep working. Received signed frames are accepted (signature not
verified); outgoing frames are not signed.

**Which flight controllers / firmware?**
Anything running ArduPilot (ArduCopter, ArduPlane, Rover). Flown against a
MINI Pix on ArduCopter 4.7.0. It is not a PX4 GCS.

**Is it on the Play Store?**
No. Download the APK from the
[Releases page](https://github.com/nomar2/Tower/releases/latest). The
`-release.apk` is signed with a stable key so updates install over each other.

**Is it free / open source?**
Yes, GPLv3, same licence as the original.

**Something not working?**
See [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — blank map, USB radio not
detected, build errors, mission transfer, follow-me — then open an
[issue](https://github.com/nomar2/Tower/issues).

## Known limitations

- MAVLink 2 message signing: received signed frames are parsed (signature
  ignored); outgoing frames are not signed.
- The improved follow-me is validated end-to-end against SITL through the app
  (all sub-modes) but not yet flown on a vehicle; the filter and lead constants
  may still need adjusting in wind and with real GPS noise.

Planned work is tracked in [`ROADMAP.md`](ROADMAP.md).

## License

GNU General Public License v3.0 — see [`LICENSE`](LICENSE) (or the
formatted [`LICENSE.md`](LICENSE.md)).

This is a modified version of DroidPlanner/Tower. Modifications 2026 by
Ramón José Moreno and Alejandro Moreno. Original work by Arthur Benemann,
3D Robotics and the Tower/DroidPlanner contributors. See [`AUTHORS`](AUTHORS)
and [`NOTICE`](NOTICE).
