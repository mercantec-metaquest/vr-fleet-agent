package com.example.vrfleetagent;

import android.os.Build;

/**
 * Centralized configuration for the VR Fleet Agent.
 * Keeps all server endpoints, timing values and command identifiers in one place.
 */
public final class AgentConfig {

    // Prevent instantiation: this class only holds constants.
    private AgentConfig() {
    }

    // --- Server ---
    public static final String SERVER_IP = "192.168.115.211";

    // HTTP endpoint used to push telemetry data (battery, status, etc.)
    public static final String TELEMETRY_URL = "http://" + SERVER_IP + "/backend-api/api.php";

    // WebSocket endpoint used to receive remote commands (runs on port 3000)
    public static final String WEBSOCKET_URL = "ws://" + SERVER_IP + ":3000";

    // --- Device identity ---
    // Hardware serial number used to identify this headset on the server.
    public static final String DEVICE_SERIAL = Build.SERIAL;

    // --- Timing ---
    // How often telemetry data is sent to the server (milliseconds).
    public static final long TELEMETRY_INTERVAL_MS = 30000;

    // Delay before attempting to reconnect a dropped WebSocket (milliseconds).
    public static final long WEBSOCKET_RECONNECT_DELAY_MS = 5000;

    // --- Notification ---
    public static final String NOTIFICATION_CHANNEL_ID = "vr_fleet_agent_channel";
    public static final String NOTIFICATION_CHANNEL_NAME = "VR Fleet Agent";
    public static final int NOTIFICATION_ID = 1001;

    // --- Remote commands ---
    public static final String CMD_INSTALL_APP = "INSTALL_APP";
    public static final String CMD_REBOOT = "REBOOT";
    public static final String CMD_PING = "PING";
}
