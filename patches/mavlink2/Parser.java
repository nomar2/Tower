package com.MAVLink;

import com.MAVLink.MAVLinkPacket;
import com.MAVLink.Messages.MAVLinkStats;

/**
 * MAVLink byte-stream parser.
 *
 * Patched for the Tower Android-16 migration to understand MAVLink 2 frames
 * (STX 0xFD, 2 flag bytes, 3-byte little-endian message id, optional 13-byte
 * signature) in addition to the original MAVLink 1 frames (STX 0xFE).
 */
public class Parser {

    MAV_states state = MAV_states.MAVLINK_PARSE_STATE_UNINIT;
    private boolean msg_received;
    public MAVLinkStats stats = new MAVLinkStats();
    private MAVLinkPacket m;
    private int signatureBytesRemaining;

    public Parser() {
        // A fresh parser means a fresh link: start assuming MAVLink 1 for TX
        // until the peer proves it speaks v2.
        MAVLinkPacket.sendMavlink2 = false;
    }

    public MAVLinkPacket mavlink_parse_char(int c) {
        this.msg_received = false;
        switch (this.state) {
            case MAVLINK_PARSE_STATE_UNINIT:
            case MAVLINK_PARSE_STATE_IDLE: {
                if (c == MAVLinkPacket.MAVLINK_STX) {
                    this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_STX;
                } else if (c == MAVLinkPacket.MAVLINK_STX_MAVLINK2) {
                    this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_STX_V2;
                }
                break;
            }

            /* ---------------- MAVLink 1 ---------------- */
            case MAVLINK_PARSE_STATE_GOT_STX: {
                this.m = new MAVLinkPacket(c);
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_LENGTH;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_LENGTH: {
                this.m.seq = c;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_SEQ;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_SEQ: {
                this.m.sysid = c;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_SYSID;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_SYSID: {
                this.m.compid = c;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_COMPID;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_COMPID: {
                this.m.msgid = c;
                if (this.m.len == 0) {
                    this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_PAYLOAD;
                } else {
                    this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_MSGID;
                }
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_MSGID: {
                this.m.payload.add((byte) c);
                if (this.m.payloadIsFilled()) {
                    this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_PAYLOAD;
                }
                break;
            }

            /* ---------------- MAVLink 2 ---------------- */
            case MAVLINK_PARSE_STATE_GOT_STX_V2: {
                this.m = new MAVLinkPacket(c, true);
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_LENGTH_V2;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_LENGTH_V2: {
                this.m.incompatFlags = c;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_INCOMPAT_V2;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_INCOMPAT_V2: {
                this.m.compatFlags = c;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_COMPAT_V2;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_COMPAT_V2: {
                this.m.seq = c;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_SEQ_V2;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_SEQ_V2: {
                this.m.sysid = c;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_SYSID_V2;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_SYSID_V2: {
                this.m.compid = c;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_COMPID_V2;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_COMPID_V2: {
                this.m.msgid = c & 0xFF;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_MSGID1_V2;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_MSGID1_V2: {
                this.m.msgid |= (c & 0xFF) << 8;
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_MSGID2_V2;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_MSGID2_V2: {
                this.m.msgid |= (c & 0xFF) << 16;
                if (this.m.len == 0) {
                    this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_PAYLOAD;
                } else {
                    this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_MSGID3_V2;
                }
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_MSGID3_V2: {
                this.m.payload.add((byte) c);
                if (this.m.payloadIsFilled()) {
                    this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_PAYLOAD;
                }
                break;
            }

            /* ---------------- shared tail ---------------- */
            case MAVLINK_PARSE_STATE_GOT_PAYLOAD: {
                this.m.generateCRC();
                if (c != this.m.crc.getLSB()) {
                    this.msg_received = false;
                    this.state = MAV_states.MAVLINK_PARSE_STATE_IDLE;
                    if (c == MAVLinkPacket.MAVLINK_STX) {
                        this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_STX;
                        this.m.crc.start_checksum();
                    } else if (c == MAVLinkPacket.MAVLINK_STX_MAVLINK2) {
                        this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_STX_V2;
                        this.m.crc.start_checksum();
                    }
                    this.stats.crcError();
                    break;
                }
                this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_CRC1;
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_CRC1: {
                if (c != this.m.crc.getMSB()) {
                    this.msg_received = false;
                    this.state = MAV_states.MAVLINK_PARSE_STATE_IDLE;
                    if (c == MAVLinkPacket.MAVLINK_STX) {
                        this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_STX;
                        this.m.crc.start_checksum();
                    } else if (c == MAVLinkPacket.MAVLINK_STX_MAVLINK2) {
                        this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_STX_V2;
                        this.m.crc.start_checksum();
                    }
                    this.stats.crcError();
                    break;
                }
                if (this.m.isMavlink2 && (this.m.incompatFlags & MAVLinkPacket.MAVLINK_IFLAG_SIGNED) != 0) {
                    // Consume (but do not validate) the 13-byte signature block.
                    this.signatureBytesRemaining = 13;
                    this.state = MAV_states.MAVLINK_PARSE_STATE_GOT_SIGNATURE_V2;
                    break;
                }
                deliver();
                break;
            }
            case MAVLINK_PARSE_STATE_GOT_SIGNATURE_V2: {
                if (--this.signatureBytesRemaining <= 0) {
                    deliver();
                }
                break;
            }
        }

        if (this.msg_received) {
            return this.m;
        }
        return null;
    }

    private void deliver() {
        this.stats.newPacket(this.m);
        if (this.m.isMavlink2) {
            // The peer speaks MAVLink 2: frame our outgoing packets as v2 too.
            MAVLinkPacket.sendMavlink2 = true;
        }
        this.msg_received = true;
        this.state = MAV_states.MAVLINK_PARSE_STATE_IDLE;
    }

    static enum MAV_states {
        MAVLINK_PARSE_STATE_UNINIT,
        MAVLINK_PARSE_STATE_IDLE,
        MAVLINK_PARSE_STATE_GOT_STX,
        MAVLINK_PARSE_STATE_GOT_LENGTH,
        MAVLINK_PARSE_STATE_GOT_SEQ,
        MAVLINK_PARSE_STATE_GOT_SYSID,
        MAVLINK_PARSE_STATE_GOT_COMPID,
        MAVLINK_PARSE_STATE_GOT_MSGID,
        MAVLINK_PARSE_STATE_GOT_CRC1,
        MAVLINK_PARSE_STATE_GOT_PAYLOAD,
        MAVLINK_PARSE_STATE_GOT_STX_V2,
        MAVLINK_PARSE_STATE_GOT_LENGTH_V2,
        MAVLINK_PARSE_STATE_GOT_INCOMPAT_V2,
        MAVLINK_PARSE_STATE_GOT_COMPAT_V2,
        MAVLINK_PARSE_STATE_GOT_SEQ_V2,
        MAVLINK_PARSE_STATE_GOT_SYSID_V2,
        MAVLINK_PARSE_STATE_GOT_COMPID_V2,
        MAVLINK_PARSE_STATE_GOT_MSGID1_V2,
        MAVLINK_PARSE_STATE_GOT_MSGID2_V2,
        MAVLINK_PARSE_STATE_GOT_MSGID3_V2,
        MAVLINK_PARSE_STATE_GOT_SIGNATURE_V2;
    }
}
