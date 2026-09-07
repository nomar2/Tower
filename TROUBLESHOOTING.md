# Troubleshooting & FAQ

Common questions, with answers, so you don't have to ask. If your problem
isn't here, open an issue: https://github.com/nomar2/Tower/issues — include your
**phone model**, **Android version**, **flight-controller firmware**, and **how
you connect** (USB serial / Wi-Fi / TCP / UDP).

This is a one-person fork with best-effort support. The connection and mission
paths are flight-tested on real hardware (see the README); other areas may not
be.

---

## Install & versions

### Which APK do I download?
- **`tower-<version>-release.apk`** — normal use. Smaller, no debug logging,
  signed with a stable key so future updates install over it.
- **`tower-<version>-dev-debug.apk`** — sideload / testing. Different app id
  (`.debug`), installs *alongside* the release build.

They can both be installed at the same time.

### Does it work on Android 14 / 15 / 16?
Yes. Built and tested on Android 10 through Android 16.

### "App not installed" / signature conflict when updating
You have an older Tower or DroidPlanner (or the other APK from this repo)
installed, signed with a different key. Uninstall it first, then install the new
one. There is no way around this — Android won't replace an app signed with a
different certificate.

### Is it on the Play Store?
No. Download the APK from the
[Releases page](https://github.com/nomar2/Tower/releases/latest).

---

## The map is blank / grey

The app needs a **Google Maps API key** and doesn't ship with one (keys are
tied to a billing account and can't be published).

If you built it yourself:
1. In `local.properties` add `MAPS_API_KEY=...`.
2. In the Google Cloud console, enable **Maps SDK for Android** on that key.
3. Add an **Android app restriction** for the package
   (`org.droidplanner.android` for the release build,
   `org.droidplanner.android.debug` for the dev build) with your signing
   certificate's **SHA-1**.
4. For a **release** build, add the *release* key's SHA-1 too — the debug and
   release certificates are different.

Map tiles are free to display; a billing account is only needed to activate the
key.

---

## Connecting to the vehicle

### What links are supported?
- **USB serial** — a SiK telemetry radio (433 / 915 MHz) on a USB-OTG cable.
  Tested.
- **Wi-Fi** — UDP or TCP to a Wi-Fi telemetry bridge or to SITL.
- **TCP** and **UDP** — direct to any MAVLink endpoint.

**Bluetooth** telemetry adapters are **not tested** in this fork.

### The USB radio isn't detected
- Use a proper **USB-OTG** cable/adapter; not all phone-to-USB cables carry the
  OTG signalling.
- Accept the **"Allow the app to access the USB device?"** dialog when it pops
  up. If you dismissed it, unplug and replug.
- Some phones don't provide enough power for a bare radio module — use a radio
  with its own USB connector, or a powered hub.
- SiK radios enumerate as a USB-serial device; the bundled FTDI driver covers
  the common ones.

### Connecting to SITL / a simulator
Use a **TCP** or **UDP** connection to the simulator's host and port
(from an Android emulator, the host machine is `10.0.2.2`).

### It connects then drops / telemetry freezes
- On a marginal radio link, lower the SiK air data rate or move the antennas.
- Over Wi-Fi, a weak signal or a busy network will stall the stream.
- If it reproduces on a *good* link, open an issue with a TLog (see below).

---

## MAVLink

### MAVLink 1 or 2?
Both. The link starts on MAVLink 1 and **auto-switches to MAVLink 2** the first
time the vehicle sends a v2 frame. Nothing to configure.

### Message signing
Received **signed** frames are accepted, but the signature is **not verified**.
Outgoing frames are **not signed**. If your vehicle *requires* signed MAVLink,
this fork won't connect.

---

## Missions

### Upload or download fails / needs a retry
Mission transfer reliability was improved (faster retry on lost packets, plus one
automatic app-level re-try). On a genuinely bad link it can still fail — retry
once the link is stable. If it fails consistently on a good link, open an issue
with a TLog.

### "Clear vehicle mission"
Third icon in the editor's trash tool. It erases the mission stored on the
flight controller. Only enabled while a vehicle is connected.

---

## Follow-me and the dronie

### Follow-me doesn't behave well
The GUIDED follow-me was reworked (filtered position + velocity setpoints at
5 Hz), but it is **only verified against a simulator so far — not flight-tested**.
The tuning constants may need adjusting on a real vehicle. Flight reports are
very welcome.

### The dronie does nothing / doesn't take off
"Dronie" only **builds and uploads** the mission. To fly it you still have to
**arm and switch to Auto** yourself. If you press it without a GPS fix it now
tells you instead of failing silently.

---

## Building

### `Could not resolve com.android.tools.build:gradle:...`
Gradle is running on the wrong Java. It needs **JDK 17**.
- Command line: set `JAVA_HOME` to a JDK 17 install.
- IDE / Android Studio: set `org.gradle.java.home=/path/to/jdk-17` in
  `~/.gradle/gradle.properties`, or point Gradle's JDK to 17 in the settings.

### What do I need?
JDK 17, the Android SDK with a recent platform and build-tools, and a Google
Maps API key (above). Then `./gradlew :Android:assembleDevDebug`.

### Release build
Copy `keystore.properties.example` to `keystore.properties`, fill in your
keystore path and passwords, then `./gradlew :Android:assembleProdRelease`.
Without `keystore.properties` the release build still works but is signed with
the debug key.

---

## Firmware / hardware scope

### PX4?
No. This is an **ArduPilot** GCS (ArduCopter, ArduPlane, Rover).

### What's *not* tested in this fork
Follow-me and the dronie in flight, Bluetooth telemetry adapters, and the
sensor-calibration screens (compass / accelerometer / radio / ESC). Parameter
read/write and the rest of the connected flows are tested.

---

## Reporting a bug

1. **GitHub Issues:** https://github.com/nomar2/Tower/issues
2. Include: phone model, Android version, flight-controller firmware and
   version, connection type (USB serial / Wi-Fi / TCP / UDP), and what you did.
3. If it's a connection or mission problem, attach a **TLog** — the app records
   one per session; the built-in TLog viewer lists them and they're under the
   app's external files directory.
4. For a crash, a `logcat` capture around the moment of the crash helps a lot.
