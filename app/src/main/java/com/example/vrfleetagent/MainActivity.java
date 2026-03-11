package com.example.vrfleetagent;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;

// OkHttp imports
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // Tag to easily find our messages in the console (Logcat)
    private static final String TAG = "VR_AGENT";

    // HTTP Client for network requests
    private final OkHttpClient httpClient = new OkHttpClient();

    // 🚨 TODO: Ací va la URL de Webhook.site per a fer proves
    private static final String API_URL = "https://webhook.site/95bbb2b3-5ba6-4921-868a-88a8436819ac";
    private static final String DEVICE_ID = "MOCK-QUEST-01";

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

        // Read the battery and trigger the network request
        readHeadsetBattery();
    }

    private void readHeadsetBattery() {
        // We "subscribe" to the system's battery status broadcast
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver(null, intentFilter);

        int batteryPct = 50; // Default fallback if reading fails

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            batteryPct = (int) ((level / (float) scale) * 100);

            Log.d(TAG, "⚡ CURRENT HEADSET BATTERY: " + batteryPct + "%");
        } else {
            Log.e(TAG, "❌ Could not read the battery. Using default 50%.");
        }

        // Send this data to the server!
        sendDataToServer(batteryPct);
    }

    private void sendDataToServer(int batteryLevel) {
        // 1. Create a simple JSON payload
        String jsonPayload = "{"
                + "\"deviceId\": \"" + DEVICE_ID + "\","
                + "\"battery\": " + batteryLevel + ","
                + "\"status\": \"online\""
                + "}";

        Log.d(TAG, "📦 Sending JSON: " + jsonPayload);

        // 2. Build the request body
        RequestBody body = RequestBody.create(
                jsonPayload,
                MediaType.parse("application/json; charset=utf-8")
        );

        // 3. Build the POST request
        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        // 4. Execute asynchronously (background thread)
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "❌ Network Error: Could not reach the server.", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "✅ Success! Server responded with HTTP " + response.code());
                } else {
                    Log.w(TAG, "⚠️ Warning! Server returned HTTP " + response.code());
                }
                // Always close the response body to avoid memory leaks
                response.close();
            }
        });
    }
}
