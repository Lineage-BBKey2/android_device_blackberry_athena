/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.blackberry.settings;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

public class LegacyBlackBerryAppsActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final ActionBar bar = getActionBar();
        if (bar != null) bar.setDisplayHomeAsUpEnabled(true);
        if (state == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            new LegacyBlackBerryAppsFragment())
                    .commit();
        }
    }

    public static class LegacyBlackBerryAppsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle state, String rootKey) {
            setPreferencesFromResource(R.xml.legacy_blackberry_apps, rootKey);
            bind(LegacyAppCatalog.KEYBOARD);
            bind(LegacyAppCatalog.LAUNCHER);
        }

        private void bind(LegacyAppCatalog.App app) {
            final Preference pref = findPreference(app.key);
            if (pref == null) return;
            pref.setOnPreferenceClickListener(clicked -> {
                final CharSequence[] choices = {
                        getString(R.string.legacy_app_google_play_title),
                        getString(R.string.legacy_app_mirror_title)
                };
                new AlertDialog.Builder(requireContext())
                        .setTitle(app.title)
                        .setItems(choices, (dialog, which) -> {
                            if (which == 0) {
                                openGooglePlay(app.packageName);
                            } else if (which == 1) {
                                startActivity(new Intent(requireContext(),
                                        LegacyApkDownloadActivity.class)
                                        .putExtra(LegacyApkDownloadActivity.EXTRA_APP, app.key));
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
        }

        @Override
        public void onResume() {
            super.onResume();
            updateStatus(LegacyAppCatalog.KEYBOARD);
            updateStatus(LegacyAppCatalog.LAUNCHER);
        }

        private void updateStatus(LegacyAppCatalog.App app) {
            final Preference pref = findPreference(app.key);
            if (pref == null) return;
            boolean installed;
            try {
                requireContext().getPackageManager().getApplicationInfo(app.packageName, 0);
                installed = true;
            } catch (PackageManager.NameNotFoundException e) {
                installed = false;
            }
            pref.setSummary(installed ? R.string.app_installed : R.string.app_not_installed);
        }

        private void openGooglePlay(String packageName) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + packageName))
                        .setPackage("com.android.vending"));
            } catch (ActivityNotFoundException e) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id="
                                    + packageName)));
                } catch (ActivityNotFoundException unavailable) {
                    Toast.makeText(requireContext(), R.string.legacy_app_no_link_handler,
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
