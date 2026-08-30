package org.droidplanner.android.utils.file;

import android.content.Context;

import org.droidplanner.android.DroidPlannerApp;

import java.io.File;

public class DirectoryPath {

    /**
     * Main path used to store private data files related to the program
     * @param context application context
     * @return Path to Tower private data directory.
     */
    public static String getPrivateDataPath(Context context){
        File dataDir = appDir(context);
        return dataDir.getAbsolutePath();
    }

    /**
     * App-owned storage root. Scoped-storage safe: an app can always read/write
     * its own external-files dir (and the internal one) on every Android version
     * with no runtime permission. Missions, parameters and offline data used to
     * live in {@code /sdcard/Tower/}, which is no longer writable on Android 11+.
     */
    private static File appDir(Context context) {
        File dir = context != null ? context.getExternalFilesDir(null) : null;
        if (dir == null && context != null) {
            // External storage unavailable (unmounted / emulated). Fall back to internal.
            dir = context.getFilesDir();
        }
        if (dir != null) {
            dir.mkdirs();
        }
        return dir;
    }

    private static File appDir() {
        return appDir(DroidPlannerApp.getAppContext());
    }

    /**
     * Main path used to store data files related to the program.
     */
    static public String getPublicDataPath() {
        return appDir().getAbsolutePath() + File.separator;
    }

    /** Returns {@code <appDir>/<name>/}, creating the directory if needed. */
    private static String subDir(String name) {
        File dir = new File(appDir(), name);
        dir.mkdirs();
        return dir.getAbsolutePath() + File.separator;
    }

    /**
     * Storage folder for Parameters
     */
    static public String getParametersPath() {
        return subDir("Parameters");
    }

    /**
     * Storage folder for mission files
     */
    static public String getWaypointsPath() {
        return subDir("Waypoints");
    }

    /**
     * Storage folder for user map tiles
     */
    static public String getMapsPath() {
        return subDir("Maps");
    }

    /**
     * Storage folder for stacktraces
     */
    public static String getLogCatPath(Context context) {
        return getPrivateDataPath(context) + "/LogCat/";
    }

    /**
     * Storage folder for stacktraces
     */
    public static String getCrashLogPath(Context context) {
        return getPrivateDataPath(context) + "/crash_log/";
    }
}
