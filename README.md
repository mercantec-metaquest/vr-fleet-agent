# VR Fleet Agent 🥽

## What is this?
**VR Fleet Agent** is an Android background application (agent) designed specifically for **Meta Quest 2** headsets. 

It is the client-side app of a custom Mobile Device Management (MDM) system. The app runs on the VR headset, reads hardware data (like battery percentage), and acts as a bridge to communicate with a central web dashboard. From that dashboard, administrators will be able to monitor the headset and send remote commands, such as installing new APKs silently.

## Technical Specifications
- **Target Device:** Meta Quest 2 (Meta Horizon OS)
- **Programming Language:** Java
- **Target SDK:** API 32 (Android 12L)
- **Minimum SDK:** API 29 (Android 10)
- **Architecture:** Empty Views Activity (Classic UI)

## Current Features
- [x] Base project configuration for VR.
- [x] Core MDM permissions configured in AndroidManifest.
- [x] Hardware telemetry: Reading current battery level.

## How to run
Since this app requires special permissions to manage the device, it must be installed via USB using ADB (Sideloading). Connect the headset, allow USB Debugging, and run the app from Android Studio.
