## Learned User Preferences

- Use the `dogan` name and `/api/v1/` API path prefix
- Prefer a dedicated Settings screen for server address, API key, and capture interval rather than inline configuration on the main screen
- Do not commit git changes unless explicitly asked

## Learned Workspace Facts

- Dogan is a Kotlin Android app for multi-mode vehicle monitoring (Watchman, Spotter, Watchman-Spotter, Copilot)
- Targets Android 10+ (`minSdk` 29, `targetSdk` 33); rear camera required, front camera and microphone optional
- Main screen: server connection status, mode picker, Front/Rear Camera buttons, Settings, Diagnostics, Logs
- Settings screen: server connect/disconnect, operating mode, AI model, capture/telemetry intervals, keep-alive screen wake interval (minutes), server settings sync interval (seconds), on-device detection, alert volume/duration, min confidence, stream mode
- HTTP API prefix is `{base}/api/v1/`; client uses `POST /auth`, `POST /telemetry`, `GET /models`, `GET /sounds`, LiveKit via `POST /webrtc/session` or `POST /streaming/token`
- Device identity is the Android ID sent as `device_id` on telemetry requests
- Settings persist in DataStore: server URL, API key (default `dogan-dev-key`), capture period (default 15 s), telemetry period (default 1 s), screen wake interval in minutes (default 0 = off), settings sync interval (default 60 s)
- While connected, `ServerSettingsSync` polls `GET /api/v1/settings` and merges server-side operational settings
- NCNN models and alert sounds are downloaded from server at runtime (not embedded); YOLOv8 models load into Tencent NCNN via JNI (`dogan_ncnn`)
- APK export scripts: `scripts/export-apk.ps1` (Windows) and `scripts/export-apk.sh`
- Install and testing require a physical Android 10+ device with rear camera
