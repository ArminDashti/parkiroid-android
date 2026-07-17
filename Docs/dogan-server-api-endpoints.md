# Dogan API Endpoints

Base: `/dogan/api/v1`

JSON wire format is **snake_case** unless noted. Bearer token required except where Auth=No.

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | /auth | No | Login with username/password (Android) or legacy `api_key` |
| POST | /auth/login | No | Web login (email/password) → JWT + user |
| GET | /auth/me | Yes | Current authenticated user |
| POST | /auth/logout | Yes | Log out (204) |
| GET | /endpoints | No | List available API endpoints |
| GET | /health | No | Health check |
| POST | /telemetry | Yes | Android unified telemetry (metrics + optional frames) |
| POST | /frame | Yes | Upload camera frame (legacy) |
| GET | /last-frame | Yes | Latest frame metadata (`?device-id=`) |
| GET | /frame/image | Yes | Latest frame JPEG (`?device-id=`) |
| POST | /device-metrics | Yes | Submit metrics (legacy) |
| GET | /device-metrics | Yes | Latest metrics (`?device-id=`, legacy) |
| POST | /actions | Yes | Queue action for Android |
| GET | /actions/pending | Yes | Pending actions (`?device-id=`) |
| PUT | /actions/:id/ack | Yes | Acknowledge action |
| GET | /settings | Yes | Android flat map (`?device_id=`), web prefs (default/no query) |
| PUT | /settings | Yes | Upsert single `{platform,key,value}` setting |
| PATCH | /settings | Yes | Patch web preferences |
| GET | /ai-models | Yes | List registered AI models (admin) |
| POST | /ai-models | Yes | Register/update NCNN model metadata |
| GET | /models | Yes | Downloadable NCNN models (Android embeds models; optional for other clients) |
| GET | /models/:id/param | Yes | Download model.param |
| GET | /models/:id/bin | Yes | Download model.bin |
| GET | /sounds | Yes | Downloadable alert sounds for Android |
| GET | /sounds/:id | Yes | Download sound file |
| POST | /diagnostic-audio | Yes | Multipart cabin diagnostic WAV |
| GET | /webrtc/connections | Yes | Recent WebRTC sessions |
| POST | /streaming/token | Yes | Issue LiveKit token |
| POST | /webrtc/session | Yes | Create LiveKit publisher session (Android) |
| GET | /devices | Yes | List registered devices |
| GET | /devices/:id/stream | Yes | LiveKit subscriber credentials (web) |
| GET | /devices/:id/telemetry | Yes | Live telemetry snapshot (web) |
| GET | /devices/:id/metrics | Yes | Metrics history for charts (web) |
| POST | /devices/:id/capture | Yes | Queue capture; return latest frame |
| GET | /images | Yes | Gallery image list |
| GET | /images/:id | Yes | Download gallery image |

## Client base URLs (from `dogan.yml`)

| Service | Host port | URL |
|---------|-----------|-----|
| Dogan API | `8090` (`DOGAN_API_PORT`) | container listens on `8080` |
| Dogan Web | `8092` (`DOGAN_WEB_PORT`) | — |
| LiveKit | `7880` / `7881` / `7882` | public `ws://host:7880` |

- **Web:** `VITE_API_BASE_URL` = `http://host:8090/dogan/api/v1`
- **Android:** server base URL must include `/dogan` (e.g. `http://host:8090/dogan`); client appends `/api/v1/...`
- **Device API key:** `dogan-dev-key` (`DOGAN_DEVICE_API_KEY`)

## Key payload notes

### POST /telemetry (Android)

`device_id`, `recorded_at`, `gps_location`, `gps_signal_quality`, `speed_kmh`, `network_signal_strength_dbm`, `network_type`, `cabin_noise_rms`, `battery_temperature_celsius`, `battery_percentage`, `rear_camera_frame_base64`, `front_camera_frame_base64`, `ambient_light_lux`, `server_latency_ms`, `device_ip_address`

### GET /devices/:id/telemetry (web)

`device_id`, `battery_percent`, `battery_temperature_celsius`, `noise_db`, `jolt`, `signal_strength`, `network_type`, `server_phone_latency_ms`, `server_web_latency_ms`, `recorded_at`

### PATCH /settings (web)

`notifications_enabled`, `temperature_unit`, `noise_alert_threshold_db`, `default_device_id`
