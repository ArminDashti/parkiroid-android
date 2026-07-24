# Features and capabilities

## Operating modes

| Mode | Behavior |
|------|----------|
| **Watchman** | Jolts, people near car, sharp sound alerts |
| **Spotter** | Watchman + tap-to-watch vehicles; departure alerts |
| **Copilot** | Road intrusion (when distance control on), overspeed, speed camera warnings (when alerts on) |
| **OFF** | No capture/detection; enter by re-tapping the active mode |

## Detection

- YOLO26 NCNN on-device when mode ≠ OFF; **person** and **car** only
- Camera duty-cycled (open → still → close) unless Preview or recording is on
- Per-mode float FPS (including 0.125) and minimum confidence (default 0.7)
- Bounding boxes on Preview and history frames (green = car, red = person, with confidence %); Spotter tap-to-watch on Preview; Watchman Preview shows jolt/sound HUD
- Activating Spotter/Watchman from the main hub starts CaptureService immediately and begins filling History + section Logs (Preview not required)

## Telemetry

Sent every `telemetry_interval_sec` when connected:

- GPS, speed, network, battery, cabin noise, ambient light (lux)
- CPU and RAM usage percent
- Camera frames only when upload policy is `auto` or server requests capture (default policy: on-demand)

## Connectivity

- Connect/Disconnect in Connectivity settings (Connected/Disconnected button; ping + API + LiveKit status above)
- Defaults: API `dogan-api.xaigrok.ir:443`, LiveKit `dogan-livekit.xaigrok.ir:443`, username `armin` / password `dogan123`
- Username/password login; same bearer token for API and LiveKit
- Diagnostics: Internet (1.1.1.1 + 8.8.8.8), Server (8 packets), LiveKit session
- SSL certificate warnings logged; self-signed certs accepted with warning
- Setting changes push via `PUT /api/v1/settings` when connected
- Section Logs (Spotter / Watchman / Record / Connectivity) filter by section; main **Logs** shows all

## Media storage

- Per-mode processed images/video under `filesDir/detection-media/{mode}/`
- Local **frame history** ring buffer (`FrameHistoryStore`) with detections JSON; tap a frame to open viewer with previous/next
- General Settings: sync interval, keep alive, keep logs, Logs button
- Flush history / Flush logs live on Spotter, Watchman, Recording, Connectivity sections

## UI

- Dark Material harmony theme
- Main: mode|gear rows, Connectivity|gear, Record|Settings, Logs|Exit; re-tap mode → OFF
- CameraActivity: Preview from section settings only when a mode is already active (bounding boxes; Watchman HUD)
- Recording: `recording_enabled` switch (default off) in Recording settings
- Section settings: Configure → Retention → Browse → Danger
- HistoryFramesActivity + HistoryFrameViewerActivity from Spotter / Watchman / Recording / Copilot
- AI model picker on Copilot, Spotter, and Watchman (shared `ai_model`)
