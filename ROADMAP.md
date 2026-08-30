# Roadmap

Current state: the app builds with a modern toolchain, runs on Android 16, flies
missions and follows against a MAVLink simulator, and connects to a real vehicle
over UDP and USB serial. Bluetooth is not re-checked yet and there is no
release-signing pipeline.

## Priority 1 — make it field-ready and publishable

- [x] **Hardware connectivity** — UDP and USB serial tested and working.
- [ ] Re-check **Bluetooth** (SiK-style radios).
- [ ] **In-flight validation** — arm / takeoff / mode changes / mission run /
      follow-me on a real vehicle, end to end.
- [ ] **Signed release build** — add a `signingConfig`, produce and test a
      release APK. Optionally enable R8/minify with `-keep` rules (the
      reflection-based `ExperimentalApi.sendMavlinkMessage` path must be kept).
- [ ] **targetSdk 34 → 35** — required by Google Play for new uploads; should be
      near-direct since no native libraries remain in the APK.
- [ ] **Vehicle setup / calibration** — the compass / accelerometer / radio /
      ESC screens send MAVLink and are currently untested.
- [x] **CI** — GitHub Actions builds the debug APK on every push
      (`.github/workflows/build.yml`).

## Priority 2 — features

- [x] **USB 5.8 GHz video (Eachine ROTG etc.)** — the dead 2016 `libuvccamera`
      was replaced with `com.herohan:UVCAndroid` and the UVC widget rebuilt.
      Verified to build and start without a device; the actual preview needs
      testing with a real receiver on a USB-OTG phone.
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
