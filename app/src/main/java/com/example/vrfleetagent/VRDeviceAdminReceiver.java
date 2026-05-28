package com.example.vrfleetagent;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Device admin receiver for the VR Fleet Agent.
 * Receiving admin privileges lets the agent enforce MDM policies such as
 * force-lock and wipe-data (see res/xml/device_admin.xml).
 */
public class VRDeviceAdminReceiver extends DeviceAdminReceiver {

    private static final String TAG = "VR_ADMIN";

    @Override
    public void onEnabled(@NonNull Context context, @NonNull Intent intent) {
        super.onEnabled(context, intent);
        Log.d(TAG, "Device admin enabled.");
    }

    @Override
    public void onDisabled(@NonNull Context context, @NonNull Intent intent) {
        super.onDisabled(context, intent);
        Log.d(TAG, "Device admin disabled.");
    }

    @Override
    public CharSequence onDisableRequested(@NonNull Context context, @NonNull Intent intent) {
        Log.w(TAG, "Device admin disable requested.");
        return "Disabling admin will remove remote management of this VR headset.";
    }
}
