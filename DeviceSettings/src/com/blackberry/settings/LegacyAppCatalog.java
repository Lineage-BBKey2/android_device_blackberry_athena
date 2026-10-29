/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.blackberry.settings;

/** Only this compiled-in catalog may choose a package, URL, or expected digest. */
final class LegacyAppCatalog {
    static final class App {
        final String key;
        final int title;
        final String packageName;
        final String url;
        final String sha256;

        App(String key, int title, String packageName, String url, String sha256) {
            this.key = key;
            this.title = title;
            this.packageName = packageName;
            this.url = url;
            this.sha256 = sha256;
        }
    }

    static final App KEYBOARD = new App(
            "blackberry_keyboard", R.string.blackberry_keyboard_title,
            "com.blackberry.keyboard",
            "https://archive.org/download/bbpsapps/com.blackberry.keyboard.apk",
            "dc5af5e1b2ac26c5f02ac56322b547072c520e3fcb77af0fe2b3bde8c737efd3");

    static final App LAUNCHER = new App(
            "blackberry_launcher", R.string.blackberry_launcher_title,
            "com.blackberry.blackberrylauncher",
            "https://archive.org/download/bbpsapps/com.blackberry.blackberrylauncher.apk",
            "3a6ade58e2890052897334e42db321adacf422dfffd3949ff796f78f351bbb63");

    static App find(String key) {
        if (KEYBOARD.key.equals(key)) return KEYBOARD;
        if (LAUNCHER.key.equals(key)) return LAUNCHER;
        return null;
    }

    private LegacyAppCatalog() {}
}
