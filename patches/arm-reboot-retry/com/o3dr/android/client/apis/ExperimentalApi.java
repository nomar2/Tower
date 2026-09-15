/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.os.Bundle
 *  android.os.Handler
 *  android.os.Parcelable
 *  android.text.TextUtils
 *  android.util.Log
 */
package com.o3dr.android.client.apis;

import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.Api;
import com.o3dr.android.client.apis.CapabilityApi;
import com.o3dr.android.client.utils.connection.IpConnectionListener;
import com.o3dr.android.client.utils.connection.UdpConnection;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.mavlink.MavlinkMessageWrapper;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.action.Action;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

public class ExperimentalApi
extends Api {
    private static final ConcurrentHashMap<Drone, ExperimentalApi> experimentalApiCache = new ConcurrentHashMap();
    private static final Api.Builder<ExperimentalApi> apiBuilder = new Api.Builder<ExperimentalApi>(){

        @Override
        public ExperimentalApi build(Drone drone) {
            return new ExperimentalApi(drone);
        }
    };
    private final CapabilityApi capabilityChecker;
    private final VideoStreamObserver videoStreamObserver;
    private final Drone drone;

    public static ExperimentalApi getApi(Drone drone) {
        return ExperimentalApi.getApi(drone, experimentalApiCache, apiBuilder);
    }

    private ExperimentalApi(Drone drone) {
        this.drone = drone;
        this.capabilityChecker = CapabilityApi.getApi(drone);
        this.videoStreamObserver = new VideoStreamObserver(drone.getHandler());
    }

    public void triggerCamera() {
        this.drone.performAsyncAction(new Action("com.o3dr.services.android.action.TRIGGER_CAMERA"));
    }

    public void setROI(LatLongAlt roi) {
        this.setROI(roi, null);
    }

    public void setROI(LatLongAlt roi, AbstractCommandListener listener) {
        Bundle params = new Bundle();
        params.putParcelable("extra_set_roi_lat_long_alt", (Parcelable)roi);
        Action epmAction = new Action("com.o3dr.services.android.action.SET_ROI", params);
        this.drone.performAsyncActionOnDroneThread(epmAction, listener);
    }

    public void sendMavlinkMessage(MavlinkMessageWrapper messageWrapper) {
        Bundle params = new Bundle();
        params.putParcelable("extra_mavlink_message", (Parcelable)messageWrapper);
        this.drone.performAsyncAction(new Action("com.o3dr.services.android.action.SEND_MAVLINK_MESSAGE", params));
    }

    /**
     * Same as sendMavlinkMessage(MavlinkMessageWrapper), but with a listener that
     * gets onSuccess()/onError()/onTimeout() for the COMMAND_ACK (2s timeout,
     * DroneCommandTracker) - only meaningful for a message DroneCommandTracker
     * tracks by ack (currently msg_command_long and msg_set_mode).
     */
    public void sendMavlinkMessage(MavlinkMessageWrapper messageWrapper, AbstractCommandListener listener) {
        Bundle params = new Bundle();
        params.putParcelable("extra_mavlink_message", (Parcelable)messageWrapper);
        this.drone.performAsyncActionOnDroneThread(new Action("com.o3dr.services.android.action.SEND_MAVLINK_MESSAGE", params), listener);
    }

    public void setRelay(int relayNumber, boolean enabled) {
        this.setRelay(relayNumber, enabled, null);
    }

    public void setRelay(int relayNumber, boolean enabled, AbstractCommandListener listener) {
        Bundle params = new Bundle(2);
        params.putInt("extra_relay_number", relayNumber);
        params.putBoolean("extra_is_relay_on", enabled);
        this.drone.performAsyncActionOnDroneThread(new Action("com.o3dr.services.android.action.SET_RELAY", params), listener);
    }

    public void setServo(int channel, int pwm) {
        this.setServo(channel, pwm, null);
    }

    public void setServo(int channel, int pwm, AbstractCommandListener listener) {
        Bundle params = new Bundle(2);
        params.putInt("extra_servo_channel", channel);
        params.putInt("extra_servo_PWM", pwm);
        this.drone.performAsyncActionOnDroneThread(new Action("com.o3dr.services.android.action.SET_SERVO", params), listener);
    }

    public void startVideoStream(final String tag, final IVideoStreamCallback callback) {
        if (callback == null) {
            throw new NullPointerException("Video stream callback can't be null");
        }
        this.capabilityChecker.checkFeatureSupport("feature_solo_video_streaming", new CapabilityApi.FeatureSupportListener(){

            @Override
            public void onFeatureSupportResult(String featureId, int result, Bundle resultInfo) {
                AbstractCommandListener listener = new AbstractCommandListener(){

                    @Override
                    public void onSuccess() {
                        ExperimentalApi.this.videoStreamObserver.setCallback(callback);
                        ExperimentalApi.this.videoStreamObserver.start();
                        ExperimentalApi.this.videoStreamObserver.getCallback().onVideoStreamConnecting();
                    }

                    @Override
                    public void onError(int executionError) {
                        ExperimentalApi.this.videoStreamObserver.getCallback().onError(executionError);
                    }

                    @Override
                    public void onTimeout() {
                        ExperimentalApi.this.videoStreamObserver.getCallback().onTimeout();
                    }
                };
                switch (result) {
                    case 0: {
                        ExperimentalApi.this.startVideoStreamForObserver(tag, listener);
                        break;
                    }
                    case 1: {
                        Api.postErrorEvent(3, listener);
                        break;
                    }
                    default: {
                        Api.postErrorEvent(4, listener);
                    }
                }
            }
        });
    }

    public void stopVideoStream(final String tag) {
        this.capabilityChecker.checkFeatureSupport("feature_solo_video_streaming", new CapabilityApi.FeatureSupportListener(){

            @Override
            public void onFeatureSupportResult(String featureId, int result, Bundle resultInfo) {
                AbstractCommandListener listener = new AbstractCommandListener(){

                    @Override
                    public void onSuccess() {
                        ExperimentalApi.this.videoStreamObserver.getCallback().onVideoStreamDisconnecting();
                        ExperimentalApi.this.videoStreamObserver.stop();
                    }

                    @Override
                    public void onError(int executionError) {
                        ExperimentalApi.this.videoStreamObserver.getCallback().onError(executionError);
                    }

                    @Override
                    public void onTimeout() {
                        ExperimentalApi.this.videoStreamObserver.getCallback().onTimeout();
                    }
                };
                switch (result) {
                    case 0: {
                        ExperimentalApi.this.stopVideoStreamForObserver(tag, listener);
                        break;
                    }
                    case 1: {
                        Api.postErrorEvent(3, listener);
                        break;
                    }
                    default: {
                        Api.postErrorEvent(4, listener);
                    }
                }
            }
        });
    }

    private String getObserverTag(String tag) {
        return "observer" + (TextUtils.isEmpty((CharSequence)tag) ? "" : "." + tag);
    }

    private void startVideoStreamForObserver(String tag, AbstractCommandListener listener) {
        Bundle params = new Bundle();
        params.putString("extra_video_tag", this.getObserverTag(tag));
        this.drone.performAsyncActionOnDroneThread(new Action("com.o3dr.services.android.action.camera.START_VIDEO_STREAM_FOR_OBSERVER", params), listener);
    }

    private void stopVideoStreamForObserver(String tag, AbstractCommandListener listener) {
        Bundle params = new Bundle();
        params.putString("extra_video_tag", this.getObserverTag(tag));
        this.drone.performAsyncActionOnDroneThread(new Action("com.o3dr.services.android.action.camera.STOP_VIDEO_STREAM_FOR_OBSERVER", params), listener);
    }

    public static interface IVideoStreamCallback {
        public void onVideoStreamConnecting();

        public void onVideoStreamConnected();

        public void onVideoStreamDisconnecting();

        public void onVideoStreamDisconnected();

        public void onError(int var1);

        public void onTimeout();

        public void onAsyncVideoStreamPacketReceived(byte[] var1, int var2);
    }

    private static class VideoStreamObserver
    implements IpConnectionListener {
        private final String TAG = VideoStreamObserver.class.getSimpleName();
        private static final int UDP_BUFFER_SIZE = 1500;
        private static final long RECONNECT_COUNTDOWN_IN_MILLIS = 1000L;
        private static final int SOLO_STREAM_UDP_PORT = 5600;
        private UdpConnection linkConn;
        private final Handler handler;
        private final Runnable onVideoStreamConnected = new Runnable(){

            @Override
            public void run() {
                VideoStreamObserver.this.handler.removeCallbacks((Runnable)this);
                if (VideoStreamObserver.this.callback != null) {
                    VideoStreamObserver.this.callback.onVideoStreamConnected();
                }
            }
        };
        private final Runnable onVideoStreamDisconnected = new Runnable(){

            @Override
            public void run() {
                VideoStreamObserver.this.handler.removeCallbacks((Runnable)this);
                if (VideoStreamObserver.this.callback != null) {
                    VideoStreamObserver.this.callback.onVideoStreamDisconnected();
                }
            }
        };
        private IVideoStreamCallback callback;
        private final Runnable reconnectTask = new Runnable(){

            @Override
            public void run() {
                VideoStreamObserver.this.handler.removeCallbacks(VideoStreamObserver.this.reconnectTask);
                if (VideoStreamObserver.this.linkConn != null) {
                    VideoStreamObserver.this.linkConn.connect(null);
                }
            }
        };

        public VideoStreamObserver(Handler handler) {
            this.handler = handler;
        }

        public void setCallback(IVideoStreamCallback callback) {
            this.callback = callback;
        }

        private IVideoStreamCallback getCallback() {
            return this.callback;
        }

        public void start() {
            if (this.linkConn == null) {
                this.linkConn = new UdpConnection(this.handler, 5600, 1500, true, 42);
                this.linkConn.setIpConnectionListener(this);
            }
            this.handler.removeCallbacks(this.reconnectTask);
            Log.d((String)this.TAG, (String)"Connecting to video stream...");
            this.linkConn.connect(null);
        }

        public void stop() {
            Log.d((String)this.TAG, (String)"Stopping video manager");
            this.handler.removeCallbacks(this.reconnectTask);
            if (this.linkConn != null) {
                this.linkConn.disconnect();
                this.linkConn = null;
            }
        }

        @Override
        public void onIpConnected() {
            Log.d((String)this.TAG, (String)"Connected to video stream");
            this.handler.post(this.onVideoStreamConnected);
            this.handler.removeCallbacks(this.reconnectTask);
        }

        @Override
        public void onIpDisconnected() {
            Log.d((String)this.TAG, (String)"Video stream disconnected");
            this.handler.post(this.onVideoStreamDisconnected);
            this.handler.postDelayed(this.reconnectTask, 1000L);
        }

        @Override
        public void onPacketReceived(ByteBuffer packetBuffer) {
            this.callback.onAsyncVideoStreamPacketReceived(packetBuffer.array(), packetBuffer.limit());
        }
    }
}

