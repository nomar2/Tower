/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 *  android.os.Bundle
 *  android.os.Handler
 *  android.text.TextUtils
 *  android.view.Surface
 *  com.MAVLink.Messages.MAVLinkMessage
 *  com.MAVLink.ardupilotmega.msg_ekf_status_report
 *  com.MAVLink.common.msg_attitude
 *  com.MAVLink.common.msg_global_position_int
 *  com.MAVLink.common.msg_gps_raw_int
 *  com.MAVLink.common.msg_heartbeat
 *  com.MAVLink.common.msg_mission_current
 *  com.MAVLink.common.msg_mission_item
 *  com.MAVLink.common.msg_mission_item_reached
 *  com.MAVLink.common.msg_nav_controller_output
 *  com.MAVLink.common.msg_radio_status
 *  com.MAVLink.common.msg_sys_status
 *  com.MAVLink.common.msg_vibration
 */
package org.droidplanner.services.android.impl.core.drone.autopilot.generic;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Surface;
import com.MAVLink.Messages.MAVLinkMessage;
import com.MAVLink.ardupilotmega.msg_ekf_status_report;
import com.MAVLink.common.msg_attitude;
import com.MAVLink.common.msg_global_position_int;
import com.MAVLink.common.msg_gps_raw_int;
import com.MAVLink.common.msg_heartbeat;
import com.MAVLink.common.msg_mission_current;
import com.MAVLink.common.msg_mission_item;
import com.MAVLink.common.msg_mission_item_reached;
import com.MAVLink.common.msg_nav_controller_output;
import com.MAVLink.common.msg_radio_status;
import com.MAVLink.common.msg_sys_status;
import com.MAVLink.common.msg_vibration;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.DroneAttribute;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Parameter;
import com.o3dr.services.android.lib.drone.property.Parameters;
import com.o3dr.services.android.lib.drone.property.Signal;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.drone.property.Vibration;
import com.o3dr.services.android.lib.mavlink.MavlinkMessageWrapper;
import com.o3dr.services.android.lib.model.ICommandListener;
import com.o3dr.services.android.lib.model.action.Action;
import com.o3dr.services.android.lib.util.MathUtils;
import org.droidplanner.services.android.impl.communication.model.DataLink;
import org.droidplanner.services.android.impl.core.MAVLink.MavLinkCommands;
import org.droidplanner.services.android.impl.core.MAVLink.MavLinkWaypoint;
import org.droidplanner.services.android.impl.core.MAVLink.WaypointManager;
import org.droidplanner.services.android.impl.core.drone.DroneEvents;
import org.droidplanner.services.android.impl.core.drone.DroneInterfaces;
import org.droidplanner.services.android.impl.core.drone.LogMessageListener;
import org.droidplanner.services.android.impl.core.drone.autopilot.MavLinkDrone;
import org.droidplanner.services.android.impl.core.drone.profiles.ParameterManager;
import org.droidplanner.services.android.impl.core.drone.variables.ApmModes;
import org.droidplanner.services.android.impl.core.drone.variables.Camera;
import org.droidplanner.services.android.impl.core.drone.variables.GuidedPoint;
import org.droidplanner.services.android.impl.core.drone.variables.HeartBeat;
import org.droidplanner.services.android.impl.core.drone.variables.MissionStats;
import org.droidplanner.services.android.impl.core.drone.variables.State;
import org.droidplanner.services.android.impl.core.drone.variables.StreamRates;
import org.droidplanner.services.android.impl.core.drone.variables.Type;
import org.droidplanner.services.android.impl.core.drone.variables.calibration.AccelCalibration;
import org.droidplanner.services.android.impl.core.drone.variables.calibration.MagnetometerCalibrationImpl;
import org.droidplanner.services.android.impl.core.firmware.FirmwareType;
import org.droidplanner.services.android.impl.core.mission.MissionImpl;
import org.droidplanner.services.android.impl.core.model.AutopilotWarningParser;
import org.droidplanner.services.android.impl.utils.CommonApiUtils;
import org.droidplanner.services.android.impl.utils.video.VideoManager;

public class GenericMavLinkDrone
implements MavLinkDrone {
    private final DataLink.DataLinkProvider<MAVLinkMessage> mavClient;
    protected final VideoManager videoMgr;
    private final DroneEvents events;
    protected final Type type;
    private final State state;
    private final HeartBeat heartbeat;
    private final StreamRates streamRates;
    private final ParameterManager parameterManager;
    private final LogMessageListener logListener;
    private final MissionStats missionStats;
    private DroneInterfaces.AttributeEventListener attributeListener;
    private final Home vehicleHome = new Home();
    private final Gps vehicleGps = new Gps();
    private final Parameters parameters = new Parameters();
    protected final Altitude altitude = new Altitude();
    protected final Speed speed = new Speed();
    protected final Battery battery = new Battery();
    protected final Signal signal = new Signal();
    protected final Attitude attitude = new Attitude();
    protected final Vibration vibration = new Vibration();
    protected final Handler handler;
    private final String droneId;

    public GenericMavLinkDrone(String droneId, Context context, Handler handler, DataLink.DataLinkProvider<MAVLinkMessage> mavClient, AutopilotWarningParser warningParser, LogMessageListener logListener) {
        this.droneId = droneId;
        this.handler = handler;
        this.mavClient = mavClient;
        this.logListener = logListener;
        this.events = new DroneEvents(this);
        this.heartbeat = this.initHeartBeat(handler);
        this.type = new Type(this);
        this.missionStats = new MissionStats(this);
        this.streamRates = new StreamRates(this);
        this.state = new State(this, handler, warningParser);
        this.parameterManager = new ParameterManager(this, context, handler);
        this.videoMgr = new VideoManager(context, handler, mavClient);
    }

    @Override
    public String getId() {
        return this.droneId;
    }

    @Override
    public void setAttributeListener(DroneInterfaces.AttributeEventListener attributeListener) {
        this.attributeListener = attributeListener;
    }

    @Override
    public MissionStats getMissionStats() {
        return this.missionStats;
    }

    @Override
    public MissionImpl getMission() {
        return null;
    }

    @Override
    public Camera getCamera() {
        return null;
    }

    @Override
    public GuidedPoint getGuidedPoint() {
        return null;
    }

    @Override
    public AccelCalibration getCalibrationSetup() {
        return null;
    }

    @Override
    public WaypointManager getWaypointManager() {
        return null;
    }

    @Override
    public MagnetometerCalibrationImpl getMagnetometerCalibration() {
        return null;
    }

    @Override
    public void destroy() {
        MagnetometerCalibrationImpl magnetometer;
        ParameterManager parameterManager = this.getParameterManager();
        if (parameterManager != null) {
            parameterManager.setParameterListener(null);
        }
        if ((magnetometer = this.getMagnetometerCalibration()) != null) {
            magnetometer.setListener(null);
        }
    }

    protected HeartBeat initHeartBeat(Handler handler) {
        return new HeartBeat(this, handler);
    }

    @Override
    public FirmwareType getFirmwareType() {
        return FirmwareType.GENERIC;
    }

    @Override
    public String getFirmwareVersion() {
        return this.type.getFirmwareVersion();
    }

    protected void setFirmwareVersion(String message) {
        this.type.setFirmwareVersion(message);
    }

    @Override
    public ParameterManager getParameterManager() {
        return this.parameterManager;
    }

    protected void logMessage(int logLevel, String message) {
        if (this.logListener != null) {
            this.logListener.onMessageLogged(logLevel, message);
        }
    }

    @Override
    public State getState() {
        return this.state;
    }

    @Override
    public boolean isConnected() {
        return this.mavClient.isConnected() && this.heartbeat.hasHeartbeat();
    }

    @Override
    public boolean isConnectionAlive() {
        return this.heartbeat.isConnectionAlive();
    }

    @Override
    public short getSysid() {
        return this.heartbeat.getSysid();
    }

    @Override
    public short getCompid() {
        return this.heartbeat.getCompid();
    }

    @Override
    public int getMavlinkVersion() {
        return this.heartbeat.getMavlinkVersion();
    }

    @Override
    public void addDroneListener(DroneInterfaces.OnDroneListener listener) {
        this.events.addDroneListener(listener);
    }

    @Override
    public StreamRates getStreamRates() {
        return this.streamRates;
    }

    @Override
    public void removeDroneListener(DroneInterfaces.OnDroneListener listener) {
        this.events.removeDroneListener(listener);
    }

    public void startVideoStream(Bundle videoProps, String appId, String newVideoTag, Surface videoSurface, ICommandListener listener) {
        this.videoMgr.startVideoStream(videoProps, appId, newVideoTag, videoSurface, listener);
    }

    public void stopVideoStream(String appId, String currentVideoTag, ICommandListener listener) {
        this.videoMgr.stopVideoStream(appId, currentVideoTag, listener);
    }

    public void startVideoStreamForObserver(String appId, String newVideoTag, ICommandListener listener) {
        this.videoMgr.startVideoStreamForObserver(appId, newVideoTag, listener);
    }

    public void stopVideoStreamForObserver(String appId, String currentVideoTag, ICommandListener listener) {
        this.videoMgr.stopVideoStreamForObserver(appId, currentVideoTag, listener);
    }

    public void tryStoppingVideoStream(String appId) {
        this.videoMgr.tryStoppingVideoStream(appId);
    }

    protected void notifyAttributeListener(String attributeEvent) {
        this.notifyAttributeListener(attributeEvent, null);
    }

    protected void notifyAttributeListener(String attributeEvent, Bundle eventInfo) {
        if (this.attributeListener != null) {
            if (eventInfo == null) {
                eventInfo = new Bundle();
            }
            eventInfo.putString("com.o3dr.services.android.lib.attribute.event.extra.VEHICLE_ID", this.getId());
            this.attributeListener.onAttributeEvent(attributeEvent, eventInfo);
        }
    }

    @Override
    public void notifyDroneEvent(DroneInterfaces.DroneEventsType event) {
        switch (event) {
            case DISCONNECTED: {
                this.signal.setValid(false);
            }
        }
        this.events.notifyDroneEvent(event);
    }

    @Override
    public DataLink.DataLinkProvider<MAVLinkMessage> getMavClient() {
        return this.mavClient;
    }

    @Override
    public boolean executeAsyncAction(Action action, ICommandListener listener) {
        String type = action.getType();
        Bundle data = action.getData();
        switch (type) {
            case "com.o3dr.services.android.action.GENERATE_DRONIE": {
                float bearing = CommonApiUtils.generateDronie(this);
                if (bearing != -1.0f) {
                    Bundle bundle = new Bundle(1);
                    bundle.putFloat("com.o3dr.services.android.lib.attribute.event.extra.MISSION_DRONIE_BEARING", bearing);
                    this.notifyAttributeListener("com.o3dr.services.android.lib.attribute.event.MISSION_DRONIE_CREATED", bundle);
                }
                return true;
            }
            case "com.o3dr.services.android.action.GOTO_WAYPOINT": {
                int missionItemIndex = data.getInt("extra_mission_item_index");
                CommonApiUtils.gotoWaypoint(this, missionItemIndex, listener);
                return true;
            }
            case "com.o3dr.services.android.action.CHANGE_MISSION_SPEED": {
                float missionSpeed = data.getFloat("extra_mission_speed");
                MavLinkCommands.changeMissionSpeed(this, missionSpeed, listener);
                return true;
            }
            case "com.o3dr.services.android.action.ARM": {
                return this.performArming(data, listener);
            }
            case "com.o3dr.services.android.action.SET_VEHICLE_MODE": {
                return this.setVehicleMode(data, listener);
            }
            case "com.o3dr.services.android.lib.drone.action.state.action.UPDATE_VEHICLE_DATA_STREAM_RATE": {
                return this.updateVehicleDataStreamRate(data, listener);
            }
            case "com.o3dr.services.android.action.DO_GUIDED_TAKEOFF": {
                return this.performTakeoff(data, listener);
            }
            case "com.o3dr.services.android.lib.drone.action.control.action.SEND_BRAKE_VEHICLE": {
                return this.brakeVehicle(listener);
            }
            case "com.o3dr.services.android.lib.drone.action.control.SET_CONDITION_YAW": {
                Parameter turnSpeedParam;
                float turnSpeed = 2.0f;
                ParameterManager parameterManager = this.getParameterManager();
                if (parameterManager != null && (turnSpeedParam = parameterManager.getParameter("ACRO_YAW_P")) != null) {
                    turnSpeed = (float)turnSpeedParam.getValue();
                }
                float targetAngle = data.getFloat("extra_yaw_target_angle");
                float yawRate = data.getFloat("extra_yaw_change_rate");
                boolean isClockwise = yawRate >= 0.0f;
                boolean isRelative = data.getBoolean("extra_yaw_is_relative");
                MavLinkCommands.setConditionYaw(this, targetAngle, Math.abs(yawRate) * turnSpeed, isClockwise, isRelative, listener);
                return true;
            }
            case "com.o3dr.services.android.lib.drone.action.control.SET_VELOCITY": {
                return this.setVelocity(data, listener);
            }
            case "com.o3dr.services.android.lib.drone.action.control.ENABLE_MANUAL_CONTROL": {
                return this.enableManualControl(data, listener);
            }
            case "com.o3dr.services.android.action.SEND_MAVLINK_MESSAGE": {
                data.setClassLoader(MavlinkMessageWrapper.class.getClassLoader());
                MavlinkMessageWrapper messageWrapper = (MavlinkMessageWrapper)data.getParcelable("extra_mavlink_message");
                CommonApiUtils.sendMavlinkMessage(this, messageWrapper, listener);
                return true;
            }
            case "org.droidplanner.services.android.core.drone.autopilot.action.REQUEST_HOME_UPDATE": {
                this.requestHomeUpdate();
                return true;
            }
            case "com.o3dr.services.android.action.CHECK_FEATURE_SUPPORT": {
                return this.checkFeatureSupport(data, listener);
            }
        }
        CommonApiUtils.postErrorEvent(3, listener);
        return true;
    }

    private boolean updateVehicleDataStreamRate(Bundle data, ICommandListener listener) {
        StreamRates streamRates = this.getStreamRates();
        if (streamRates != null) {
            int rate = data.getInt("extra_vehicle_data_stream_rate", -1);
            if (rate != -1) {
                StreamRates.Rates rates = new StreamRates.Rates(rate);
                streamRates.setRates(rates);
            }
            CommonApiUtils.postSuccessEvent(listener);
            return true;
        }
        CommonApiUtils.postErrorEvent(3, listener);
        return false;
    }

    private boolean checkFeatureSupport(Bundle data, ICommandListener listener) {
        String featureId = data.getString("extra_feature_id");
        if (!TextUtils.isEmpty((CharSequence)featureId)) {
            if (this.isFeatureSupported(featureId)) {
                CommonApiUtils.postSuccessEvent(listener);
            } else {
                CommonApiUtils.postErrorEvent(3, listener);
            }
        } else {
            CommonApiUtils.postErrorEvent(4, listener);
        }
        return true;
    }

    protected boolean isFeatureSupported(String featureId) {
        return false;
    }

    protected boolean enableManualControl(Bundle data, ICommandListener listener) {
        boolean enable = data.getBoolean("extra_do_enable");
        if (enable) {
            CommonApiUtils.postSuccessEvent(listener);
        } else {
            CommonApiUtils.postErrorEvent(3, listener);
        }
        return true;
    }

    protected boolean performArming(Bundle data, ICommandListener listener) {
        boolean doArm = data.getBoolean("extra_arm");
        boolean emergencyDisarm = data.getBoolean("extra_emergency_disarm");
        if (!doArm && emergencyDisarm) {
            MavLinkCommands.sendFlightTermination(this, listener);
        } else {
            MavLinkCommands.sendArmMessage(this, doArm, false, listener);
        }
        return true;
    }

    protected boolean setVehicleMode(Bundle data, ICommandListener listener) {
        data.setClassLoader(VehicleMode.class.getClassLoader());
        VehicleMode newMode = (VehicleMode)data.getParcelable("extra_vehicle_mode");
        if (newMode != null) {
            switch (newMode) {
                case COPTER_LAND: {
                    MavLinkCommands.sendNavLand(this, listener);
                    break;
                }
                case COPTER_RTL: {
                    MavLinkCommands.sendNavRTL(this, listener);
                    break;
                }
                case COPTER_GUIDED: {
                    MavLinkCommands.sendPause(this, listener);
                    break;
                }
                case COPTER_AUTO: {
                    MavLinkCommands.startMission(this, listener);
                }
            }
        }
        return true;
    }

    protected boolean setVelocity(Bundle data, ICommandListener listener) {
        float xAxis = data.getFloat("extra_velocity_x");
        short x = (short)(xAxis * 1000.0f);
        float yAxis = data.getFloat("extra_velocity_y");
        short y = (short)(yAxis * 1000.0f);
        float zAxis = data.getFloat("extra_velocity_z");
        short z = (short)(zAxis * 1000.0f);
        MavLinkCommands.sendManualControl(this, x, y, z, (short)0, 0, listener);
        return true;
    }

    protected boolean performTakeoff(Bundle data, ICommandListener listener) {
        double takeoffAltitude = data.getDouble("extra_altitude");
        MavLinkCommands.sendTakeoff(this, takeoffAltitude, listener);
        return true;
    }

    protected boolean brakeVehicle(ICommandListener listener) {
        this.getGuidedPoint().pauseAtCurrentLocation(listener);
        return true;
    }

    @Override
    public DroneAttribute getAttribute(String attributeType) {
        if (TextUtils.isEmpty((CharSequence)attributeType)) {
            return null;
        }
        switch (attributeType) {
            case "com.o3dr.services.android.lib.attribute.SPEED": {
                return this.speed;
            }
            case "com.o3dr.services.android.lib.attribute.BATTERY": {
                return this.battery;
            }
            case "com.o3dr.services.android.lib.attribute.SIGNAL": {
                return this.signal;
            }
            case "com.o3dr.services.android.lib.attribute.ATTITUDE": {
                return this.attitude;
            }
            case "com.o3dr.services.android.lib.attribute.ALTITUDE": {
                return this.altitude;
            }
            case "com.o3dr.services.android.lib.attribute.STATE": {
                return CommonApiUtils.getState(this, this.isConnected(), this.vibration);
            }
            case "com.o3dr.services.android.lib.attribute.GPS": {
                return this.vehicleGps;
            }
            case "com.o3dr.services.android.lib.attribute.HOME": {
                return this.vehicleHome;
            }
            case "com.o3dr.services.android.lib.attribute.PARAMETERS": {
                ParameterManager paramMgr = this.getParameterManager();
                if (paramMgr != null) {
                    this.parameters.setParametersList(paramMgr.getParameters().values());
                }
                return this.parameters;
            }
            case "com.o3dr.services.android.lib.attribute.TYPE": {
                return CommonApiUtils.getType(this);
            }
        }
        return null;
    }

    private void onHeartbeat(MAVLinkMessage msg) {
        this.heartbeat.onHeartbeat(msg);
    }

    @Override
    public void onMavLinkMessageReceived(MAVLinkMessage message) {
        if (message.sysid != this.getSysid()) {
            return;
        }
        this.onHeartbeat(message);
        switch (message.msgid) {
            case 109: {
                msg_radio_status m_radio_status = (msg_radio_status)message;
                this.processSignalUpdate(m_radio_status.rxerrors, m_radio_status.fixed, m_radio_status.rssi, m_radio_status.remrssi, m_radio_status.txbuf, m_radio_status.noise, m_radio_status.remnoise);
                break;
            }
            case 30: {
                msg_attitude m_att = (msg_attitude)message;
                this.processAttitude(m_att);
                break;
            }
            case 0: {
                msg_heartbeat msg_heart = (msg_heartbeat)message;
                this.processHeartbeat(msg_heart);
                break;
            }
            case 241: {
                msg_vibration vibrationMsg = (msg_vibration)message;
                this.processVibrationMessage(vibrationMsg);
                break;
            }
            case 193: {
                this.processEfkStatus((msg_ekf_status_report)message);
                break;
            }
            case 1: {
                msg_sys_status m_sys = (msg_sys_status)message;
                this.processSysStatus(m_sys);
                break;
            }
            case 33: {
                this.processGlobalPositionInt((msg_global_position_int)message);
                break;
            }
            case 24: {
                this.processGpsState((msg_gps_raw_int)message);
                break;
            }
            case 39: {
                this.processHomeUpdate((msg_mission_item)message);
                break;
            }
            case 42: {
                this.missionStats.setWpno(((msg_mission_current)message).seq);
                break;
            }
            case 46: {
                this.missionStats.setLastReachedWaypointNumber(((msg_mission_item_reached)message).seq);
                break;
            }
            case 62: {
                msg_nav_controller_output m_nav = (msg_nav_controller_output)message;
                this.setDisttowpAndSpeedAltErrors(m_nav.wp_dist, m_nav.alt_error, m_nav.aspd_error);
            }
        }
    }

    protected void processSysStatus(msg_sys_status m_sys) {
        this.processBatteryUpdate((double)m_sys.voltage_battery / 1000.0, m_sys.battery_remaining, (double)m_sys.current_battery / 100.0);
    }

    private void processHeartbeat(msg_heartbeat msg_heart) {
        this.setType(msg_heart.type);
        this.checkIfFlying(msg_heart);
        this.processState(msg_heart);
        this.processVehicleMode(msg_heart);
    }

    private void processVehicleMode(msg_heartbeat msg_heart) {
        ApmModes newMode = ApmModes.getMode(msg_heart.custom_mode, this.getType());
        this.state.setMode(newMode);
    }

    private void processState(msg_heartbeat msg_heart) {
        this.checkArmState(msg_heart);
        this.checkFailsafe(msg_heart);
    }

    private void checkFailsafe(msg_heartbeat msg_heart) {
        boolean failsafe2;
        boolean bl = failsafe2 = msg_heart.system_status == 5 || msg_heart.system_status == 6;
        if (failsafe2) {
            this.state.repeatWarning();
        }
    }

    private void checkArmState(msg_heartbeat msg_heart) {
        this.state.setArmed((msg_heart.base_mode & 0x80) == 128);
    }

    private void checkIfFlying(msg_heartbeat msg_heart) {
        short systemStatus = msg_heart.system_status;
        boolean wasFlying = this.state.isFlying();
        boolean isFlying = systemStatus == 4 || wasFlying && (systemStatus == 5 || systemStatus == 6);
        this.state.setIsFlying(isFlying);
    }

    private void setDisttowpAndSpeedAltErrors(double disttowp, double alt_error, double aspd_error) {
        this.missionStats.setDistanceToWp(disttowp);
        this.altitude.setTargetAltitude(this.altitude.getAltitude() + alt_error);
        this.notifyDroneEvent(DroneInterfaces.DroneEventsType.ORIENTATION);
    }

    public void processHomeUpdate(msg_mission_item missionItem) {
        if (missionItem.seq != 0) {
            return;
        }
        float latitude = missionItem.x;
        float longitude = missionItem.y;
        float altitude = missionItem.z;
        boolean homeUpdated = false;
        LatLongAlt homeCoord = this.vehicleHome.getCoordinate();
        if (homeCoord == null) {
            this.vehicleHome.setCoordinate(new LatLongAlt(latitude, longitude, altitude));
            homeUpdated = true;
        } else if (homeCoord.getLatitude() != (double)latitude || homeCoord.getLongitude() != (double)longitude || homeCoord.getAltitude() != (double)altitude) {
            homeCoord.setLatitude(latitude);
            homeCoord.setLongitude(longitude);
            homeCoord.setAltitude(altitude);
            homeUpdated = true;
        }
        if (homeUpdated) {
            this.notifyDroneEvent(DroneInterfaces.DroneEventsType.HOME);
        }
    }

    protected void processBatteryUpdate(double voltage, double remain, double current) {
        if (this.battery.getBatteryVoltage() != voltage || this.battery.getBatteryRemain() != remain || this.battery.getBatteryCurrent() != current) {
            this.battery.setBatteryVoltage(voltage);
            this.battery.setBatteryRemain(remain);
            this.battery.setBatteryCurrent(current);
            this.notifyDroneEvent(DroneInterfaces.DroneEventsType.BATTERY);
        }
    }

    private void processVibrationMessage(msg_vibration vibrationMsg) {
        boolean wasUpdated = false;
        if (this.vibration.getVibrationX() != vibrationMsg.vibration_x) {
            this.vibration.setVibrationX(vibrationMsg.vibration_x);
            wasUpdated = true;
        }
        if (this.vibration.getVibrationY() != vibrationMsg.vibration_y) {
            this.vibration.setVibrationY(vibrationMsg.vibration_y);
            wasUpdated = true;
        }
        if (this.vibration.getVibrationZ() != vibrationMsg.vibration_z) {
            this.vibration.setVibrationZ(vibrationMsg.vibration_z);
            wasUpdated = true;
        }
        if (this.vibration.getFirstAccelClipping() != vibrationMsg.clipping_0) {
            this.vibration.setFirstAccelClipping(vibrationMsg.clipping_0);
            wasUpdated = true;
        }
        if (this.vibration.getSecondAccelClipping() != vibrationMsg.clipping_1) {
            this.vibration.setSecondAccelClipping(vibrationMsg.clipping_1);
            wasUpdated = true;
        }
        if (this.vibration.getThirdAccelClipping() != vibrationMsg.clipping_2) {
            this.vibration.setThirdAccelClipping(vibrationMsg.clipping_2);
            wasUpdated = true;
        }
        if (wasUpdated) {
            this.notifyAttributeListener("com.o3dr.services.android.lib.attribute.event.STATE_VEHICLE_VIBRATION");
        }
    }

    protected void setType(int type) {
        this.type.setType(type);
    }

    @Override
    public int getType() {
        return this.type.getType();
    }

    private void processAttitude(msg_attitude m_att) {
        this.attitude.setRoll(Math.toDegrees(m_att.roll));
        this.attitude.setRollSpeed((float)Math.toDegrees(m_att.rollspeed));
        this.attitude.setPitch(Math.toDegrees(m_att.pitch));
        this.attitude.setPitchSpeed((float)Math.toDegrees(m_att.pitchspeed));
        this.attitude.setYaw(Math.toDegrees(m_att.yaw));
        this.attitude.setYawSpeed((float)Math.toDegrees(m_att.yawspeed));
        this.notifyDroneEvent(DroneInterfaces.DroneEventsType.ATTITUDE);
    }

    protected void processSignalUpdate(int rxerrors, int fixed, short rssi, short remrssi, short txbuf, short noise, short remnoise) {
        this.signal.setValid(true);
        this.signal.setRxerrors(rxerrors & 0xFFFF);
        this.signal.setFixed(fixed & 0xFFFF);
        this.signal.setRssi(this.SikValueToDB(rssi & 0xFF));
        this.signal.setRemrssi(this.SikValueToDB(remrssi & 0xFF));
        this.signal.setNoise(this.SikValueToDB(noise & 0xFF));
        this.signal.setRemnoise(this.SikValueToDB(remnoise & 0xFF));
        this.signal.setTxbuf(txbuf & 0xFF);
        this.signal.setSignalStrength(MathUtils.getSignalStrength(this.signal.getFadeMargin(), this.signal.getRemFadeMargin()));
        this.notifyDroneEvent(DroneInterfaces.DroneEventsType.RADIO);
    }

    protected double SikValueToDB(int value) {
        return (double)value / 1.9 - 127.0;
    }

    protected void processGlobalPositionInt(msg_global_position_int gpi) {
        if (gpi == null) {
            return;
        }
        double newLat = (double)gpi.lat / 1.0E7;
        double newLong = (double)gpi.lon / 1.0E7;
        boolean positionUpdated = false;
        LatLong gpsPosition = this.vehicleGps.getPosition();
        if (gpsPosition == null) {
            gpsPosition = new LatLong(newLat, newLong);
            this.vehicleGps.setPosition(gpsPosition);
            positionUpdated = true;
        } else if (gpsPosition.getLatitude() != newLat || gpsPosition.getLongitude() != newLong) {
            gpsPosition.setLatitude(newLat);
            gpsPosition.setLongitude(newLong);
            positionUpdated = true;
        }
        if (positionUpdated) {
            this.notifyAttributeListener("com.o3dr.services.android.lib.attribute.event.GPS_POSITION");
        }
    }

    private void processEfkStatus(msg_ekf_status_report ekf_status_report) {
        this.state.setEkfStatus(ekf_status_report);
        this.vehicleGps.setVehicleArmed(this.state.isArmed());
        this.vehicleGps.setEkfStatus(CommonApiUtils.generateEkfStatus(ekf_status_report));
        this.notifyAttributeListener("com.o3dr.services.android.lib.attribute.event.GPS_POSITION");
    }

    private void processGpsState(msg_gps_raw_int gpsState) {
        if (gpsState == null) {
            return;
        }
        double newEph = (double)gpsState.eph / 100.0;
        if (this.vehicleGps.getSatellitesCount() != gpsState.satellites_visible || this.vehicleGps.getGpsEph() != newEph) {
            this.vehicleGps.setSatCount(gpsState.satellites_visible);
            this.vehicleGps.setGpsEph(newEph);
            this.notifyAttributeListener("com.o3dr.services.android.lib.attribute.event.GPS_COUNT");
        }
        if (this.vehicleGps.getFixType() != gpsState.fix_type) {
            this.vehicleGps.setFixType(gpsState.fix_type);
            this.notifyAttributeListener("com.o3dr.services.android.lib.attribute.event.GPS_FIX");
        }
    }

    protected void requestHomeUpdate() {
        GenericMavLinkDrone.requestHomeUpdate(this);
    }

    private static void requestHomeUpdate(MavLinkDrone drone) {
        MavLinkWaypoint.requestWayPoint(drone, 0);
    }
}

