package com.example.vrfleetagent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Long-running foreground service that keeps the VR headset under management:
 * it periodically reports telemetry to the server and reacts to remote
 * commands (install app, reboot) delivered through {@link WebSocketManager}.
 */
public class AgentService extends Service implements WebSocketManager.CommandListener {

    private static final String TAG = "VR_SERVICE";

    // Intent action used by PackageInstaller to report install progress back to us.
    private static final String ACTION_INSTALL_STATUS = "INSTALL_STATUS";

    // Keep the CPU awake for at most 10 minutes per acquisition.
    private static final long WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L;

    private final OkHttpClient httpClient = new OkHttpClient();

    // Handler bound to the main looper that drives the periodic telemetry loop.
    private final Handler telemetryHandler = new Handler(Looper.getMainLooper());

    // Self-rescheduling task: sends telemetry, then queues itself again.
    private final Runnable telemetryRunnable = new Runnable() {
        @Override
        public void run() {
            sendTelemetry();
            telemetryHandler.postDelayed(this, AgentConfig.TELEMETRY_INTERVAL_MS);
        }
    };

    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();

        // Warn early if we lack Device Owner privileges (some commands need them).
        logDeviceOwnerStatus();

        acquireWakeLock();
        startForegroundService();

        // Kick off the periodic telemetry loop immediately.
        telemetryHandler.post(telemetryRunnable);
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // Re-entry from PackageInstaller carrying the result of an install session.
        if (intent != null && ACTION_INSTALL_STATUS.equals(intent.getAction())) {
            handleInstallStatus(intent);
        }

        // Ask the system to recreate the service if it gets killed.
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // This is a started service, not a bound one.
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        telemetryHandler.removeCallbacks(telemetryRunnable);
        releaseWakeLock();
    }

    // --- Foreground service setup ---

    private void startForegroundService() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Notification channels are required from Android O onwards.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    AgentConfig.NOTIFICATION_CHANNEL_ID,
                    AgentConfig.NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, AgentConfig.NOTIFICATION_CHANNEL_ID)
                .setContentTitle(AgentConfig.NOTIFICATION_CHANNEL_NAME)
                .setContentText("Fleet agent is running")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(AgentConfig.NOTIFICATION_ID, notification);
    }

    // --- Wake lock ---

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            Log.e(TAG, "PowerManager unavailable, cannot acquire wake lock.");
            return;
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VRFleetAgent::AgentWakeLock");
        // Bound the wake lock so a stuck service can't drain the battery forever.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);
        Log.d(TAG, "Partial wake lock acquired (max " + WAKE_LOCK_TIMEOUT_MS + " ms).");
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "Wake lock released.");
        }
    }

    // --- Telemetry ---

    private void sendTelemetry() {
        int batteryPct = readBatteryLevel();

        FormBody body = new FormBody.Builder()
                .add("numero_serie", AgentConfig.DEVICE_SERIAL)
                .add("bateria", String.valueOf(batteryPct))
                .add("app_activa", "Agent Service")
                .build();

        Request request = new Request.Builder()
                .url(AgentConfig.TELEMETRY_URL)
                .post(body)
                .build();

        Log.d(TAG, "Sending telemetry (battery " + batteryPct + "%)...");

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Telemetry request failed.", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Telemetry sent (HTTP " + response.code() + ").");
                } else {
                    Log.w(TAG, "Telemetry rejected by server: HTTP " + response.code());
                }
                response.close();
            }
        });
    }

    private int readBatteryLevel() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, filter);

        if (batteryStatus == null) {
            Log.e(TAG, "Could not read battery, using default 50%.");
            return 50;
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            Log.e(TAG, "Invalid battery values, using default 50%.");
            return 50;
        }
        return (int) ((level / (float) scale) * 100);
    }

    // --- Device Owner ---

    private void logDeviceOwnerStatus() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
            Log.d(TAG, "Running as Device Owner: full MDM capabilities available.");
        } else {
            Log.w(TAG, "Not a Device Owner: privileged commands (e.g. reboot) will be unavailable.");
        }
    }

    // --- Remote command callbacks (WebSocketManager.CommandListener) ---

    @Override
    public void onInstallCommand(String apkUrl) {
        Log.d(TAG, "Install command received for: " + apkUrl);
        installApk(apkUrl);
    }

    @Override
    public void onRebootCommand() {
        Log.d(TAG, "Reboot command received.");
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        if (dpm == null || !dpm.isDeviceOwnerApp(getPackageName())) {
            Log.w(TAG, "Reboot ignored: app is not Device Owner.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ComponentName admin = new ComponentName(this, VRDeviceAdminReceiver.class);
            dpm.reboot(admin);
        } else {
            Log.w(TAG, "Reboot not supported below Android N.");
        }
    }

    // --- App installation via PackageInstaller ---

    private void installApk(String apkUrl) {
        // Download and install off the main thread to avoid blocking the looper.
        new Thread(() -> {
            Request request = new Request.Builder().url(apkUrl).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.e(TAG, "APK download failed: HTTP " + response.code());
                    return;
                }

                PackageInstaller installer = getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                int sessionId = installer.createSession(params);

                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    try (OutputStream out = session.openWrite("vr_app.apk", 0, -1);
                         InputStream in = response.body().byteStream()) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        session.fsync(out);
                    }
                    session.commit(buildInstallStatusSender());
                }
            } catch (IOException e) {
                Log.e(TAG, "APK installation failed.", e);
            }
        }).start();
    }

    private android.content.IntentSender buildInstallStatusSender() {
        Intent statusIntent = new Intent(this, AgentService.class).setAction(ACTION_INSTALL_STATUS);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // PackageInstaller fills in extras, so the PendingIntent must be mutable.
            flags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, statusIntent, flags);
        return pendingIntent.getIntentSender();
    }

    private void handleInstallStatus(@NonNull Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                // The system needs the user to confirm the install.
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(confirm);
                }
                break;
            case PackageInstaller.STATUS_SUCCESS:
                Log.d(TAG, "App installed successfully.");
                break;
            default:
                Log.e(TAG, "App install failed: status=" + status + ", message=" + message);
                break;
        }
    }
}
