package org.droidplanner.android.utils.analytics;

import android.content.Context;

import org.droidplanner.android.BuildConfig;

import timber.log.Timber;

/**
 * Formerly a Google Analytics wrapper. Google Analytics for mobile (the
 * {@code com.google.android.gms.analytics} SDK) was discontinued, so the
 * tracking was removed. The class is kept as a thin local event logger so the
 * existing call sites stay meaningful; in debug builds the events go to logcat,
 * in release builds the calls are no-ops.
 */
public class GAUtils {

    private static final String LOG_TAG = "Analytics";

    // Not instantiable
    private GAUtils() {}

    /**
     * Event categories used across the app.
     */
    public static class Category {
        public static final String FLIGHT = "Flight";
        public static final String EDITOR = "Editor";
        public static final String FAILSAFE = "Failsafe";
        public static final String MAVLINK_CONNECTION = "Mavlink connection";
        public static final String DRONESHARE = "Droneshare";
        public static final String MISSION_PLANNING = "Mission planning";
        public static final String PREFERENCE_DIALOGS = "Preference Dialogs";
    }

    /** Kept for source compatibility; does nothing. */
    public static void initGATracker(Context context) {}

    /** Kept for source compatibility; does nothing. */
    public static void startNewSession(Context context) {}

    public static void logEvent(String category, String action) {
        logEvent(category, action, null);
    }

    public static void logEvent(String category, String action, String label) {
        if (BuildConfig.DEBUG) {
            Timber.tag(LOG_TAG).d("%s / %s%s", category, action,
                    label == null ? "" : " / " + label);
        }
    }
}
