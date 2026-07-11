## Learned User Preferences

- Use the `dogan` name and `/dogan/` API path prefix
- Prefer a dedicated Settings screen for server address, API key, and capture interval rather than inline configuration on the main screen
- Do not commit git changes unless explicitly asked

## Learned Workspace Facts

- Dogan is a Kotlin Android app for multi-mode vehicle monitoring (Watchman, Spotter, Watchman-Spotter, Copilot)
- Targets Android 10+ (`minSdk` 29, `targetSdk` 33); rear camera required, front camera and microphone optional
- Main screen: server connection status, mode picker, Front/Rear Camera buttons, Settings, Diagnostics, Logs
- Settings screen: server connect/disconnect, operating mode, AI model, capture/telemetry intervals, on-device detection, alert volume/duration, min confidence, stream mode
- HTTP API prefix is `{base}/dogan/api/v1/`; client uses `POST /auth`, `POST /telemetry`, `GET /models`, `GET /sounds`, WebRTC session/signaling
- Device identity is the Android ID sent as `device_id` on telemetry requests
- Settings persist in DataStore: server URL, API key (default `dogan-dev-key`), capture period (default 15 s), telemetry period (default 1 s)
- NCNN models and alert sounds are downloaded from server at runtime (not embedded)
- APK export scripts: `scripts/export-apk.ps1` (Windows) and `scripts/export-apk.sh`
- Install and testing require a physical Android 10+ device with rear camera
