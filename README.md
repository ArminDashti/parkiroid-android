# Parkiroid (Android 11+)

Minimal Kotlin Android app for low-power background monitoring of a parked vehicle. While monitoring runs, it periodically uploads rear-camera photos and battery telemetry to a configurable **Parkiroid** HTTP server, and reports bumps and violent jolts via server alarms and optional on-device SMS.

## Overview

| | |
|---|---|
| **Purpose** | Watch a parked car in the background with periodic photos, battery status, and motion alarms |
| **Platform** | Android 11+ (Kotlin), rear camera required |
| **Backend** | Parkiroid REST API on a server you configure (IP/host + port) |

## How to use

1. Install and open the app. Grant **Camera** and **SMS** when prompted (SMS is only needed for alarm texts).
2. Open **Settings** and set:
   - **Server address** — base URL, e.g. `http://192.168.1.10:8080` (no trailing slash required)
   - **Max shake magnitude** — acceleration threshold in m/s² for a violent-jolt alarm (default `30`, valid range `5`–`80`)
   - **SMS number(s)** — optional comma- or semicolon-separated numbers for alarm SMS
3. On the main screen, tap **Start Monitoring** or **Stop Monitoring**.
4. While running, a persistent notification (*Parkiroid running*) indicates the foreground service is active. Monitoring continues with the screen off.

## Background behavior

When monitoring is started, `CaptureService` runs as a foreground service with a partial wake lock and binds CameraX to the **rear** camera.

### Periodic capture (default every 15 seconds)

On each cycle the app:

1. Captures a JPEG from the rear camera (quality 65, minimize-latency mode).
2. Posts battery level (%) and battery temperature (°C).

The capture interval is stored as **15 seconds** by default. It is not exposed in the Settings UI yet; changing it requires updating the stored `period_sec` value (minimum 5 seconds in code).

### Motion alarms (accelerometer)

While monitoring, the accelerometer runs continuously. Acceleration magnitude is compared to thresholds derived from **max shake magnitude**:

| Tier | Threshold | Server endpoint | SMS (if numbers configured) |
|------|-----------|-----------------|-----------------------------|
| Jarring noise | 60% of max shake (default ~18 m/s²) | `POST .../alarm/jarring-noise` | `Parkiroid: jarring noise detected` |
| Violent jolt | Max shake (default 30 m/s²) | `POST .../alarm/violent-jolt` | `Parkiroid: violent jolt detected` |

Alarms are rate-limited to **once every 5 seconds**. Server alarm POSTs are skipped if the server URL is empty. SMS is sent only when an alarm fires and at least one phone number is configured.

## Parkiroid API

Replace `{base}` with your configured server URL (e.g. `http://192.168.1.10:8080`).

| Action | Method | Path | Body |
|--------|--------|------|------|
| Image upload | `POST` | `{base}/parkiroid/api/v1/img` | `multipart/form-data`, field `file` (JPEG) |
| Battery info | `POST` | `{base}/parkiroid/api/v1/battery/info` | JSON: `batteryPercent`, `batteryTempC` |
| Jarring alarm | `POST` | `{base}/parkiroid/api/v1/alarm/jarring-noise` | `{}` |
| Violent jolt alarm | `POST` | `{base}/parkiroid/api/v1/alarm/violent-jolt` | `{}` |

SMS alerts are sent on-device only; there is no server API for SMS.

## Battery and reliability

- Foreground service (required for reliable background camera use on Android 11+).
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
| `SEND_SMS` | Optional alarm SMS |
| `FOREGROUND_SERVICE` | Background monitoring |
| `WAKE_LOCK` | Partial wake lock while monitoring |
| `INTERNET` | Uploads and alarm POSTs |
| `ACCESS_NETWORK_STATE` | Network availability |

## Not included (yet)

- In-app gallery or local image history
- GPS / location reporting
- Authentication or HTTPS enforcement (use your server/network as appropriate)
- Capture-interval control in the Settings UI
- Remote configuration beyond the Parkiroid endpoints above
