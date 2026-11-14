/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.blackberry.settings;

import android.content.Context;
import android.os.SystemProperties;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

/** Device-wide keyboard family choice. Applied at the next boot. */
final class KeyboardHardwareLayout {
    private static final String KEY = "keyboard_hardware_layout";
    private static final String REQUEST = "persist.vendor.bb.keyboard.layout";
    private static final String APPLIED = "vendor.bb.keyboard.layout.applied";

    private KeyboardHardwareLayout() {}

    static void bind(PreferenceFragmentCompat fragment) {
        final ListPreference preference = fragment.findPreference(KEY);
        if (preference == null) {
            return;
        }
        final Context context = fragment.requireContext();
        final String saved = normalize(SystemProperties.get(REQUEST, "auto"));
        preference.setValue(saved);
        updateSummary(context, preference, saved);
        preference.setOnPreferenceChangeListener((item, newValue) -> {
            if (!(newValue instanceof String) || !isValid((String) newValue)) {
                return false;
            }
            final String request = (String) newValue;
            try {
                SystemProperties.set(REQUEST, request);
            } catch (RuntimeException error) {
                Log.w("KeyboardHardwareLayout", "Unable to save keyboard layout", error);
                Toast.makeText(context, R.string.keyboard_hardware_layout_save_failed,
                        Toast.LENGTH_LONG).show();
                return false;
            }
            preference.setValue(request);
            updateSummary(context, preference, request);
            // Value/property are updated above. Do not use SharedPreferences.
            return false;
        });
    }

    private static void updateSummary(Context context, ListPreference preference,
            String request) {
        final String applied = SystemProperties.get(APPLIED, "");
        final int index = preference.findIndexOfValue(request);
        final CharSequence selected = preference.getEntries()[index];
        if (applied.equals("error")) {
            preference.setSummary(context.getString(
                    R.string.keyboard_hardware_layout_apply_failed, selected));
            return;
        }
        final String[] parts = applied.split(":", -1);
        if (parts.length != 2 || !isValid(parts[0]) || !isFamily(parts[1])) {
            preference.setSummary(context.getString(
                    R.string.keyboard_hardware_layout_restart, selected));
        } else if (request.equals(parts[0])) {
            preference.setSummary(context.getString(
                    R.string.keyboard_hardware_layout_current,
                    parts[1].toUpperCase(java.util.Locale.ROOT)));
        } else {
            preference.setSummary(context.getString(
                    R.string.keyboard_hardware_layout_pending, selected,
                    parts[1].toUpperCase(java.util.Locale.ROOT)));
        }
    }

    private static String normalize(String value) {
        return isValid(value) ? value : "auto";
    }

    private static boolean isValid(String value) {
        return "auto".equals(value) || isFamily(value);
    }

    private static boolean isFamily(String value) {
        return "qwerty".equals(value) || "azerty".equals(value) || "qwertz".equals(value);
    }
}
