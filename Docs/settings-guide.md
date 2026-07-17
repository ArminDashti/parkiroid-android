# Settings guide

Settings open as **focused section screens** from the main button grid. There is no global Save button: each change is written to DataStore immediately and, when connected, pushed with `PUT /api/v1/settings` (`platform=android`, `key`, `value`).

## Connectivity

| Setting | Key | Notes |
|---------|-----|-------|
| Status panel | — | Ping ms, API Connected/Disconnected, LiveKit Connected/Disconnected (session count) |
| Connect / Disconnect | — | Button shows only Connected (green) or Disconnected (red); errors appear above the button |
| Username | `username` | Required; default `armin` (see repo `credentials.txt`) |
| Password | `password` | Required; same credentials authorize API and LiveKit (`POST /auth` → bearer) |
| API endpoint | `api_endpoint` | Hostname or IP |
| API port | `api_port` | Default 443 (8090 for local Docker) |
| Stream endpoint | `stream_endpoint` | LiveKit host |
| Stream port | `stream_port` | Default 7880 |
| Telemetry interval (sec) | `telemetry_interval_sec` | Send metrics every N seconds |

## Recording

| Setting | Key | Default |
|---------|-----|---------|
| FPS (+/−) | `recording_fps` | 15 |
| Chunk (minutes) | `recording_chunk_minutes` | 15 |
| Quality | `recording_quality` | LOW / BALANCED / HIGH / ORIGINAL |
| Sound | `recording_sound_enabled` | on |
| Retention (hours, max 12) | `recording_retention_hours` | 12 |

Recording runs while mode ≠ OFF.

## Copilot

| Setting | Key | Default |
|---------|-----|---------|
| FPS (+/−) | `copilot_fps` (stored as `copilot_fps_f`) | 4 |
| AI model | `ai_model` | YOLO26 nano |
| Copilot alert | `copilot_alerts_enabled` | false |
| Distance control | `copilot_distance_control_enabled` | false |
| History retention (frames) | `copilot_history_retention_frames` | 100 |
| Alert duration | `alert_duration` | 3s |
| Frame quality | `copilot_frame_quality` | BALANCED |
| Min confidence | `copilot_min_confidence` | 0.7 |

**History frames** opens the local gallery for Copilot.

## Spotter

| Setting | Key | Default |
|---------|-----|---------|
| FPS (+/−) | `spotter_fps_f` | 0.125 |
| AI model | `ai_model` | Shared with Copilot/Watchman |
| History retention | `spotter_history_retention_frames` | 100 |
| Frame quality | `spotter_frame_quality` | BALANCED |
| Min confidence | `spotter_min_confidence` | 0.7 |

Spotter also enables Watchman alerts. **History frames** opens the local gallery; tap a frame to view with previous/next.

## Watchman

| Setting | Key | Default |
|---------|-----|---------|
| FPS (+/−) | `watcher_fps_f` | 0.125 |
| AI model | `ai_model` | Shared with Copilot/Spotter |
| History retention | `watcher_history_retention_frames` | 100 |
| Jolt sensitivity | `jolt_sensitivity` | Medium |
| Sound sensitivity | `sound_sensitivity` | Medium |
| Frame quality | `watcher_frame_quality` | BALANCED |
| Min confidence | `watcher_min_confidence` | 0.7 |

UI label is **Watchman**; stored/API mode value remains `watcher`. **History frames** opens the local gallery; tap a frame to view with previous/next.

## Settings (general)

| Setting | Key | Notes |
|---------|-----|-------|
| Sync interval (sec) | `settings_sync_interval_sec` | Default **15** (min 10) |
| Camera | `active_camera` | Rear / Front / Both |
| Keep alive (min) | `screen_on_interval_min` | 0 = off |
| Log retention (days) | `log_retention_days` | |
| Show detection boxes | `show_bounding_boxes` | Overlay preference |
| Storage flush | — | Spotter / Watchman / Copilot / Logs |

On-device detection is **always enabled** (no toggle).

## Main screen

- Mode tabs: Copilot | Spotter | Watchman | OFF
- Camera (eye) disabled when OFF
- Connect only in Connectivity (username/password; Connected/Disconnected button)
- Exit closes the app completely
