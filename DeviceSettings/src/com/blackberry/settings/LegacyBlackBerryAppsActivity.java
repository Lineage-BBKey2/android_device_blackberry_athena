/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.blackberry.settings;

import android.app.ActionBar;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class LegacyBlackBerryAppsActivity extends CollapsingToolbarBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new LegacyBlackBerryAppsFragment())
                    .commit();
        }
    }

    public static class LegacyBlackBerryAppsFragment extends PreferenceFragmentCompat {

        private static final String KEY_BLACKBERRY_KEYBOARD =
                "blackberry_keyboard";
        private static final String KEY_BLACKBERRY_LAUNCHER =
                "blackberry_launcher";
        private static final String PACKAGE_BLACKBERRY_KEYBOARD =
                "com.blackberry.keyboard";
        private static final String PACKAGE_BLACKBERRY_LAUNCHER =
                "com.blackberry.blackberrylauncher";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.legacy_blackberry_apps, rootKey);

            final Preference keyboard = findPreference(KEY_BLACKBERRY_KEYBOARD);
            if (keyboard != null) {
                keyboard.setOnPreferenceClickListener(preference -> {
                    openGooglePlay(PACKAGE_BLACKBERRY_KEYBOARD);
                    return true;
                });
            }

            final Preference launcher = findPreference(KEY_BLACKBERRY_LAUNCHER);
            if (launcher != null) {
                launcher.setOnPreferenceClickListener(preference -> {
                    openGooglePlay(PACKAGE_BLACKBERRY_LAUNCHER);
                    return true;
                });
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            updateAppStatus(KEY_BLACKBERRY_KEYBOARD, PACKAGE_BLACKBERRY_KEYBOARD);
            updateAppStatus(KEY_BLACKBERRY_LAUNCHER, PACKAGE_BLACKBERRY_LAUNCHER);
        }

        private void updateAppStatus(String preferenceKey, String packageName) {
            final Preference preference = findPreference(preferenceKey);
            if (preference == null) {
                return;
            }

            preference.setSummary(isPackageInstalled(packageName)
                    ? R.string.app_installed
                    : R.string.app_not_installed);
        }

        private boolean isPackageInstalled(String packageName) {
            try {
                requireContext().getPackageManager().getApplicationInfo(packageName, 0);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        }

        private void openGooglePlay(String packageName) {
            final Intent playStoreIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + packageName));
            playStoreIntent.setPackage("com.android.vending");

            try {
                startActivity(playStoreIntent);
            } catch (ActivityNotFoundException e) {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id="
                                + packageName)));
            }
        }
    }
}
