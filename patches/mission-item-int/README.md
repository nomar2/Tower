# MISSION_ITEM_INT patch

Tower 4.0.0's bundled `WaypointManager` (inside `dronekit-android-3.0.2.aar`)
uploads each waypoint as **`MISSION_ITEM`** (msg 39) — a 2010-era message whose
latitude/longitude are a `float`. ArduPilot 4.7+ still accepts it, but logs
`GCS should send MISSION_ITEM_INT`, since **`MISSION_ITEM_INT`** (msg 73)
carries lat/lon as a fixed-point `int` (degrees × 1e7) and never loses
precision the way a 32-bit float can at global coordinate magnitudes.

| File | Class | Change |
|------|-------|--------|
| `WaypointManager.java` | `org.droidplanner.services.android.impl.core.MAVLink.WaypointManager` | upload path now sends `msg_mission_item_int` instead of `msg_mission_item` |

`msg_mission_item_int` already existed in the vendored `Mavlink.jar` (it
predates the 2016 dialect this project is built on), so unlike the MAVLink 2
patch this needed **no binding regeneration and no CRC table change** — it's a
straight recompile of one class.

## What changed

The internal mission list stays `List<msg_mission_item>` — nothing about the
app-facing API (`writeWaypoints()`, `MissionApi`, `MissionProxy`, …) changed.
The conversion happens right before an item goes on the wire, in a new
`toMissionItemInt()` helper, called from:

- `processWaypointToSend()` — the normal upload path, triggered by each
  `MISSION_REQUEST` from the vehicle.
- `processTimeOut()`'s `WRITING_WP` / `WAITING_WRITE_ACK` branches — the
  existing retry-on-timeout path, so a retried item is also sent as
  `MISSION_ITEM_INT`, not a fallback to the old float format.

```java
private static msg_mission_item_int toMissionItemInt(msg_mission_item item) {
    msg_mission_item_int out = new msg_mission_item_int();
    // ... same fields copied as-is ...
    out.x = Math.round(item.x * 1.0E7f);   // lat: float degrees -> int * 1e7
    out.y = Math.round(item.y * 1.0E7f);   // lon: float degrees -> int * 1e7
    out.z = item.z;                        // altitude stays a float in both
    return out;
}
```

Download, the mission-count/ack handshake, the 3 s / 6-retry watchdog, and
every other message type in `WaypointManager` are unchanged.

## Not covered

**`MISSION_REQUEST_INT`** (msg 51) — the receive-side counterpart, which some
vehicles/GCS use to *request* an item in the INT format — is missing from this
project's 2016 dialect entirely (unlike `MISSION_ITEM_INT`, it wasn't in the
jar yet). Supporting it would need a new generated message class and a CRC
table entry, i.e. the same class of work as the MAVLink 2 patch. ArduPilot
requests items with plain `MISSION_REQUEST` (msg 40) regardless of which
`MISSION_ITEM` variant the GCS sends back, so this gap doesn't block the fix
above.

## Build & test

Recompile against the **original** `classes.jar` and `Mavlink.jar` (both from
the aar):

```
javac -source 8 -target 8 \
  -cp "classes.jar;Mavlink.jar;<android-sdk>/platforms/android-36/android.jar" \
  -d out patches/mission-item-int/WaypointManager.java
```

To ship: swap the resulting `WaypointManager*.class` files into `classes.jar`
(extract fully, replace, repack with `jar cf`), then `classes.jar` back into
`dronekit-android-3.0.2.aar` the same way — do not use in-place zip update
tools, they corrupt entry sizes.

Verified: `assembleDevDebug` / `assembleProdRelease` both build; the built
APK's `classes.dex` references `msg_mission_item_int` alongside the existing
`msg_mission_item` and `msg_mission_item_reached`.

---

## Authors

Android 12–16 modernization of Tower, including this patch:
**Ramón José Moreno** and **Alejandro Moreno** (2026).

The underlying MAVLink Java library and Tower/DroidPlanner are the work of
Arthur Benemann, 3D Robotics and the Tower / DroidPlanner contributors. This
patch is distributed under the same terms — GPLv3.
