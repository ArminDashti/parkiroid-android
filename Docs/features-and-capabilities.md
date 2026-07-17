# Features and capabilities

## Operating modes

| Mode | Behavior |
|------|----------|
| **Watchman** | Jolts, people near car, sharp sound alerts |
| **Spotter** | Watchman + tap-to-watch vehicles; departure alerts |
| **Copilot** | Road intrusion (when distance control on), overspeed, speed camera warnings (when alerts on) |
| **OFF** | No capture/detection; Camera button disabled |

## Detection

- YOLO26 NCNN on-device **always**; **person** and **car** only
- Per-mode float FPS (including 0.125) and minimum confidence (default 0.7)
- Bounding boxes on Camera eye and history frames; Spotter tap-to-watch on Camera

## Telemetry

Sent every `telemetry_interval_sec` when connected:

- GPS, speed, network, battery, cabin noise, ambient light (lux)
- CPU and RAM usage percent
- Camera frames only when upload policy is `auto` or server requests capture

## Connectivity

- Connect/Disconnect in Connectivity settings (Connected/Disconnected button; ping + API + LiveKit status above)
- Username/password login; same bearer token for API and LiveKit
- Diagnostics: Internet (1.1.1.1 + 8.8.8.8), Server (8 packets), LiveKit session
- SSL certificate warnings logged; self-signed certs accepted with warning
- Setting changes push via `PUT /api/v1/settings` when connected

## Media storage

- Per-mode processed images/video under `filesDir/detection-media/{mode}/`
- Local **frame history** ring buffer (`FrameHistoryStore`) with detections JSON; tap a frame to open viewer with previous/next
- General Settings shows usage and flush controls

## UI

- Dark Material harmony theme
- Main: button grid hub (no preview); larger buttons (Camera 80dp, others 72dp)
- CameraActivity: live eye/preview
- Section settings: Connectivity / Recording / Copilot / Spotter / Watchman / General
- HistoryFramesActivity + HistoryFrameViewerActivity from Copilot / Spotter / Watchman settings
- AI model picker on Copilot, Spotter, and Watchman (shared `ai_model`)
