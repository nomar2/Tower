# Arm/disarm and reboot retry patch

`MAV_CMD_COMPONENT_ARM_DISARM` (arm/disarm) and `MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN`
(reboot) are each a single `COMMAND_LONG` with no protocol-level retry of their
own — unlike mission upload/download, which already has a watchdog. On a lossy
link, a single dropped packet used to fail the command silently: no error, no
retry, nothing visibly happens. Reproduced on hardware: a vehicle that arms and
reboots fine over USB-SiK failed to do either over a lossy UDP telemetry link.

The fix has two parts.

## Arm/disarm — no aar patch needed

The aar already tracks every `msg_command_long` it sends by its `COMMAND_ACK`,
with a 2 s timeout (`DroneCommandTracker`, inside `MAVLinkClient.sendMavMessage`)
— but only when a listener is passed in. The app was calling
`VehicleApi.arm(boolean)`, the 1-argument overload that passes `listener = null`,
so the tracker never engaged and the command was sent exactly once with zero
feedback.

The fix is entirely in app code (`BaseFlightControlFragment.armWithRetry()`):
call the existing `VehicleApi.arm(boolean, AbstractCommandListener)` overload
instead, with a listener that resends (up to twice) on `onTimeout()` and shows
a failure toast on `onError()` or after retries are exhausted.

## Reboot — needed a small aar patch

Reboot goes through `ExperimentalApi.sendMavlinkMessage(MavlinkMessageWrapper)`,
since dronekit-android has no dedicated reboot action — but that method had no
listener-accepting overload, and the one place that *could* have forwarded a
listener across the process boundary
(`GenericMavLinkDrone.executeAsyncAction()`'s `SEND_MAVLINK_MESSAGE` case) was
hardcoding `CommonApiUtils.sendMavlinkMessage(this, messageWrapper)` — the
no-listener overload — even though `listener` was already sitting right there
in scope, just not passed through.

| File | Change |
|------|--------|
| `ExperimentalApi.java` | new `sendMavlinkMessage(MavlinkMessageWrapper, AbstractCommandListener)` overload, using `performAsyncActionOnDroneThread` (which the AIDL layer can carry a listener across) instead of the listener-less `performAsyncAction` |
| `GenericMavLinkDrone.java` | `SEND_MAVLINK_MESSAGE` case now forwards the already-available `listener` into `CommonApiUtils.sendMavlinkMessage(...)` instead of dropping it |
| `CommonApiUtils.java` | new `sendMavlinkMessage(MavLinkDrone, MavlinkMessageWrapper, ICommandListener)` overload, calling `drone.getMavClient().sendMessage(message, listener)` instead of `sendMessage(message, null)` |

Because the sent message is a `msg_command_long` either way, this reboot ACK
now rides the *same* generic `DroneCommandTracker` that already tracks arm — no
new tracking logic needed, just wiring the listener all the way through.
`SuperUI.rebootVehicle()` uses the new overload with the same retry-then-fail
listener pattern as arm.

## Build & test

Recompile against the **original** `classes.jar`, `Mavlink.jar` (both from the
aar) and `timber` (a transitive dependency `CommonApiUtils` uses for logging):

```
javac -source 8 -target 8 -g \
  -cp "classes.jar;Mavlink.jar;timber-5.0.1.jar;<android-sdk>/platforms/android-36/android.jar" \
  -d out patches/arm-reboot-retry/com/o3dr/android/client/apis/ExperimentalApi.java \
        patches/arm-reboot-retry/org/droidplanner/services/android/impl/core/drone/autopilot/generic/GenericMavLinkDrone.java \
        patches/arm-reboot-retry/org/droidplanner/services/android/impl/utils/CommonApiUtils.java
```

`-g` matters here: the original classes were compiled with full debug info, and
without it the recompiled classes come out smaller (missing local-variable
tables) even though nothing is functionally different — harmless, but confusing
to diff by size.

To ship: swap the resulting `.class` files into `classes.jar` (extract fully,
replace, repack with `jar cf`), then `classes.jar` back into
`dronekit-android-3.0.2.aar` the same way — do not use in-place zip update
tools, they corrupt entry sizes.

Verified: `assembleDevDebug` / `assembleProdRelease` both build with no new
warnings beyond the pre-existing deprecation/unchecked notes.

---

## Authors

Android 12–16 modernization of Tower, including this patch:
**Ramón José Moreno** and **Alejandro Moreno** (2026).

The underlying DroneKit-Android client library and Tower/DroidPlanner are the
work of Arthur Benemann, 3D Robotics and the Tower / DroidPlanner contributors.
This patch is distributed under the same terms — GPLv3.
