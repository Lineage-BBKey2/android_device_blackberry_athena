/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.blackberry.settings;

import android.os.Bundle;
import android.provider.Settings;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class DeviceSettingsActivity extends CollapsingToolbarBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new DeviceSettingsFragment())
                    .commit();
        }
    }

    public static class DeviceSettingsFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceChangeListener {

        private static final String KEY_IME_SWITCHER = "ime_switcher_shortcut";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.device_settings, rootKey);

            ListPreference imeSwitcher = findPreference(KEY_IME_SWITCHER);
            if (imeSwitcher != null) {
                int currentValue = Settings.Secure.getInt(
                        getContext().getContentResolver(),
                        "ime_switcher_shortcut", 0);
                imeSwitcher.setValue(String.valueOf(currentValue));
                imeSwitcher.setSummary(imeSwitcher.getEntry());
                imeSwitcher.setOnPreferenceChangeListener(this);
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (KEY_IME_SWITCHER.equals(preference.getKey())) {
                int value = Integer.parseInt((String) newValue);
                Settings.Secure.putInt(
                        getContext().getContentResolver(),
                        "ime_switcher_shortcut", value);
                ListPreference listPref = (ListPreference) preference;
                int index = listPref.findIndexOfValue((String) newValue);
                listPref.setSummary(listPref.getEntries()[index]);
                return true;
            }
            return false;
        }
    }
}
