# VR Fleet Agent 🥽

## What is this?
**VR Fleet Agent** is an Android background application (agent) designed specifically for **Meta Quest 2** headsets.

It is the client-side app of a custom Mobile Device Management (MDM) system. The app runs on the VR headset, reads hardware data (like battery percentage), and acts as a bridge to communicate with a central web dashboard. From that dashboard, administrators can monitor the headset and send remote commands over a live connection, such as installing new APKs silently or rebooting the device.

## Technical Specifications
- **Target Device:** Meta Quest 2 (Meta Horizon OS)
- **Programming Language:** Java
- **Target SDK:** API 36 (Android 16)
- **Minimum SDK:** API 32 (Android 12L)
- **Architecture:** Empty Views Activity (Classic UI) + background Foreground Service

## Current Features
- [x] Base project configuration for VR.
- [x] Core MDM permissions configured in AndroidManifest.
- [x] **Foreground Service** (`AgentService`): keeps the agent alive in the background with an ongoing notification.
- [x] **Device Owner setup**: device admin receiver and policies (force-lock, wipe-data) for privileged MDM operations.
- [x] **Battery telemetry**: `AgentService` periodically reports the battery level to the server.
- [x] **WebSocket command channel** (`WebSocketManager`): receives remote commands with automatic reconnection.
- [x] **Silent app installation** (`AppInstaller`): downloads APKs with `DownloadManager` and installs them via `PackageInstaller`.
- [x] **Remote reboot**: reboots the headset through `DevicePolicyManager` (Device Owner only).
- [x] **Status UI**: on-screen panel showing device serial, server IP, ping interval, and Device Owner status.

## Pending / Roadmap
- [ ] **Boot persistence**: `BOOT_COMPLETED` receiver to relaunch the agent automatically after a reboot.
- [ ] **Active app detection**: report the currently running foreground app via `UsageStatsManager`.
- [ ] **WebSocket server**: real command server endpoint (pending the DAW team).

## How to run
Since this app requires special permissions to manage the device, it must be installed via USB using ADB (Sideloading). Connect the headset, allow USB Debugging, and run the app from Android Studio.

To unlock the privileged MDM operations (silent install, remote reboot), the app must also be set as **Device Owner**:

```
adb shell dpm set-device-owner com.example.vrfleetagent/.VRDeviceAdminReceiver
```
