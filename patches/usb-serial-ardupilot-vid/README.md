# USB serial driver: ArduPilot vendor ids

Direct USB connection to a flight controller involves **two separate,
independent allowlists**, and both have to recognize the device:

1. **`Android/res/xml/device_filter.xml`** — controls whether Android even
   offers the "Allow the app to access the USB device?" permission dialog.
2. **This library's own driver prober** (`usb-serial-android` 0.1.0, a fork of
   `usb-serial-for-android`) — does its own, completely separate exact
   vendor-id/product-id match to decide which serial driver (FTDI, CP210x,
   Prolific, or CDC-ACM) to use. Getting past (1) doesn't help if (2) doesn't
   know the device either — the symptom is exactly that: the permission
   dialog appears, nothing else happens, and the app's own device list stays
   empty.

(1) was fixed in 4.0.0.6 by adding ArduPilot's official USB vendor id
(`0x2DAE` / 11694) to `device_filter.xml`. This patch fixes (2) for the same
vendor id, plus a second ArduPilot allocation some boards use.

## What changed

`com.hoho.android.usbserial.driver` ships as its own small vendored library
(`org.droidplanner.android:usb-serial-android:0.1.0` — like `dronekit-android`,
its upstream repository is also offline, so it's now vendored under
`Android/libs/` instead of resolved from Maven).

| File | Change |
|------|--------|
| `UsbSerialProber.java` | `CDC_ACM_SERIAL`'s `probe()` and `getAvailableSupportedDevices()` now also accept any device whose vendor id is `0x2DAE` (11694) — ArduPilot's own official USB vendor id, not shared with other projects, so a product-id wildcard is safe here. Scoped to the CDC-ACM prober only, so this vendor id isn't also (wrongly) probed as FTDI/CP210x/Prolific. |
| `CdcAcmSerialDriver.java` | `getSupportedDevices()` gained an entry for vendor id `0x1209` (4617) — the shared open-source [pid.codes](https://pid.codes/1209/) vendor id, which ArduPilot also has an allocation under — with product ids `0x5740` and `0x5741` (22336 / 22337). **Because `0x1209` is shared across many unrelated hobbyist projects, it is listed by exact product id, not wildcarded** the way `0x2DAE` is. |

Confirmed with a Pixhawk 2.4.8 clone reporting `VID_1209&PID_5741` — direct USB
connection failed before this patch (permission dialog appeared, nothing in
Tower's device list) and works after it.

## Build & test

Recompile against the **original** `classes.jar` (from the `.aar`) and
`android.jar`:

```
javac -source 8 -target 8 -g \
  -cp "classes.jar;<android-sdk>/platforms/android-36/android.jar" \
  -d out patches/usb-serial-ardupilot-vid/com/hoho/android/usbserial/driver/UsbSerialProber.java \
        patches/usb-serial-ardupilot-vid/com/hoho/android/usbserial/driver/CdcAcmSerialDriver.java
```

To ship: swap the resulting `.class` files into `classes.jar` (extract fully,
replace, repack with `jar cf`), then `classes.jar` back into
`usb-serial-android-0.1.0.aar` the same way — do not use in-place zip update
tools, they corrupt entry sizes.

## If your board still isn't recognized

Different ArduPilot board definitions can use different vendor/product id
combinations (clones in particular vary). Get the exact id — on a PC: Device
Manager → the device's Properties → Details tab → "Hardware Ids", you'll see
`USB\VID_xxxx&PID_xxxx` — and open an
[issue](https://github.com/nomar2/Tower/issues). If it's product id under
`0x1209` or `0x2DAE`, it's a one-line addition; a new vendor id entirely needs
both this file and `device_filter.xml` updated together.

---

## Authors

Android 12–16 modernization of Tower, including this patch:
**Ramón José Moreno** and **Alejandro Moreno** (2026).

`usb-serial-for-android` is the work of mik3y and contributors; this vendored
fork (`usb-serial-android` 0.1.0) and Tower/DroidPlanner are the work of
Arthur Benemann, 3D Robotics and the Tower / DroidPlanner contributors. This
patch is distributed under the same terms — GPLv3.
