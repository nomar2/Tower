/*
 * Patched from the DroneKit-Android 3.0.2 WaypointManager.
 *
 * Upload path now sends msg_mission_item_int (MISSION_ITEM_INT, msg 73)
 * instead of msg_mission_item (MISSION_ITEM, msg 39), converting the
 * float lat/lon into MISSION_ITEM_INT's fixed-point int (degrees * 1e7).
 * ArduPilot 4.7+ logs "GCS should send MISSION_ITEM_INT" for the old
 * format; it still works, but with less coordinate precision. The
 * internal mission list stays List<msg_mission_item> (unchanged app-
 * facing API) - the conversion happens right before each item goes on
 * the wire, in toMissionItemInt() / processWaypointToSend() and in the
 * WRITING_WP / WAITING_WRITE_ACK retry paths of processTimeOut().
 * Download and every other message type are unchanged.
 *
 * Modifications 2026 by Ramon Jose Moreno and Alejandro Moreno.
 */
package org.droidplanner.services.android.impl.core.MAVLink;

import android.os.Handler;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.common.msg_mission_ack;
import com.MAVLink.common.msg_mission_count;
import com.MAVLink.common.msg_mission_current;
import com.MAVLink.common.msg_mission_item;
import com.MAVLink.common.msg_mission_item_int;
import com.MAVLink.common.msg_mission_item_reached;
import com.MAVLink.common.msg_mission_request;
import java.util.ArrayList;
import java.util.List;
import org.droidplanner.services.android.impl.core.MAVLink.MavLinkWaypoint;
import org.droidplanner.services.android.impl.core.drone.DroneInterfaces;
import org.droidplanner.services.android.impl.core.drone.DroneVariable;
import org.droidplanner.services.android.impl.core.drone.autopilot.MavLinkDrone;

public class WaypointManager
extends DroneVariable {
    private static final long TIMEOUT = 3000L;
    private static final int RETRY_LIMIT = 6;
    private int retryTracker = 0;
    private int readIndex;
    private int writeIndex;
    private int retryIndex;
    private DroneInterfaces.OnWaypointManagerListener wpEventListener;
    WaypointStates state = WaypointStates.IDLE;
    private final Handler watchdog;
    private final Runnable watchdogCallback = new Runnable(){

        @Override
        public void run() {
            if (WaypointManager.this.processTimeOut(++WaypointManager.this.retryTracker)) {
                WaypointManager.this.watchdog.postDelayed((Runnable)this, 3000L);
            }
        }
    };
    private int waypointCount;
    private List<msg_mission_item> mission = new ArrayList<msg_mission_item>();

    public WaypointManager(MavLinkDrone mavLinkDrone, Handler handler) {
        super(mavLinkDrone);
        this.watchdog = handler;
    }

    public void setWaypointManagerListener(DroneInterfaces.OnWaypointManagerListener onWaypointManagerListener) {
        this.wpEventListener = onWaypointManagerListener;
    }

    private void startWatchdog() {
        this.stopWatchdog();
        this.retryTracker = 0;
        this.watchdog.postDelayed(this.watchdogCallback, 3000L);
    }

    private void stopWatchdog() {
        this.watchdog.removeCallbacks(this.watchdogCallback);
    }

    public void getWaypoints() {
        if (this.state != WaypointStates.IDLE) {
            return;
        }
        this.doBeginWaypointEvent(WaypointEvent_Type.WP_DOWNLOAD);
        this.readIndex = -1;
        this.state = WaypointStates.READ_REQUEST;
        MavLinkWaypoint.requestWaypointsList(this.myDrone);
        this.startWatchdog();
    }

    public void writeWaypoints(List<msg_mission_item> list) {
        if (this.state != WaypointStates.IDLE) {
            return;
        }
        if (this.mission != null) {
            this.doBeginWaypointEvent(WaypointEvent_Type.WP_UPLOAD);
            this.mission.clear();
            this.mission.addAll(list);
            this.writeIndex = 0;
            this.state = WaypointStates.WRITING_WP_COUNT;
            MavLinkWaypoint.sendWaypointCount(this.myDrone, this.mission.size());
            this.startWatchdog();
        }
    }

    public void setCurrentWaypoint(int n) {
        if (this.mission != null) {
            MavLinkWaypoint.sendSetCurrentWaypoint(this.myDrone, (short)n);
        }
    }

    public void onWaypointReached(int n) {
    }

    private void onCurrentWaypointUpdate(int n) {
    }

    /**
     * Converts a float-precision MISSION_ITEM into the fixed-point
     * MISSION_ITEM_INT that ArduPilot 4.7+ expects on upload. Every field
     * is copied as-is except x/y (lat/lon), which go from degrees as a
     * float to degrees*1e7 as an int - MISSION_ITEM_INT's native format.
     */
    private static msg_mission_item_int toMissionItemInt(msg_mission_item msg_mission_item2) {
        msg_mission_item_int msg_mission_item_int2 = new msg_mission_item_int();
        msg_mission_item_int2.target_system = msg_mission_item2.target_system;
        msg_mission_item_int2.target_component = msg_mission_item2.target_component;
        msg_mission_item_int2.seq = msg_mission_item2.seq;
        msg_mission_item_int2.frame = msg_mission_item2.frame;
        msg_mission_item_int2.command = msg_mission_item2.command;
        msg_mission_item_int2.current = msg_mission_item2.current;
        msg_mission_item_int2.autocontinue = msg_mission_item2.autocontinue;
        msg_mission_item_int2.param1 = msg_mission_item2.param1;
        msg_mission_item_int2.param2 = msg_mission_item2.param2;
        msg_mission_item_int2.param3 = msg_mission_item2.param3;
        msg_mission_item_int2.param4 = msg_mission_item2.param4;
        msg_mission_item_int2.x = Math.round(msg_mission_item2.x * 1.0E7f);
        msg_mission_item_int2.y = Math.round(msg_mission_item2.y * 1.0E7f);
        msg_mission_item_int2.z = msg_mission_item2.z;
        return msg_mission_item_int2;
    }

    public boolean processMessage(MAVLinkMessage mAVLinkMessage) {
        switch (this.state) {
            default: {
                break;
            }
            case READ_REQUEST: {
                if (mAVLinkMessage.msgid != 44) break;
                this.waypointCount = ((msg_mission_count)mAVLinkMessage).count;
                this.mission.clear();
                this.startWatchdog();
                MavLinkWaypoint.requestWayPoint(this.myDrone, this.mission.size());
                this.state = WaypointStates.READING_WP;
                return true;
            }
            case READING_WP: {
                if (mAVLinkMessage.msgid != 39) break;
                this.startWatchdog();
                this.processReceivedWaypoint((msg_mission_item)mAVLinkMessage);
                this.doWaypointEvent(WaypointEvent_Type.WP_DOWNLOAD, this.readIndex + 1, this.waypointCount);
                if (this.mission.size() < this.waypointCount) {
                    MavLinkWaypoint.requestWayPoint(this.myDrone, this.mission.size());
                } else {
                    this.stopWatchdog();
                    this.state = WaypointStates.IDLE;
                    MavLinkWaypoint.sendAck(this.myDrone);
                    this.myDrone.getMission().onMissionReceived(this.mission);
                    this.doEndWaypointEvent(WaypointEvent_Type.WP_DOWNLOAD);
                }
                return true;
            }
            case WRITING_WP_COUNT: {
                this.state = WaypointStates.WRITING_WP;
            }
            case WRITING_WP: {
                if (mAVLinkMessage.msgid != 40) break;
                this.startWatchdog();
                this.processWaypointToSend((msg_mission_request)mAVLinkMessage);
                this.doWaypointEvent(WaypointEvent_Type.WP_UPLOAD, this.writeIndex + 1, this.mission.size());
                return true;
            }
            case WAITING_WRITE_ACK: {
                if (mAVLinkMessage.msgid != 47) break;
                this.stopWatchdog();
                this.myDrone.getMission().onWriteWaypoints((msg_mission_ack)mAVLinkMessage);
                this.state = WaypointStates.IDLE;
                this.doEndWaypointEvent(WaypointEvent_Type.WP_UPLOAD);
                return true;
            }
        }
        if (mAVLinkMessage.msgid == 46) {
            this.onWaypointReached(((msg_mission_item_reached)mAVLinkMessage).seq);
            return true;
        }
        if (mAVLinkMessage.msgid == 42) {
            this.onCurrentWaypointUpdate(((msg_mission_current)mAVLinkMessage).seq);
            return true;
        }
        return false;
    }

    public boolean processTimeOut(int n) {
        if (n >= 6) {
            this.state = WaypointStates.IDLE;
            this.doWaypointEvent(WaypointEvent_Type.WP_TIMED_OUT, this.retryIndex, 6);
            return false;
        }
        ++this.retryIndex;
        this.doWaypointEvent(WaypointEvent_Type.WP_RETRY, this.retryIndex, 6);
        switch (this.state) {
            default: {
                break;
            }
            case READ_REQUEST: {
                MavLinkWaypoint.requestWaypointsList(this.myDrone);
                break;
            }
            case READING_WP: {
                if (this.mission.size() >= this.waypointCount) break;
                MavLinkWaypoint.requestWayPoint(this.myDrone, this.mission.size());
                break;
            }
            case WRITING_WP_COUNT: {
                MavLinkWaypoint.sendWaypointCount(this.myDrone, this.mission.size());
                break;
            }
            case WRITING_WP: {
                if (this.writeIndex >= this.mission.size()) break;
                this.myDrone.getMavClient().sendMessage((MAVLinkMessage)toMissionItemInt(this.mission.get(this.writeIndex)), null);
                break;
            }
            case WAITING_WRITE_ACK: {
                this.myDrone.getMavClient().sendMessage((MAVLinkMessage)toMissionItemInt(this.mission.get(this.mission.size() - 1)), null);
            }
        }
        return true;
    }

    private void processWaypointToSend(msg_mission_request msg_mission_request2) {
        this.writeIndex = msg_mission_request2.seq;
        msg_mission_item msg_mission_item2 = this.mission.get(this.writeIndex);
        msg_mission_item2.target_system = this.myDrone.getSysid();
        msg_mission_item2.target_component = this.myDrone.getCompid();
        this.myDrone.getMavClient().sendMessage((MAVLinkMessage)toMissionItemInt(msg_mission_item2), null);
        if (this.writeIndex + 1 >= this.mission.size()) {
            this.state = WaypointStates.WAITING_WRITE_ACK;
        }
    }

    private void processReceivedWaypoint(msg_mission_item msg_mission_item2) {
        if (msg_mission_item2.seq <= this.readIndex) {
            return;
        }
        this.readIndex = msg_mission_item2.seq;
        this.mission.add(msg_mission_item2);
    }

    private void doBeginWaypointEvent(WaypointEvent_Type waypointEvent_Type) {
        this.retryIndex = 0;
        if (this.wpEventListener == null) {
            return;
        }
        this.wpEventListener.onBeginWaypointEvent(waypointEvent_Type);
    }

    private void doEndWaypointEvent(WaypointEvent_Type waypointEvent_Type) {
        if (this.retryIndex > 0) {
            this.doWaypointEvent(WaypointEvent_Type.WP_CONTINUE, this.retryIndex, 6);
        }
        this.retryIndex = 0;
        if (this.wpEventListener == null) {
            return;
        }
        this.wpEventListener.onEndWaypointEvent(waypointEvent_Type);
    }

    private void doWaypointEvent(WaypointEvent_Type waypointEvent_Type, int n, int n2) {
        this.retryIndex = 0;
        if (this.wpEventListener == null) {
            return;
        }
        this.wpEventListener.onWaypointEvent(waypointEvent_Type, n, n2);
    }

    static enum WaypointStates {
        IDLE,
        READ_REQUEST,
        READING_WP,
        WRITING_WP_COUNT,
        WRITING_WP,
        WAITING_WRITE_ACK;

    }

    public static enum WaypointEvent_Type {
        WP_UPLOAD,
        WP_DOWNLOAD,
        WP_RETRY,
        WP_CONTINUE,
        WP_TIMED_OUT;

    }
}
