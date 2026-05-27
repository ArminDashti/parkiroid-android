# Parkiroid (Android 11+)

Minimal Kotlin Android app focused on low-power background monitoring for parked vehicles.

## Features
- Rear camera capture in background every N seconds (default 15 sec).
- Upload image to:
  - `<SERVER-IP>/samroid/api/v1/img`
- Upload battery info and battery temperature to:
  - `<SERVER-IP>/samroid/api/v1/battery/info`
- Vibration/jolt detection with accelerometer alarms:
  - `<SERVER-IP>/samroid/api/v1/alarm/jarring-noise`
  - `<SERVER-IP>/samroid/api/v1/alarm/violent-jolt`
- SMS alerts on device (no server API): optional alarm SMS to comma-separated numbers in settings
- Works while screen is off via Foreground Service + partial wakelock.
- Minimal settings UI:
  - Server URL (IP + port)
  - Capture period in seconds (default 15)

## Notes on battery optimization
- Uses low JPEG quality and minimize latency camera mode.
- Uses single-thread executor.
- Uses foreground service (required for reliable background camera usage on Android 11+).
- Keep capture interval at 15s or higher for better battery life.

## Build
Open in Android Studio (Giraffe+), sync Gradle, and run on device.

## Permissions
- CAMERA
- SEND_SMS
- FOREGROUND_SERVICE
- WAKE_LOCK
- INTERNET
- ACCESS_NETWORK_STATE
