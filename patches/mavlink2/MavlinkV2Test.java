import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Parser;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.common.msg_heartbeat;
import com.MAVLink.common.msg_global_position_int;

public class MavlinkV2Test {
    static int ok = 0, fail = 0;
    static void check(boolean c, String m){ if(c){ok++; System.out.println("PASS "+m);} else {fail++; System.out.println("FAIL "+m);} }

    public static void main(String[] a) throws Exception {
        // --- 1. Hand-build a MAVLink2 HEARTBEAT (msgid 0, CRC_EXTRA 50) ---
        // payload: type=2(QUADROTOR),autopilot=3(ARDUPILOTMEGA),base_mode=0,custom_mode=0,system_status=4,mavlink_version=3
        // heartbeat wire order (v1 field order): custom_mode(u32), type(u8), autopilot(u8), base_mode(u8), system_status(u8), mavlink_version(u8) = 9 bytes
        byte[] pl = new byte[]{ 0,0,0,0, 2, 3, 0, 4, 3 };
        byte[] frame = buildV2(0, 50, 7, 1, 1, 0, 0, pl);
        Parser p = new Parser();
        MAVLinkPacket pkt = null;
        for (byte b : frame) { MAVLinkPacket r = p.mavlink_parse_char(b & 0xFF); if (r != null) pkt = r; }
        check(pkt != null, "v2 heartbeat parsed");
        check(pkt != null && pkt.isMavlink2, "packet flagged mavlink2");
        check(pkt != null && pkt.msgid == 0, "msgid==0");
        check(MAVLinkPacket.sendMavlink2, "sendMavlink2 latched true after RX v2");
        MAVLinkMessage msg = pkt == null ? null : pkt.unpack();
        check(msg instanceof msg_heartbeat, "unpacked to msg_heartbeat");
        if (msg instanceof msg_heartbeat) {
            msg_heartbeat h = (msg_heartbeat) msg;
            check(h.type == 2 && h.autopilot == 3 && h.system_status == 4 && h.mavlink_version == 3, "heartbeat fields decoded");
        }

        // --- 2. Truncated v2 payload (trailing zeros trimmed): send only 5 bytes ---
        byte[] plTrunc = new byte[]{ 0,0,0,0, 2 };  // rest (autopilot..version) truncated -> zero
        byte[] frame2 = buildV2(0, 50, 7, 1, 1, 0, 5, plTrunc);
        Parser p2 = new Parser();
        MAVLinkPacket pkt2 = null;
        for (byte b : frame2) { MAVLinkPacket r = p2.mavlink_parse_char(b & 0xFF); if (r != null) pkt2 = r; }
        check(pkt2 != null, "truncated v2 heartbeat parsed");
        MAVLinkMessage m2 = pkt2 == null ? null : pkt2.unpack();
        check(m2 instanceof msg_heartbeat && ((msg_heartbeat)m2).type == 2 && ((msg_heartbeat)m2).autopilot == 0,
              "truncated fields read back as zero without exception");

        // --- 3. Signed v2 frame: 13-byte signature must be consumed ---
        byte[] frame3 = buildV2Signed(0, 50, 7, 1, 1, 0, pl);
        Parser p3 = new Parser();
        MAVLinkPacket pkt3 = null;
        for (byte b : frame3) { MAVLinkPacket r = p3.mavlink_parse_char(b & 0xFF); if (r != null) pkt3 = r; }
        check(pkt3 != null && pkt3.msgid == 0, "signed v2 frame parsed, signature skipped");

        // --- 4. v1 frame still works ---
        MAVLinkPacket.sendMavlink2 = false;
        byte[] v1 = buildV1(0, 50, 42, 1, 1, pl);
        Parser p4 = new Parser();
        MAVLinkPacket pkt4 = null;
        for (byte b : v1) { MAVLinkPacket r = p4.mavlink_parse_char(b & 0xFF); if (r != null) pkt4 = r; }
        check(pkt4 != null && !pkt4.isMavlink2 && pkt4.msgid == 0, "v1 heartbeat still parses");

        // --- 5. TX round-trip: encode v2, parse back ---
        msg_heartbeat out = new msg_heartbeat();
        out.type = 2; out.autopilot = 3; out.base_mode = 81; out.custom_mode = 5; out.system_status = 4; out.mavlink_version = 3;
        out.sysid = 255; out.compid = 190;
        MAVLinkPacket enc = out.pack();
        MAVLinkPacket.sendMavlink2 = true;
        byte[] wire = enc.encodePacket();
        check((wire[0] & 0xFF) == 0xFD, "encodePacket emits 0xFD when sendMavlink2");
        Parser p5 = new Parser();
        MAVLinkPacket back = null;
        for (byte b : wire) { MAVLinkPacket r = p5.mavlink_parse_char(b & 0xFF); if (r != null) back = r; }
        check(back != null, "self-encoded v2 frame parses back (CRC ok)");
        msg_heartbeat rb = back == null ? null : (msg_heartbeat) back.unpack();
        check(rb != null && rb.custom_mode == 5 && rb.base_mode == 81 && rb.type == 2, "TX round-trip fields intact");

        // --- 6. v1 TX still correct when sendMavlink2 false ---
        MAVLinkPacket.sendMavlink2 = false;
        MAVLinkPacket enc1 = out.pack();
        byte[] wire1 = enc1.encodePacket();
        check((wire1[0] & 0xFF) == 0xFE, "encodePacket emits 0xFE by default");
        Parser p6 = new Parser();
        MAVLinkPacket back1 = null;
        for (byte b : wire1) { MAVLinkPacket r = p6.mavlink_parse_char(b & 0xFF); if (r != null) back1 = r; }
        check(back1 != null && ((msg_heartbeat)back1.unpack()).custom_mode == 5, "v1 TX round-trip ok");

        // --- 7. unknown high msgid v2 -> dropped, no crash ---
        Parser p7 = new Parser();
        byte[] frameU = buildV2(0x0FFFFF, 0, 7, 1, 1, 0, 0, new byte[]{1,2,3,4});
        MAVLinkPacket pu = null;
        for (byte b : frameU) { MAVLinkPacket r = p7.mavlink_parse_char(b & 0xFF); if (r != null) pu = r; }
        check(pu == null, "unknown high msgid v2 frame dropped (bad CRC_EXTRA), no exception");

        System.out.println("\n" + ok + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }

    static void crcAccum(int[] crc, int b){
        b &= 0xFF;
        int tmp = b ^ (crc[0] & 0xFF);
        tmp ^= (tmp << 4) & 0xFF;
        crc[0] = ((crc[0] >> 8) & 0xFF) ^ (tmp << 8) ^ (tmp << 3) ^ ((tmp >> 4) & 0xF);
        crc[0] &= 0xFFFF;
    }

    static byte[] buildV2(int msgid, int crcExtra, int seq, int sys, int comp, int incompat, int lenOverride, byte[] pl){
        int len = lenOverride > 0 ? lenOverride : pl.length;
        int[] crc = {0xFFFF};
        crcAccum(crc, len);
        crcAccum(crc, incompat);
        crcAccum(crc, 0);
        crcAccum(crc, seq);
        crcAccum(crc, sys);
        crcAccum(crc, comp);
        crcAccum(crc, msgid & 0xFF);
        crcAccum(crc, (msgid >> 8) & 0xFF);
        crcAccum(crc, (msgid >> 16) & 0xFF);
        for (int i = 0; i < len; i++) crcAccum(crc, i < pl.length ? pl[i] : 0);
        crcAccum(crc, crcExtra);
        int sigLen = (incompat & 1) != 0 ? 13 : 0;
        byte[] f = new byte[10 + len + 2 + sigLen];
        int i = 0;
        f[i++] = (byte)0xFD; f[i++] = (byte)len; f[i++] = (byte)incompat; f[i++] = 0;
        f[i++] = (byte)seq; f[i++] = (byte)sys; f[i++] = (byte)comp;
        f[i++] = (byte)(msgid & 0xFF); f[i++] = (byte)((msgid>>8)&0xFF); f[i++] = (byte)((msgid>>16)&0xFF);
        for (int j = 0; j < len; j++) f[i++] = j < pl.length ? pl[j] : 0;
        f[i++] = (byte)(crc[0] & 0xFF); f[i++] = (byte)((crc[0] >> 8) & 0xFF);
        return f;
    }

    static byte[] buildV2Signed(int msgid, int crcExtra, int seq, int sys, int comp, int dummy, byte[] pl){
        return buildV2(msgid, crcExtra, seq, sys, comp, 1, 0, pl);
    }

    static byte[] buildV1(int msgid, int crcExtra, int seq, int sys, int comp, byte[] pl){
        int len = pl.length;
        int[] crc = {0xFFFF};
        crcAccum(crc, len); crcAccum(crc, seq); crcAccum(crc, sys); crcAccum(crc, comp); crcAccum(crc, msgid);
        for (byte b : pl) crcAccum(crc, b);
        crcAccum(crc, crcExtra);
        byte[] f = new byte[6 + len + 2];
        int i = 0;
        f[i++] = (byte)0xFE; f[i++] = (byte)len; f[i++] = (byte)seq; f[i++] = (byte)sys; f[i++] = (byte)comp; f[i++] = (byte)msgid;
        for (byte b : pl) f[i++] = b;
        f[i++] = (byte)(crc[0] & 0xFF); f[i++] = (byte)((crc[0] >> 8) & 0xFF);
        return f;
    }
}
