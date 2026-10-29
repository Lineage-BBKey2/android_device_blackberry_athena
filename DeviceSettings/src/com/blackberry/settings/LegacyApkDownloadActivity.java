/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.blackberry.settings;

import android.app.ActionBar;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A foreground UI for a persistent download; no background installer launches. */
public class LegacyApkDownloadActivity extends CollapsingToolbarBaseActivity {
    static final String EXTRA_APP = "legacy_app_key";
    private static final String MIME_APK = "application/vnd.android.package-archive";
    private static final int REQUEST_INSTALL_APK = 1001;
    private static final String STATE_PENDING_INSTALL = "pending_install_file";
    private static final long MAX_APK_BYTES = 200L * 1024 * 1024;
    private static final long OLD_COPY_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();
    private final Runnable mPoll = this::refreshDownload;
    private LegacyAppCatalog.App mApp;
    private DownloadManager mDownloads;
    private SharedPreferences mPrefs;
    private TextView mStatus;
    private ProgressBar mProgress;
    private Button mAction;
    private Button mCancel;
    private long mDownloadId = -1;
    private File mVerified;
    private String mPendingInstallName;
    private boolean mInstallInProgress;
    private boolean mResumed;
    private boolean mVerifying;
    private boolean mFailed;
    private volatile boolean mDestroyed;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mApp = LegacyAppCatalog.find(getIntent().getStringExtra(EXTRA_APP));
        if (mApp == null) {
            finish();
            return;
        }
        setTitle(mApp.title);
        final ActionBar bar = getActionBar();
        if (bar != null) {
            bar.setTitle(mApp.title);
            bar.setDisplayHomeAsUpEnabled(true);
        }
        final ViewGroup frame = findViewById(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame);
        getLayoutInflater().inflate(R.layout.legacy_apk_download, frame, true);
        mStatus = findViewById(R.id.legacy_apk_status);
        mProgress = findViewById(R.id.legacy_apk_progress);
        mAction = findViewById(R.id.legacy_apk_action);
        mCancel = findViewById(R.id.legacy_apk_cancel);
        mDownloads = getSystemService(DownloadManager.class);
        mPrefs = getSharedPreferences("legacy_apk_downloads", MODE_PRIVATE);

        // Preserve ownership of the installer input across activity/process
        // recreation. Do not start another verification or install while waiting.
        if (state != null) {
            final String name = state.getString(STATE_PENDING_INSTALL);
            if (name != null && name.startsWith("verified-") && name.endsWith(".apk")
                    && name.indexOf('/') < 0 && name.indexOf('\\') < 0) {
                mPendingInstallName = name;
                mInstallInProgress = true;
                mVerified = new File(new File(getCacheDir(), "legacy-verified-apks"), name);
            }
        }

        mAction.setOnClickListener(v -> {
            if (mVerified != null) offerInstallation();
            else startDownload();
        });
        mCancel.setOnClickListener(v -> {
            discardDownload();
            finish();
        });

        // IDs belong to this app's DownloadManager requests. Retire old requests
        // if the ROM's configured artifact changes; never reuse them as a new APK.
        mDownloadId = mPrefs.getLong(mApp.key + ".id", -1);
        if (!mApp.url.equals(mPrefs.getString(mApp.key + ".url", ""))
                || !mApp.sha256.equals(mPrefs.getString(mApp.key + ".sha256", ""))) {
            discardDownload();
        }
        if (mInstallInProgress) {
            showInstalling();
        } else if (!configured()) {
            mStatus.setText(R.string.legacy_apk_not_configured);
            mAction.setEnabled(false);
            mCancel.setVisibility(View.GONE);
            mProgress.setVisibility(View.GONE);
        } else if (mDownloadId < 0) {
            showIdle(R.string.legacy_apk_ready_to_download);
        }
    }

    private boolean configured() {
        final Uri uri = Uri.parse(mApp.url);
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && !"example.com".equalsIgnoreCase(uri.getHost())
                && mApp.sha256.matches("(?i)[0-9a-f]{64}")
                && !mApp.sha256.matches("0{64}");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        if (mInstallInProgress) {
            showInstalling();
            return;
        }
        if (mApp != null && configured()) refreshDownload();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (mInstallInProgress) {
            state.putString(STATE_PENDING_INSTALL, mPendingInstallName);
        }
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL_APK) return;

        // Success, cancellation, and installation failure all finish the use of
        // our source file. Never interpret cancellation as a successful install.
        // The app list checks installed-package status again when it resumes.
        mInstallInProgress = false;
        mPendingInstallName = null;
        deleteVerifiedCopy();
        discardDownload();
        finish();
    }

    @Override
    protected void onPause() {
        mResumed = false;
        mHandler.removeCallbacks(mPoll);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mDestroyed = true;
        mHandler.removeCallbacksAndMessages(null);
        mWorker.shutdownNow();
        // Back/rotation must not cancel a download. If this page is closed before
        // the installer opens, its verified cache copy is no longer needed.
        // Never delete an in-flight installer's input during recreation.
        if (isFinishing() && !mInstallInProgress) deleteVerifiedCopy();
        super.onDestroy();
    }

    private void showIdle(int message) {
        mStatus.setText(message);
        mProgress.setVisibility(View.GONE);
        mAction.setVisibility(View.VISIBLE);
        mAction.setEnabled(true);
        mAction.setText(R.string.legacy_apk_download);
        mCancel.setVisibility(View.GONE);
    }

    private void startDownload() {
        if (!configured() || mVerifying) return;
        try {
            discardDownload();
            final DownloadManager.Request request = new DownloadManager.Request(
                    Uri.parse(mApp.url));
            request.setTitle(getString(mApp.title));
            request.setMimeType(MIME_APK);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            // Notify during download only; completed files are opened through
            // this activity's verification flow, not a completion auto-launch.
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS,
                    "legacy-" + mApp.key + "-" + UUID.randomUUID() + ".apk");
            mDownloadId = mDownloads.enqueue(request);
            mPrefs.edit().putLong(mApp.key + ".id", mDownloadId)
                    .putString(mApp.key + ".url", mApp.url)
                    .putString(mApp.key + ".sha256", mApp.sha256).apply();
            mFailed = false;
            refreshDownload();
        } catch (RuntimeException e) {
            showFailure(getString(R.string.legacy_apk_error, e.toString()));
        }
    }

    private void refreshDownload() {
        mHandler.removeCallbacks(mPoll);
        if (mDestroyed || !mResumed || mVerifying || mFailed
                || mInstallInProgress || mApp == null) return;
        if (mVerified != null) {
            showVerified();
            return;
        }
        if (mDownloadId < 0) return;
        try (Cursor c = mDownloads.query(
                new DownloadManager.Query().setFilterById(mDownloadId))) {
            if (c == null || !c.moveToFirst()) {
                showFailure(getString(R.string.legacy_apk_missing));
                return;
            }
            final int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                verifyDownload();
                return;
            }
            if (status == DownloadManager.STATUS_FAILED) {
                final int reason = c.getInt(
                        c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                showFailure(getString(R.string.legacy_apk_failed, reason));
                return;
            }
            final long total = c.getLong(c.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            final long bytes = c.getLong(c.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            if (bytes > MAX_APK_BYTES || total > MAX_APK_BYTES) {
                showFailure(getString(R.string.legacy_apk_too_large));
                return;
            }
            mAction.setVisibility(View.GONE);
            mCancel.setVisibility(View.VISIBLE);
            mCancel.setEnabled(true);
            mProgress.setVisibility(View.VISIBLE);
            mProgress.setIndeterminate(total <= 0);
            if (status == DownloadManager.STATUS_PAUSED) {
                mStatus.setText(R.string.legacy_apk_paused);
            } else if (status == DownloadManager.STATUS_PENDING) {
                mStatus.setText(R.string.legacy_apk_pending);
            } else if (total > 0) {
                final int percent = (int) Math.min(100, bytes * 100 / total);
                mProgress.setProgress(percent);
                mStatus.setText(getString(R.string.legacy_apk_running, percent));
            } else {
                mStatus.setText(R.string.legacy_apk_running_unknown);
            }
            mHandler.postDelayed(mPoll, 1000);
        } catch (RuntimeException e) {
            showFailure(getString(R.string.legacy_apk_error, e.toString()));
        }
    }

    private void verifyDownload() {
        mVerifying = true;
        mStatus.setText(R.string.legacy_apk_verifying);
        mProgress.setVisibility(View.VISIBLE);
        mProgress.setIndeterminate(true);
        mAction.setVisibility(View.GONE);
        mCancel.setVisibility(View.GONE);
        final long id = mDownloadId;
        mWorker.execute(() -> {
            File copy = null;
            try {
                final File dir = new File(getCacheDir(), "legacy-verified-apks");
                if (!dir.isDirectory() && !dir.mkdirs()) {
                    throw new IOException("Cannot create private APK directory");
                }
                // Retain recent copies because the system installer might still
                // be using them. Never clean unrelated cache directories.
                final File[] oldCopies = dir.listFiles();
                if (oldCopies != null) {
                    final long cutoff = System.currentTimeMillis() - OLD_COPY_AGE_MS;
                    for (File old : oldCopies) {
                        if (old.isFile() && old.getName().startsWith("verified-")
                                && old.getName().endsWith(".apk")
                                && old.lastModified() < cutoff) old.delete();
                    }
                }
                copy = File.createTempFile("verified-", ".apk", dir);
                final MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long size = 0;
                try (ParcelFileDescriptor fd = mDownloads.openDownloadedFile(id);
                        InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(fd);
                        FileOutputStream output = new FileOutputStream(copy)) {
                    final byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (mDestroyed || Thread.currentThread().isInterrupted()) {
                            throw new InterruptedIOException("Verification interrupted");
                        }
                        size += count;
                        if (size > MAX_APK_BYTES) {
                            throw new IOException(getString(R.string.legacy_apk_too_large));
                        }
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                    }
                    output.getFD().sync();
                }
                if (!MessageDigest.isEqual(digest.digest(), expectedDigest(mApp.sha256))) {
                    throw new IOException(getString(R.string.legacy_apk_bad_hash));
                }
                final PackageInfo archive = getPackageManager().getPackageArchiveInfo(
                        copy.getAbsolutePath(), 0);
                if (archive == null || !mApp.packageName.equals(archive.packageName)) {
                    throw new IOException(getString(R.string.legacy_apk_bad_package));
                }
                final File verified = copy;
                mHandler.post(() -> {
                    if (mDestroyed) {
                        verified.delete();
                        return;
                    }
                    mVerifying = false;
                    mVerified = verified;
                    showVerified();
                });
            } catch (Exception e) {
                if (copy != null) copy.delete();
                mHandler.post(() -> {
                    if (!mDestroyed) {
                        mVerifying = false;
                        showFailure(getString(R.string.legacy_apk_error, e.toString()));
                    }
                });
            }
        });
    }

    private static byte[] expectedDigest(String hex) {
        final byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private void showVerified() {
        mStatus.setText(R.string.legacy_apk_verified);
        mProgress.setVisibility(View.GONE);
        mCancel.setVisibility(View.GONE);
        mAction.setVisibility(View.VISIBLE);
        mAction.setEnabled(true);
        mAction.setText(R.string.legacy_apk_continue);
    }

    private void offerInstallation() {
        if (mInstallInProgress) return;
        if (mVerified == null || !mVerified.isFile()) {
            mVerified = null;
            showFailure(getString(R.string.legacy_apk_missing));
            return;
        }
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                return;
            }
            final Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".legacyapks", mVerified);
            final Intent install = new Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setData(uri)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            install.setClipData(ClipData.newRawUri("APK", uri));

            mPendingInstallName = mVerified.getName();
            mInstallInProgress = true;
            startActivityForResult(install, REQUEST_INSTALL_APK);
            // Remove only the original DownloadManager copy now. Keep this
            // activity and the verified private copy until the installer returns.
            discardDownload();
            showInstalling();
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException e) {
            mInstallInProgress = false;
            mPendingInstallName = null;
            showVerified();
            mStatus.setText(R.string.legacy_apk_install_unavailable);
        }
    }

    private void showInstalling() {
        mStatus.setText(R.string.legacy_apk_installing);
        mProgress.setVisibility(View.GONE);
        mAction.setVisibility(View.GONE);
        mCancel.setVisibility(View.GONE);
    }

    private void deleteVerifiedCopy() {
        if (mVerified != null && mVerified.exists() && !mVerified.delete()) {
            // Leave the existing age-based cleanup as a fallback for I/O errors.
            Log.w("LegacyApkDownload", "Could not remove verified APK cache copy");
        }
        mVerified = null;
    }

    private void showFailure(String message) {
        discardDownload();
        mFailed = true;
        mStatus.setText(message);
        mProgress.setVisibility(View.GONE);
        mCancel.setVisibility(View.GONE);
        mAction.setVisibility(View.VISIBLE);
        mAction.setEnabled(true);
        mAction.setText(R.string.legacy_apk_retry);
    }

    private void discardDownload() {
        mHandler.removeCallbacks(mPoll);
        if (mDownloadId >= 0 && mDownloads != null) {
            try {
                mDownloads.remove(mDownloadId);
            } catch (RuntimeException ignored) {
                // A disabled/removed DownloadProvider must not crash Settings.
            }
        }
        mDownloadId = -1;
        if (mPrefs != null && mApp != null) {
            mPrefs.edit().remove(mApp.key + ".id")
                    .remove(mApp.key + ".url").remove(mApp.key + ".sha256").apply();
        }
    }
}
