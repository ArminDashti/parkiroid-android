# Parkiroid (Android 10+)

Minimal Kotlin Android app for low-power background monitoring of a parked vehicle. While monitoring runs, it periodically uploads rear-camera photos and battery telemetry to a configurable **Parkiroid** HTTP server.

## Overview

| | |
|---|---|
| **Purpose** | Watch a parked car in the background with periodic photos and battery status |
| **Platform** | Android 10+ (Kotlin), rear camera required |
| **Backend** | Parkiroid REST API on a server you configure (IP/host + port) |

## How to use

1. Install and open the app. Grant **Camera** when prompted.
2. Open **Settings** and set:
   - **Server address** — base URL, e.g. `http://192.168.1.10:8080` (no trailing slash required)
   - **API key** — must match `PARKIROID_API_KEY` on the server (default dev key: `parkiroid-dev-key`)
3. On the main screen, tap **Start Monitoring** or **Stop Monitoring**.
4. While running, a persistent notification (*Parkiroid running*) indicates the foreground service is active. Monitoring continues with the screen off.

## Background behavior

When monitoring is started, `CaptureService` runs as a foreground service with a partial wake lock and binds CameraX to the **rear** camera.

### Periodic capture (default every 15 seconds)

On each cycle the app:

1. Captures a JPEG from the rear camera (quality 65, minimize-latency mode).
2. Posts battery level (%) and battery temperature (°C).

The capture interval is stored as **15 seconds** by default. In server object-detection mode, it can be configured in Settings (1–60 seconds).

## Parkiroid API

Compatible with [parkiroid-server](https://github.com/parkiroid/parkiroid-server). Replace `{base}` with your configured server URL (e.g. `http://192.168.1.10:8080`).

The app authenticates with `POST /auth`, then sends bearer tokens on protected requests. Each device is identified by its Android ID.

| Action | Method | Path | Body |
|--------|--------|------|------|
| Authenticate | `POST` | `{base}/parkiroid/api/v1/auth` | JSON: `api_key` |
| Frame upload | `POST` | `{base}/parkiroid/api/v1/frame` | JSON: `device_id`, `image_data` (base64 JPEG), `captured_at` |
| Device metrics | `POST` | `{base}/parkiroid/api/v1/device-metrics` | JSON: `device_id`, `battery_level_percent`, `temperature_celsius`, `recorded_at` |

## Battery and reliability

- Foreground service (required for reliable background camera use on Android 10+).
- Partial wake lock while monitoring.
- Single-thread executor for capture and uploads.
- Low JPEG quality and minimize-latency capture mode.
- Keep the capture interval at **15 seconds or higher** when possible for better battery life.

## Build

Open the project in Android Studio (Giraffe or newer), sync Gradle, and run on a physical device with a rear camera.

## Permissions

| Permission | Used for |
|------------|----------|
| `CAMERA` | Rear-camera capture |
| `FOREGROUND_SERVICE` | Background monitoring |
| `WAKE_LOCK` | Partial wake lock while monitoring |
| `INTERNET` | Auth, frame uploads, and device metrics |
| `ACCESS_NETWORK_STATE` | Network availability |

## Not included (yet)

- In-app gallery or local image history
- GPS / location reporting
- Motion alarms
