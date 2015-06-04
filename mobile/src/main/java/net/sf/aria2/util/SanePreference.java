package net.sf.aria2.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import com.afollestad.materialdialogs.prefs.MaterialEditTextPreference;

public class SanePreference extends MaterialEditTextPreference implements SharedPreferences.OnSharedPreferenceChangeListener {
    public SanePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SanePreference(Context context) {
        super(context);
    }

    @Override
    protected void onAttachedToHierarchy(@NonNull PreferenceManager preferenceManager) {
        super.onAttachedToHierarchy(preferenceManager);

        preferenceManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onActivityDestroy() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

        super.onActivityDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getKey().equals(key)) {
            setText(sharedPreferences.getString(key, ""));
        }
    }
}
