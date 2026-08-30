package org.droidplanner.android;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.o3dr.android.client.Drone;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeEventExtra;

import org.droidplanner.android.activities.FlightActivity;
import org.droidplanner.android.notifications.NotificationHandler;
import org.droidplanner.android.notifications.StatusBarNotificationProvider;
import org.droidplanner.android.utils.NetworkUtils;

import timber.log.Timber;

/**
 * Created by Fredia Huya-Kouadio on 9/28/15.
 */
public class AppService extends Service {

    private static final IntentFilter filter = new IntentFilter();

    static {
        filter.addAction(AttributeEvent.STATE_CONNECTED);
        filter.addAction(AttributeEvent.STATE_DISCONNECTED);
        filter.addAction(AttributeEvent.AUTOPILOT_ERROR);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case AttributeEvent.STATE_CONNECTED:
                    if (notificationHandler != null)
                        notificationHandler.init();

                    if (NetworkUtils.isOnSoloNetwork(context)) {
                        bringUpCellularNetwork();
                    }
                    break;

                case AttributeEvent.STATE_DISCONNECTED:
                    if (notificationHandler != null) {
                        notificationHandler.terminate();
                    }

                    leaveForeground();
                    stopSelf();
                    break;

                case AttributeEvent.AUTOPILOT_ERROR:
                    final String errorName = intent.getStringExtra(AttributeEventExtra.EXTRA_AUTOPILOT_ERROR_ID);
                    if (notificationHandler != null)
                        notificationHandler.onAutopilotError(errorName);
                    break;
            }
        }
    };

    private final BinderHandler binderHandler = new BinderHandler();

    private NotificationHandler notificationHandler;
    private DroidPlannerApp dpApp;
    private Drone drone;

    /**
     * Started (via {@code ContextCompat.startForegroundService}) when a vehicle is
     * connected. Android 8+ forbids background {@code startService}, and Android 12+
     * requires {@code startForeground()} promptly after a foreground-service start,
     * so we go foreground here immediately. It's also bound by every activity
     * (BIND_AUTO_CREATE) for the common in-app case.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground();
        return START_NOT_STICKY;
    }

    private boolean isForeground;

    private void goForeground() {
        if (isForeground) return;
        try {
            StatusBarNotificationProvider.ensureChannel(this);
            final int id = StatusBarNotificationProvider.NOTIFICATION_ID;
            final Notification notification = buildForegroundNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
            } else {
                startForeground(id, notification);
            }
            isForeground = true;
        } catch (Exception e) {
            Timber.w(e, "Unable to start AppService in the foreground.");
        }
    }

    private void leaveForeground() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
        isForeground = false;
    }

    private Notification buildForegroundNotification() {
        final PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, FlightActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, StatusBarNotificationProvider.CHANNEL_ID)
                .setContentTitle(getString(R.string.app_title))
                .setContentText(getString(R.string.connected))
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .build();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        dpApp = (DroidPlannerApp) getApplication();
        dpApp.createFileStartLogging();

        dpApp.getSoundManager().start();

        drone = dpApp.getDrone();

        final Context context = getApplicationContext();
        if (NetworkUtils.isOnSoloNetwork(context)) {
            bringUpCellularNetwork();
        }

        notificationHandler = new NotificationHandler(context, drone);

        if (drone.isConnected()) {
            notificationHandler.init();
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        leaveForeground();
        LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(receiver);

        if (notificationHandler != null)
            notificationHandler.terminate();

        dpApp.getSoundManager().stop();

        bringDownCellularNetwork();

        dpApp.closeLogFile();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binderHandler;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void bringUpCellularNetwork() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return;

        // Wait until the drone is connected.
        if(drone == null || !drone.isConnected())
            return;

        Timber.i("Setting up cellular network request.");
        final ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkRequest networkReq = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();

        connMgr.requestNetwork(networkReq, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Timber.i("Setting up process default network: %s", network);
                boolean wasBound;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    wasBound = connMgr.bindProcessToNetwork(network);
                } else {
                    wasBound = ConnectivityManager.setProcessDefaultNetwork(network);
                }
                DroidPlannerApp.setCellularNetworkAvailability(wasBound);
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void bringDownCellularNetwork() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return;

        Timber.i("Bringing down cellular netowrk access.");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            connMgr.bindProcessToNetwork(null);
        } else {
            ConnectivityManager.setProcessDefaultNetwork(null);
        }
        ConnectivityManager.setProcessDefaultNetwork(null);
        DroidPlannerApp.setCellularNetworkAvailability(false);
    }

    public static class BinderHandler extends Binder {
    }
}
