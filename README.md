# Dogan (Android 10+)

Multi-mode Kotlin Android app for vehicle monitoring and driving assistance. Supports Watchman, Spotter, Watchman-Spotter, and Copilot modes with NCNN on-device AI, LiveKit WebRTC streaming, and telemetry upload to a configurable **Dogan** HTTP server.

## Overview

| | |
|---|---|
| **Purpose** | Parked-vehicle security, departure watch, and driving copilot assistance |
| **Platform** | Android 10+ (Kotlin), rear camera required |
| **Backend** | Dogan REST API on a server you configure |

## Modes

- **Watchman** — Detects jolts, people near car, sharp sounds
- **Spotter** — Watch selected parked vehicles; alert when they leave
- **Watchman-Spotter** — Both modes simultaneously
- **Copilot** — Driving assistance: intrusion warnings, speed limits, speed cameras

## How to use

1. Install and open the app. Grant Camera, Microphone, and Location permissions.
2. Open **Settings** and set server address and API key (`dogan-dev-key` default).
3. Select operating mode on the main screen.
4. Connect to server in Settings, then select a camera to start monitoring.
5. Use **Diagnostics** to test internet, server API, and LiveKit WebRTC session.

## Telemetry

The app uploads a fixed 14-field telemetry payload every second (default) via `POST /api/v1/telemetry`. See [Dogan Server API](Docs/dogan-server-api.md).

## Build

Open in Android Studio (Giraffe+), sync Gradle, run on a physical device with rear camera.

The first Gradle build downloads Tencent NCNN prebuilt libraries (`app/download_ncnn.gradle`) and compiles the native `dogan_ncnn` JNI library. Models themselves are **not** bundled; they are fetched from the Dogan server at runtime.

Server base URL must include `/dogan` (e.g. `http://192.168.1.10:8090/dogan`). Docker compose publishes the API on host port **8090**.

## Permissions

Camera, Microphone, Location, Internet, Notifications, Foreground Service (camera/microphone/location).
