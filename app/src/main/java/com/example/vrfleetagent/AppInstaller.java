package com.example.vrfleetagent;

import android.app.DownloadManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Downloads an APK from a remote URL and installs it silently through
 * {@link PackageInstaller}. Silent installation only succeeds when this app
 * is the Device Owner; otherwise a SecurityException is raised.
 */
public class AppInstaller {

    private static final String TAG = "VR_INSTALLER";

    // Action used by the PendingIntent so PackageInstaller reports back to AgentService.
    private static final String ACTION_INSTALL_STATUS = "INSTALL_STATUS";

    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static final String APK_FILE_NAME = "vr_app.apk";

    // 64 KiB copy buffer.
    private static final int BUFFER_SIZE = 65536;

    private final Context context;

    private long downloadId = -1L;
    private BroadcastReceiver downloadReceiver;

    public AppInstaller(@NonNull Context context) {
        // Use the application context to avoid leaking the calling component.
        this.context = context.getApplicationContext();
    }

    /**
     * Entry point: enqueues the APK download. Installation continues from the
     * download-complete broadcast once the file is ready.
     */
    public void receiveInstallCommand(String apkUrl) {
        DownloadManager downloadManager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Log.e(TAG, "DownloadManager is unavailable.");
            return;
        }

        Log.d(TAG, "Queuing APK download from: " + apkUrl);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setMimeType(APK_MIME_TYPE);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME);

        registerDownloadReceiver();
        downloadId = downloadManager.enqueue(request);
        Log.d(TAG, "Download enqueued with id " + downloadId);
    }

    // Listens for the download-complete broadcast for our specific download id.
    private void registerDownloadReceiver() {
        if (downloadReceiver != null) {
            return;
        }

        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long receivedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (receivedId != downloadId) {
                    // A different download finished; ignore it.
                    return;
                }

                if (isDownloadSuccessful(receivedId)) {
                    Uri apkUri = getDownloadedApkUri(receivedId);
                    if (apkUri != null) {
                        installApk(apkUri);
                    } else {
                        Log.e(TAG, "Could not resolve the downloaded APK URI.");
                    }
                }

                unregisterDownloadReceiver();
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        // Not exported: only the system DownloadManager should reach this receiver.
        ContextCompat.registerReceiver(context, downloadReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterDownloadReceiver() {
        if (downloadReceiver == null) {
            return;
        }
        try {
            context.unregisterReceiver(downloadReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver was already unregistered; safe to ignore.
        }
        downloadReceiver = null;
    }

    private boolean isDownloadSuccessful(long id) {
        DownloadManager downloadManager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            return false;
        }

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (Cursor cursor = downloadManager.query(query)) {
            if (cursor != null && cursor.moveToFirst()) {
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    return true;
                }
                Log.e(TAG, "Download finished but was not successful, status=" + status);
            }
        }
        return false;
    }

    @Nullable
    private Uri getDownloadedApkUri(long id) {
        DownloadManager downloadManager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            return null;
        }
        return downloadManager.getUriForDownloadedFile(id);
    }

    // Streams the downloaded APK into a PackageInstaller session and commits it.
    private void installApk(@NonNull Uri apkUri) {
        Log.d(TAG, "Installing APK from " + apkUri);

        ContentResolver resolver = context.getContentResolver();
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();

        PackageInstaller.Session session = null;
        try {
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            int sessionId = packageInstaller.createSession(params);
            session = packageInstaller.openSession(sessionId);

            try (InputStream in = resolver.openInputStream(apkUri);
                 OutputStream out = session.openWrite(APK_FILE_NAME, 0, -1)) {
                if (in == null) {
                    Log.e(TAG, "Unable to open the downloaded APK for reading.");
                    return;
                }
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                session.fsync(out);
            }

            session.commit(buildInstallStatusSender());
            Log.d(TAG, "Install session committed; awaiting status broadcast.");
        } catch (SecurityException e) {
            // Silent install is only permitted for the Device Owner.
            Log.e(TAG, "Silent install denied. This app must be the Device Owner. "
                    + "Grant it via ADB:\n"
                    + "adb shell dpm set-device-owner "
                    + "com.example.vrfleetagent/.VRDeviceAdminReceiver", e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write the APK to the install session.", e);
        } finally {
            if (session != null) {
                session.close();
            }
        }
    }

    private IntentSender buildInstallStatusSender() {
        Intent statusIntent = new Intent(context, AgentService.class).setAction(ACTION_INSTALL_STATUS);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // PackageInstaller fills in result extras, so the PendingIntent must be mutable.
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, statusIntent, flags);
        return pendingIntent.getIntentSender();
    }
}
