# MAVLink 2 patch

Tower 4.0.0 shipped a 2016 build of the MAVLink Java library (inside
`dronekit-android-3.0.2.aar` → `libs/Mavlink.jar`) that only speaks **MAVLink 1**
(`0xFE` frames). Current ArduPilot and PX4 default to **MAVLink 2** (`0xFD`
frames: 2 flag bytes, a 3-byte little-endian message id, optional 13-byte
signature, and payload truncation of trailing zero bytes).

This patch teaches the bundled library to **receive** MAVLink 2 and to
**auto-negotiate** it on transmit, while MAVLink 1 links keep working
unchanged. Two classes are touched:

| File | Class | Change |
|------|-------|--------|
| `Parser.java` | `com.MAVLink.Parser` | rewritten byte-stream state machine with a `0xFD` branch |
| `MAVLinkPacket.java` | `com.MAVLink.MAVLinkPacket` | v2 fields, CRC, framing and a bounds-guarded unpack |

It is applied by recompiling these two `.java` against the original
`Mavlink.jar` and swapping the `.class` files back into the jar / aar. The
result is what ships in `Android/libs/dronekit-android-3.0.2.aar`.

Verified with `MavlinkV2Test.java` (16 assertions) and, in flight, against a
MINI Pix / ArduCopter 4.7.0 over MAVLink 1 and MAVLink 2.

## Build & test

Recompile against the **original** `Mavlink.jar` (put the patched classes ahead
of it on the classpath), then run the test:

```
javac -cp Mavlink.jar -d out patches/mavlink2/Parser.java patches/mavlink2/MAVLinkPacket.java
javac -cp "out:Mavlink.jar" -d out patches/mavlink2/MavlinkV2Test.java
java  -cp "out:Mavlink.jar" MavlinkV2Test        #  ->  16 passed, 0 failed
```

To ship: copy `out/com/MAVLink/*.class` into `Mavlink.jar`, and `Mavlink.jar`
back into `dronekit-android-3.0.2.aar` (extract the aar fully and repack with
`jar cf` — do not use in-place zip update tools).

---

## `Parser`

Adds these parse states after the shared `IDLE`:

```
GOT_STX_V2 → GOT_LENGTH_V2 → GOT_INCOMPAT_V2 → GOT_COMPAT_V2 → GOT_SEQ_V2
           → GOT_SYSID_V2 → GOT_COMPID_V2 → GOT_MSGID1_V2 → GOT_MSGID2_V2
           → GOT_MSGID3_V2 → (payload) → shared CRC tail
```

- `0xFE` still enters the MAVLink 1 path; `0xFD` enters the v2 path.
- v2 header: `len`, `incompat_flags`, `compat_flags`, `seq`, `sysid`, `compid`,
  then the 3-byte little-endian `msgid` (`msgid |= b << 0/8/16`).
- The CRC tail is shared. `generateCRC()` on the packet already knows whether it
  is a v2 packet and folds the two flag bytes + 3-byte id into the checksum.
- **Signature**: if `incompatFlags & MAVLINK_IFLAG_SIGNED`, the next 13 bytes
  are *consumed but not validated* (`GOT_SIGNATURE_V2`), then the packet is
  delivered. Signed links are read; signatures are not checked and nothing is
  signed on transmit.
- **TX auto-negotiation**: `deliver()` sets the static
  `MAVLinkPacket.sendMavlink2 = true` the first time a v2 frame is decoded. A
  fresh `Parser` resets it to `false`, so a new link starts as MAVLink 1 until
  the peer proves it speaks v2.

## `MAVLinkPacket`

New constants / fields:

```java
public static final int MAVLINK_STX_MAVLINK2 = 253;   // 0xFD
public static final int MAVLINK_IFLAG_SIGNED  = 0x01;
public static final int MAX_PAYLOAD_SIZE      = 255;
public static volatile boolean sendMavlink2   = false; // set by Parser.deliver()

public boolean isMavlink2  = false;
public int     incompatFlags = 0;
public int     compatFlags   = 0;
```

- **`MAVLinkPacket(int payloadLength, boolean mavlink2)`** — a v2 packet
  allocates a full **255-byte** payload buffer regardless of the framed `len`,
  because v2 truncates trailing zero bytes on the wire. The unpack helpers then
  read fields back as zero past the framed length instead of throwing.
- **`generateCRC()`** — v2 branch adds `incompat_flags`, `compat_flags` and the
  full 3-byte `msgid` to the checksum; `finish_checksum(msgid)` is bounds-guarded
  so an unknown high v2 msgid drops the frame (bad CRC_EXTRA) rather than
  indexing out of the 2016 CRC_EXTRA table.
- **`encodePacket()`** — emits a `0xFD` frame (`encodePacketMavlink2()`) when the
  packet is a v2 packet *or* `sendMavlink2` is set; otherwise the original
  `0xFE` framing. v2 output uses `incompat_flags = 0` (unsigned).
- The `unpack()` message factory is the stock 2016 dialect and is **unchanged**.
  Messages added to MAVLink after 2016 (e.g. `MISSION_REQUEST_INT`, msgid 51)
  are still unknown and dropped.

---

## The proper long-term fix

This patch is the pragmatic "make v2 work now" version. A clean revival should
**regenerate the whole `com.MAVLink.*` Java binding** from the current
`common.xml` / `ardupilotmega.xml` with the pymavlink Java generator
(`mavgen.py --lang Java`). That gives native MAVLink 2, every current message,
and real signing hooks in place of a patched 2016 dialect.

---

## Authors

Android 12–16 modernization of Tower, including this MAVLink 2 work:
**Ramón José Moreno** and **Alejandro Moreno** (2026).

The underlying MAVLink Java library and Tower/DroidPlanner are the work of
Arthur Benemann, 3D Robotics and the Tower / DroidPlanner contributors. This
patch is distributed under the same terms — GPLv3.
