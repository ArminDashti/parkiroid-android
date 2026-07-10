## Learned User Preferences

- Use the `parkiroid` name and `/parkiroid/` API path prefix (not `samroid`)
- Prefer a dedicated Settings screen for server address, API key, and capture interval rather than inline configuration on the main screen
- Do not commit git changes unless explicitly asked

## Learned Workspace Facts

- Parkiroid is a Kotlin Android app for background parked-vehicle monitoring (front/rear camera photos, microphone sound monitoring, battery telemetry, cloud object detection via server uploads)
- Targets Android 10+ (`minSdk` 29, `targetSdk` 33); rear camera is required in the manifest, front camera and microphone optional
- Main screen: server connection status, Front/Rear Camera buttons, Settings, Logs
- Settings screen: server connect/disconnect, AI model, capture interval (ms), on-device detection toggle, screen-wake interval, detection/sending image quality, realtime FPS
- HTTP API prefix is `{base}/parkiroid/api/v1/`; client uses `POST /auth`, `POST /frame`, and `POST /device-metrics` per parkiroid-server
- Device identity is the Android ID sent as `device_id` on frame and metrics requests
- Settings persist in DataStore: server URL, API key (default `parkiroid-dev-key`), and capture period (default 15 s)
- APK export scripts: `scripts/export-apk.ps1` (Windows) and `scripts/export-apk.sh` copy builds to `exports/` with versioned filenames
- Root `.gitignore` excludes build outputs, `.gradle/`, `.idea/`, `.cursor/`, `exports/`, and `local.properties`
- Install and testing require a physical Android 10+ device with rear camera; game emulators often fail install due to required camera hardware
