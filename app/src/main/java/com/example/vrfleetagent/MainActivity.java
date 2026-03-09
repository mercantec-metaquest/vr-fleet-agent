package com.example.vrfleetagent;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    // Tag to easily find our messages in the console (Logcat)
    private static final String TAG = "VR_AGENT";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- ANDROID STUDIO VISUAL CODE ---
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // ---------------------------------------------------

        // HERE BEGINS OUR MAGIC!
        // Call the function to read the battery as soon as the app opens
        readHeadsetBattery();
    }

    private void readHeadsetBattery() {
        // We "subscribe" to the system's battery status broadcast
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            // Get the current level and the maximum level (scale)
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            // Calculate the percentage
            int batteryPct = (int) ((level / (float) scale) * 100);

            // Print it to the Android Studio console (Logcat)
            Log.d(TAG, "⚡ CURRENT HEADSET BATTERY: " + batteryPct + "%");
        } else {
            Log.e(TAG, "❌ Could not read the battery.");
        }
    }
}
