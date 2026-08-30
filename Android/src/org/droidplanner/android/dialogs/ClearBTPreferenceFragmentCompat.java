package org.droidplanner.android.dialogs;

import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;

import org.droidplanner.android.utils.prefs.DroidPlannerPrefs;

/**
 * Created by fhuya on 5/12/2016.
 */
public class ClearBTPreferenceFragmentCompat extends PreferenceDialogFragmentCompat {

    public static ClearBTPreferenceFragmentCompat newInstance(Preference preference){
        ClearBTPreferenceFragmentCompat fragment = new ClearBTPreferenceFragmentCompat();
        Bundle bundle = new Bundle(1);
        bundle.putString(ARG_KEY, preference.getKey());
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if(positiveResult) {
            DroidPlannerPrefs.getInstance(getContext()).setBluetoothDeviceAddress("");
        }
    }
}
