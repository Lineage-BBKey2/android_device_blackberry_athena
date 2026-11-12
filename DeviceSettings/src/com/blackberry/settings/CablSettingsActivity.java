/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.blackberry.settings;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class CablSettingsActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new CablSettingsFragment()).commit();
        }
    }

    public static class CablSettingsFragment extends PreferenceFragmentCompat {
        private static final String TAG = "CablSettings";
        private static final String MODE = "persist.vendor.display.cabl.mode";
        private static final String APPLIED = "vendor.display.cabl.applied";
        private static final String STATE = "vendor.display.cabl.state";
        private final Handler mHandler = new Handler(Looper.getMainLooper());
        private ListPreference mMode;
        private Preference mStatus;
        private Preference mRetry;
        private boolean mPrimaryUser;
        private final Runnable mRefresh = new Runnable() {
            @Override
            public void run() {
                refresh();
                mHandler.postDelayed(this, 1000);
            }
        };

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.cabl_settings, rootKey);
            mMode = findPreference("cabl_mode");
            mMode.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            mStatus = findPreference("cabl_status");
            mRetry = findPreference("cabl_retry");
            mPrimaryUser = UserHandle.myUserId() == 0;
            mMode.setEnabled(mPrimaryUser);
            mRetry.setEnabled(mPrimaryUser);
            mMode.setOnPreferenceChangeListener((preference, newValue) -> {
                if (!(newValue instanceof String)) return false;
                String value = (String) newValue;
                if (!writeMode(value)) return false;
                mMode.setValue(value);
                refresh();
                return false;
            });
            mRetry.setOnPreferenceClickListener(preference -> {
                writeMode(SystemProperties.get(MODE, "High"));
                return true;
            });
            refresh();
        }

        @Override
        public void onResume() {
            super.onResume();
            mHandler.removeCallbacks(mRefresh);
            mRefresh.run();
        }

        @Override
        public void onPause() {
            mHandler.removeCallbacks(mRefresh);
            super.onPause();
        }

        private boolean writeMode(String value) {
            if (!mPrimaryUser || mMode.findIndexOfValue(value) < 0) return false;
            try {
                // Changing (or re-writing) the saved value requests init to apply it.
                // The UI never executes the client or opens the display HAL itself.
                SystemProperties.set(MODE, value);
                return true;
            } catch (RuntimeException e) {
                Log.e(TAG, "Cannot save CABL mode", e);
                Toast.makeText(requireContext(), R.string.cabl_save_failed,
                        Toast.LENGTH_LONG).show();
                return false;
            }
        }

        private int confirmedStrengthLabel(String value) {
            switch (value) {
                case "off": return R.string.cabl_off;
                case "Low": return R.string.cabl_strong_short;
                case "Medium": return R.string.cabl_balanced_short;
                case "High": return R.string.cabl_mild_short;
                default: return R.string.cabl_unknown_selection;
            }
        }

        private void refresh() {
            String desired = SystemProperties.get(MODE, "High");
            int index = mMode.findIndexOfValue(desired);
            if (index >= 0) {
                mMode.setValue(desired);
                // The summary provider displays the selected translated entry.
            } else {
                mMode.setValue(null);
                // Invalid saved values remain unselected; choosing a mode repairs them.
            }
            if (!mPrimaryUser) {
                mStatus.setSummary(R.string.cabl_primary_user_only);
                return;
            }
            String state = SystemProperties.get(STATE, "");
            String applied = SystemProperties.get(APPLIED, "");
            int appliedIndex = mMode.findIndexOfValue(applied);
            if ("applying".equals(state)) {
                mStatus.setSummary(R.string.cabl_applying);
            } else if ("applied".equals(state) && desired.equals(applied)
                    && appliedIndex >= 0) {
                mStatus.setSummary(getString(R.string.cabl_last_confirmed,
                        getString(confirmedStrengthLabel(applied))));
            } else {
                mStatus.setSummary(R.string.cabl_not_confirmed);
            }
        }
    }
}
