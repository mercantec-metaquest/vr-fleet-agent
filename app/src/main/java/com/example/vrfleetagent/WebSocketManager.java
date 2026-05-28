package com.example.vrfleetagent;

/**
 * Manages the WebSocket connection used to receive remote commands from the server.
 *
 * NOTE: this is currently a contract stub. It defines the {@link CommandListener}
 * interface that {@link AgentService} implements; the actual connection logic
 * (connect / reconnect / message parsing) is expected to be added later.
 */
public class WebSocketManager {

    /**
     * Callback invoked when the server sends a remote command over the WebSocket.
     */
    public interface CommandListener {
        // Triggered by AgentConfig.CMD_INSTALL_APP: install the APK at the given URL.
        void onInstallCommand(String apkUrl);

        // Triggered by AgentConfig.CMD_REBOOT: reboot the device.
        void onRebootCommand();
    }
}
