package com.example.vrfleetagent;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Manages the WebSocket connection used to receive remote commands from the server.
 * Handles registration on open, command dispatching, and automatic reconnection.
 */
public class WebSocketManager {

    private static final String TAG = "VR_WEBSOCKET";

    // Normal closure status code as defined by the WebSocket protocol (RFC 6455).
    private static final int CLOSE_CODE_NORMAL = 1000;

    /**
     * Callback invoked when the server sends a remote command over the WebSocket.
     */
    public interface CommandListener {
        // Triggered by AgentConfig.CMD_INSTALL_APP: install the APK at the given URL.
        void onInstallCommand(String apkUrl);

        // Triggered by AgentConfig.CMD_REBOOT: reboot the device.
        void onRebootCommand();
    }

    // readTimeout = 0 keeps the socket open indefinitely waiting for server pushes.
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build();

    private final CommandListener listener;

    private WebSocket webSocket;

    // True when disconnect() was called, so we don't try to reconnect.
    private volatile boolean intentionalClose;

    public WebSocketManager(@NonNull CommandListener listener) {
        this.listener = listener;
    }

    /**
     * Opens the WebSocket connection and wires up the listener callbacks.
     */
    public void connect() {
        intentionalClose = false;

        Request request = new Request.Builder()
                .url(AgentConfig.WEBSOCKET_URL)
                .addHeader("X-Device-Serial", AgentConfig.DEVICE_SERIAL)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket socket, @NonNull Response response) {
                Log.d(TAG, "WebSocket connected.");
                sendRegistration(socket);
            }

            @Override
            public void onMessage(@NonNull WebSocket socket, @NonNull String text) {
                Log.d(TAG, "Message received: " + text);
                handleCommand(text);
            }

            @Override
            public void onFailure(@NonNull WebSocket socket, @NonNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "WebSocket failure.", t);
                if (!intentionalClose) {
                    scheduleReconnect();
                }
            }

            @Override
            public void onClosed(@NonNull WebSocket socket, int code, @NonNull String reason) {
                Log.d(TAG, "WebSocket closed: " + code + " / " + reason);
                if (!intentionalClose) {
                    scheduleReconnect();
                }
            }
        });
    }

    /**
     * Closes the connection cleanly and prevents automatic reconnection.
     */
    public void disconnect() {
        intentionalClose = true;
        if (webSocket != null) {
            webSocket.close(CLOSE_CODE_NORMAL, "Client disconnect");
            webSocket = null;
        }
    }

    // Announces this device to the server right after the connection opens.
    private void sendRegistration(@NonNull WebSocket socket) {
        try {
            JSONObject registration = new JSONObject();
            registration.put("type", "register");
            registration.put("serial", AgentConfig.DEVICE_SERIAL);
            socket.send(registration.toString());
            Log.d(TAG, "Registration sent for serial " + AgentConfig.DEVICE_SERIAL);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build registration message.", e);
        }
    }

    // Parses an incoming message and dispatches the requested command.
    private void handleCommand(String text) {
        try {
            JSONObject json = new JSONObject(text);
            String command = json.optString("command");

            switch (command) {
                case AgentConfig.CMD_INSTALL_APP:
                    listener.onInstallCommand(json.optString("url"));
                    break;
                case AgentConfig.CMD_REBOOT:
                    listener.onRebootCommand();
                    break;
                case AgentConfig.CMD_PING:
                    sendPong();
                    break;
                default:
                    Log.w(TAG, "Unknown command: " + command);
                    break;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse command message.", e);
        }
    }

    // Replies to a PING with a PONG so the server knows we are alive.
    private void sendPong() {
        if (webSocket == null) {
            return;
        }
        try {
            JSONObject pong = new JSONObject();
            pong.put("response", "PONG");
            pong.put("serial", AgentConfig.DEVICE_SERIAL);
            webSocket.send(pong.toString());
        } catch (JSONException e) {
            Log.e(TAG, "Failed to build PONG message.", e);
        }
    }

    // Waits the configured delay on a background thread, then reconnects.
    private void scheduleReconnect() {
        if (intentionalClose) {
            return;
        }
        new Thread(() -> {
            try {
                Thread.sleep(AgentConfig.WEBSOCKET_RECONNECT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!intentionalClose) {
                Log.d(TAG, "Attempting WebSocket reconnection...");
                connect();
            }
        }).start();
    }
}
