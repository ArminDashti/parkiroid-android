# Dogan (Android 10+)

Multi-mode Kotlin Android app for vehicle monitoring and driving assistance. Supports Watchman, Spotter, Watchman-Spotter, and Copilot modes with NCNN on-device AI, WebRTC streaming, and telemetry upload to a configurable **Dogan** HTTP server.

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
5. Use **Diagnostics** to test internet, server API, and WebRTC.

## Telemetry

The app uploads a fixed 14-field telemetry payload every second (default) via `POST /dogan/api/v1/telemetry`. See [Dogan Server API](Docs/dogan-server-api.md).

## Build

Open in Android Studio (Giraffe+), sync Gradle, run on a physical device with rear camera.

## Permissions

Camera, Microphone, Location, Internet, Notifications, Foreground Service (camera/microphone/location).
