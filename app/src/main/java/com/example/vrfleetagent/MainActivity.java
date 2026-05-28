package com.example.vrfleetagent;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

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

        // Start the long-running background agent (telemetry + remote commands).
        startForegroundService(new Intent(this, AgentService.class));

        // Show the current configuration and Device Owner status on screen.
        updateStatusUI();
    }

    // Renders the agent configuration and Device Owner status into the status TextView.
    private void updateStatusUI() {
        TextView statusText = findViewById(R.id.statusText);
        if (statusText == null) {
            return;
        }

        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        boolean isDeviceOwner = dpm != null && dpm.isDeviceOwnerApp(getPackageName());

        String status = "Device Serial: " + AgentConfig.DEVICE_SERIAL + "\n"
                + "Server IP: " + AgentConfig.SERVER_IP + "\n"
                + "Ping Interval: " + (AgentConfig.TELEMETRY_INTERVAL_MS / 1000) + "s\n"
                + "Device Owner: " + (isDeviceOwner ? "YES" : "NO");

        statusText.setText(status);
    }
}
