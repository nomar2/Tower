# Roadmap

Current state: the app builds with a modern toolchain, runs on phones from
Android 10 to Android 16, and has been flown against a MINI Pix / ArduCopter
4.7.0 over a 433 MHz SiK radio, Wi-Fi and TCP / UDP links (MAVLink 1 and 2) —
connection, telemetry, mode changes, arm, takeoff, mission
edit/upload/download/run, parameters, reboot and vehicle-mission-clear all work.
Follow-me and the dronie are still simulator-only.

## Priority 1 — make it field-ready and publishable

- [x] **Hardware connectivity** — 433 MHz SiK telemetry radio, Wi-Fi, TCP and UDP
      tested on a MINI Pix (ArduCopter 4.7.0), MAVLink 1 and 2.
- [ ] Re-check **Bluetooth** telemetry adapters and **USB-direct serial** on
      hardware.
- [x] **In-flight validation** — arm / takeoff / mode changes / mission
      edit-upload-download-run / parameters / reboot / clear-vehicle-mission
      verified on a MINI Pix. Still to fly: **follow-me** and the **dronie**.
- [x] **Signed release build** — `keystore.properties` (or CI secrets) drives a
      `signingConfig`; `./gradlew :Android:assembleProdRelease` produces the
      APK. R8/minify still off (optional; the reflection-based
      `ExperimentalApi.sendMavlinkMessage` path would need `-keep` rules).
- [x] **targetSdk 34 → 35** — done; no native libraries in the APK so it was
      near-direct.
- [ ] **Vehicle setup / calibration** — the compass / accelerometer / radio /
      ESC screens send MAVLink and are currently untested.
- [x] **CI** — GitHub Actions builds the debug APK on every push
      (`.github/workflows/build.yml`).

## Priority 2 — features

- [ ] **USB 5.8 GHz video (Eachine ROTG etc.)** — replace the dead 2016
      `libuvccamera` with a maintained arm64 UVC library
      (`com.herohan:UVCAndroid`) and re-enable the UVC video widget.
- [ ] **External Bluetooth / USB GPS** as the follow-me position source (the
      phone GPS is the weak link), plus an in-flight panel to tune the follow
      lead / radius / altitude.
- [ ] **MAVLink 2 signing** (receive and transmit) with a key-setup screen —
      for links that require it (Herelink, some mavlink-router configs).
- [ ] **Native ArduPilot FOLLOW mode** as an alternative to the GCS-side GUIDED
      loop (offloads tracking to the flight controller).

## Priority 3 — modernization / polish

- [ ] Replace deprecated APIs (`AsyncTask`, bare `Handler()`,
      `PreferenceManager`, `android.app.Fragment`).
- [ ] Self-hosted crash reporting (ACRA or Sentry) — Crashlytics was removed.
- [ ] Translate the new strings (reboot action, credits); remove the orphan
      weather / droneshare strings left in the `values-*` locale files.
- [ ] Visual refresh (Material 3, dark theme).
- [ ] Offline maps — the Mapbox tile provider likely uses a retired API.
- [ ] Dependency updates and gradual Kotlin conversion.
