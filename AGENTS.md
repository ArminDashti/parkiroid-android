## Learned User Preferences

- Use the `dogan` name and `/api/v1/` API path prefix
- Prefer a dedicated Settings screen for endpoints and mode options rather than inline configuration on the main screen
- Do not commit git changes unless explicitly asked

## Learned Workspace Facts

- Dogan is a Kotlin Android app for multi-mode vehicle monitoring (Watchman, Spotter, Copilot, OFF)
- Spotter mode also runs Watchman engine (bump/sound/person alerts plus vehicle departure watch)
- Targets Android 10+ (`minSdk` 29, `targetSdk` 33); rear camera required, front camera and microphone optional
- Main screen is a dark button grid (no live preview): Copilot|gear, Spotter|gear, Watchman|gear, Connectivity|gear, Record|Settings, Logs|Exit; re-tap active mode → OFF
- Preview opens from section settings only when a mode is already active (CameraActivity); Watchman preview shows jolt/sound HUD; Exit kills the process
- Connect/Disconnect lives in Connectivity settings (manual only; default Disconnected; no auto-connect); API URL / Stream URL are combined host:port fields
- Settings are section screens (Connectivity, Recording, Copilot, Spotter, Watchman, General) ordered Configure→Retention→Browse→Danger; each change auto-saves to DataStore and `PUT /api/v1/settings` when connected
- On-device YOLO detection runs when mode ≠ OFF; defaults: operating mode OFF, recording off (`recording_enabled`), image upload on-demand, sync 15s, min confidence 0.7, Watchman/Spotter FPS 0.125, Copilot FPS 4, recording FPS 15 / chunk 15 min / retention ≤12h
- Capture camera is duty-cycled (open → still → close) unless Preview is open or recording is enabled
- Frame quality UI: LOW / BALANCED / HIGH / ORIGINAL; FPS adjusted with +/- steppers
- Copilot/Spotter/Watchman settings share AI model picker (`ai_model`); Spotter/Watchman/Recording link to History, Logs, Preview; HistoryFramesActivity viewer with prev/next
- HTTP API prefix is `{base}/api/v1/`; auth is username/password via `POST /auth` (defaults `armin`/`dogan123` in `credentials.txt`); same bearer for API and LiveKit (`POST /webrtc/session`)
- Default Connectivity hosts: API `dogan-api.xaigrok.ir:443`, LiveKit `dogan-livekit.xaigrok.ir:443`
- Telemetry includes `ambient_light_lux`, `cpu_usage_percent`, `ram_usage_percent`
- Detection filters to **person** and **car** only; Preview/history boxes are green (car) / red (person) with confidence; SSL cert issues log warnings and use permissive trust for self-hosted servers
- Local Docker: API **8090**, LiveKit **7880**; Android `http://<host-ip>:8090/dogan`
- While connected, `ServerSettingsSync` polls `GET /api/v1/settings` every sync interval (default 15s)
- `CaptureService` runs when mode ≠ OFF; activating Spotter/Watchman fills History and Logs immediately; YOLO26 NCNN models embedded from `ai-models/` (~124 MB APK)
- Section Logs filter by mode/area; main Logs shows all
- APK export: `scripts/export-apk.ps1` / `scripts/export-apk.sh`
- APK USB/ADB install: `install-on-phone-directly.ps1` (root; rebuilds by default; `-SkipBuild` / `-Launch` optional) / `scripts/install-apk.sh`
