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
import okhttp3.FormBody;

public class MainActivity extends AppCompatActivity {

    // Tag to easily find our messages in the console (Logcat)
    private static final String TAG = "VR_AGENT";

    // HTTP Client for network requests
    private final OkHttpClient httpClient = new OkHttpClient();

    // TODO: Here is the URL of Webhook.site for testing
    //private static final String API_URL = "https://webhook.site/95bbb2b3-5ba6-4921-868a-88a8436819ac";
    //private static final String DEVICE_ID = "MOCK-QUEST-01";
    // Local IP
    private static final String API_URL = "http://192.168.115.211/backend-api/api.php";
    private static final String DEVICE_ID = "TEST-001";

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
        // Instead of JSON, we build a "form" just like JavaScript's URLSearchParams
        FormBody body = new FormBody.Builder()
                .add("numero_serie", DEVICE_ID)
                .add("bateria", String.valueOf(batteryLevel))
                .add("app_activa", "Main Menu")
                .build();

        Log.d(TAG, "📦 Sending Form Data to server API...");

        // We built the POST request
        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .build();

        // We run it in the background
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "❌ Network Error: Cannot connect to the server IP. Are you on the same WiFi?", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    // If the PHP returns text (like an echo json_encode), we can read it like this:
                    String responseBody = response.body() != null ? response.body().string() : "No body";
                    Log.d(TAG, "✅ Success! Server responded (HTTP " + response.code() + "): " + responseBody);
                } else {
                    Log.w(TAG, "⚠️ Warning! Server connected but returned an error: " + response.code());
                }
                response.close();
            }
        });
    }
}
