# Settings guide

Settings open as **focused section screens** from the main button grid. There is no global Save button: each change is written to DataStore immediately and, when connected, pushed with `PUT /api/v1/settings` (`platform=android`, `key`, `value`).

Each section (except Copilot, unchanged) follows **Configure → Retention → Browse → Danger**.

## Connectivity

| Setting | Key | Notes |
|---------|-----|-------|
| Username | `username` | Required; default `armin` |
| Password | `password` | Required; default `dogan123` |
| API URL | `api_endpoint` + `api_port` | Combined `host:port` field |
| Stream URL | `stream_endpoint` + `stream_port` | Combined `host:port` field |
| Status panel | — | Ping ms, API, LiveKit |
| Connect / Disconnect | — | Manual only; default Disconnected; no auto-connect |
| Keep logs (days) | `log_retention_days` | Shared app-wide |
| Logs / Flush history / Flush logs | — | Local actions |

## Recording

| Setting | Key | Default |
|---------|-----|---------|
| Record video | `recording_enabled` | **off** |
| Audio | `recording_sound_enabled` | on |
| FPS (+/−) | `recording_fps` | 15 |
| Frame quality | `recording_quality` | BALANCED |
| Chunk (minutes) | `recording_chunk_minutes` | 15 |
| Keep logs | `log_retention_days` | 7 |
| Preview / History / Logs | — | Browse |
| Flush history / Flush logs | — | Danger |

Recording runs only when **Record video** is on and mode ≠ OFF (keeps camera continuous while recording). Preview requires an already-active mode (does not auto-enable Copilot/Spotter/Watchman).

## Copilot

Unchanged field set (FPS, AI model, alerts, distance, history frames, alert duration, frame quality, confidence, History frames button).

## Spotter

| Setting | Key | Default |
|---------|-----|---------|
| AI model | `ai_model` | Shared |
| Confidence | `spotter_min_confidence` | 0.7 |
| FPS (+/−) | `spotter_fps_f` | 0.125 |
| Frame quality | `spotter_frame_quality` | BALANCED |
| Keep frames (hours) | `spotter_image_retention_hours` | 24 |
| Keep logs | `log_retention_days` | 7 |
| Preview / History / Logs | — | Browse |
| Flush history / Flush logs | — | Danger |

Spotter also enables Watchman alerts. Preview opens with bounding boxes.

## Watchman

| Setting | Key | Default |
|---------|-----|---------|
| AI model | `ai_model` | Shared |
| Confidence | `watcher_min_confidence` | 0.7 |
| FPS (+/−) | `watcher_fps_f` | 0.125 |
| Frame quality | `watcher_frame_quality` | BALANCED |
| Sound sensitivity | `sound_sensitivity` | Medium (Low / Medium / High / Custom) |
| Custom sound threshold | `custom_sound_threshold` | 2500 RMS (shown when Custom) |
| Jolt sensitivity | `jolt_sensitivity` | Medium (Low / Medium / High / Custom) |
| Custom jolt scale | `custom_jolt_scale` | 1.0 (shown when Custom) |
| Keep frames (hours) | `watcher_image_retention_hours` | 24 |
| Keep logs | `log_retention_days` | 7 |
| Preview (boxes + jolt/sound HUD) / History / Logs | — | Browse |
| Flush history / Flush logs | — | Danger |

UI label is **Watchman**; stored/API mode value remains `watcher`.

## Settings (general)

| Setting | Key | Notes |
|---------|-----|-------|
| Sync interval (sec) | `settings_sync_interval_sec` | Default **15** (min 10) |
| Keep alive (min) | `screen_on_interval_min` | 0 = off |
| Keep logs (days) | `log_retention_days` | |
| Logs | — | Opens LogsActivity |

## Sync behavior

- Device → server: every local change when connected
- Server → device: `ServerSettingsSync` polls `GET /api/v1/settings` every sync interval while connected
- Username/password are local only (not in full snapshot push)
