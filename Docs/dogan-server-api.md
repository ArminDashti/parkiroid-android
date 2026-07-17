# Dogan Server API Specification

API prefix: `{base}/api/v1/` where `{base}` is the server root including `/dogan` (e.g. `http://192.168.1.10:8090/dogan`). Docker host port is **8090** (`DOGAN_API_PORT`).

## Authentication

### POST /auth

Android request (username/password only):
```json
{ "username": "armin", "password": "dogan123" }
```

Admin/web may still accept the same username/password. Server also supports legacy device `{ "api_key": "..." }` for other clients; the Android app does not use API-key login.

Response:
```json
{ "token": "<bearer>", "expires_at": "2026-07-11T12:00:00Z" }
```

The bearer token authorizes REST calls and LiveKit session creation.

## Health

### GET /health

Returns HTTP 200 when the server is reachable. Used for latency measurement.

## Models (NCNN)

> **Android note:** The Dogan Android app embeds YOLO26 NCNN weights (`yolo26_nano`, `yolo26_small`, `yolo26_medium`) in the APK and no longer calls these download endpoints. Server model APIs remain available for other clients or tooling.

### GET /models

Requires bearer token. Returns only models that have both `model.param` and `model.bin` on the server.

Response:
```json
{
  "models": [
    {
      "id": "yolo26_nano",
      "param_url": "http://host:8090/dogan/api/v1/models/yolo26_nano/param",
      "bin_url": "http://host:8090/dogan/api/v1/models/yolo26_nano/bin",
      "param_sha256": "...",
      "bin_sha256": "...",
      "format": "ncnn",
      "labels": ["person", "bicycle", "car", "..."]
    }
  ]
}
```

Suggested model `id` values for newer clients: `yolo26_nano`, `yolo26_small`, `yolo26_medium`. Legacy ids (`yolov8_nano`, `yolov8_small`, `mobilenet_ssd`) may still exist on older servers.

### GET /models/:id/param

Requires bearer token. Downloads the NCNN `.param` file for the given model id.

### GET /models/:id/bin

Requires bearer token. Downloads the NCNN `.bin` file for the given model id.

### POST /ai-models (admin registration)

Requires bearer token. Registers or updates model metadata. If `param_sha256` / `bin_sha256` are omitted, the server computes them from files under `DOGAN_MODELS_DIR/{model_name}/`.

Request:
```json
{
  "model_name": "yolo26_nano",
  "param_sha256": "...",
  "bin_sha256": "...",
  "labels": ["person", "car"],
  "format": "ncnn",
  "version": "1.0.0"
}
```

On-disk layout: `{DOGAN_MODELS_DIR}/{model_name}/model.param` and `model.bin`. Use `scripts/register-models.ps1` on the server to scan and register models.

## Sounds

### GET /sounds

Requires bearer token.

Response:
```json
{
  "sounds": [
    {
      "id": "bump",
      "url": "https://.../bump.ogg",
      "sha256": "...",
      "alert_type": "bump",
      "format": "ogg"
    }
  ]
}
```

Alert types: `bump`, `person`, `sound_spike`, `vehicle_departed`, `intrusion`, `overspeed`, `speed_camera`, `generic_warning`

## Settings

### GET /settings?device_id={device_id}

Requires bearer token. Returns the operational settings the device should apply. Omitted keys are left unchanged on the client.

Response:
```json
{
  "device_id": "<android_id>",
  "operating_mode": "watchman",
  "ai_model": "yolo26_nano",
  "capture_interval_ms": 15000,
  "telemetry_interval_ms": 1000,
  "object_detection_on_device": false,
  "screen_on_interval_min": 5,
  "detection_image_quality": "balanced",
  "sending_image_quality": "balanced",
  "realtime_fps": 5,
  "alert_volume": "balanced",
  "alert_duration": "3",
  "min_detection_confidence": 0.45,
  "stream_mode": "video_audio",
  "wifi_only_downloads": false,
  "settings_sync_interval_sec": 60
}
```

The client polls this endpoint every `settings_sync_interval_sec` (default 15) while connected. Server address, username/password, active camera, show-detection-boxes, and **object detection on device** remain device-local. The Android client ignores `wifi_only_downloads` if present (alert sounds always download when connected).

## Telemetry

### POST /telemetry

Requires bearer token. **Fixed 14-field schema only.**

Request:
```json
{
  "device_id": "<android_id>",
  "recorded_at": "2026-07-11T12:00:00Z",
  "gps_location": { "latitude": 35.6892, "longitude": 51.3890 },
  "gps_signal_quality": "good",
  "speed_kmh": 45.0,
  "network_signal_strength_dbm": -85,
  "network_type": "4G",
  "cabin_noise_rms": 1200.5,
  "battery_temperature_celsius": 32.0,
  "battery_percentage": 78,
  "rear_camera_frame_base64": "<JPEG base64>",
  "front_camera_frame_base64": "<JPEG base64>",
  "ambient_light_lux": 150.0,
  "server_latency_ms": 42,
  "device_ip_address": "192.168.1.5"
}
```

Response: HTTP 2xx on success. Client deletes the SQLite row after successful upload.

## Diagnostic Audio

### POST /diagnostic-audio

Multipart form:
- `metadata` — JSON with `segment_id`, `start_ms`, `end_ms`, `rms_peak`, `linked_alert_id`, `mode`, `device_id`
- `audio` — WAV file

## WebRTC (LiveKit)

Streaming uses **LiveKit**. The server issues short-lived tokens; Android and web connect directly to the LiveKit server. Custom WebSocket signaling is not used.

### POST /streaming/token

Request:
```json
{
  "device_id": "<android_id>",
  "role": "publisher",
  "identity": "optional-custom-identity"
}
```

Roles: `publisher` (Android camera/audio source) or `subscriber` (web viewer).

Response:
```json
{
  "token": "<jwt>",
  "url": "ws://localhost:7880",
  "room": "device-<android_id>",
  "identity": "publisher-<android_id>",
  "expires_at": "2026-07-14T10:00:00Z"
}
```

### POST /webrtc/session

Android convenience alias that always issues a **publisher** token.

Request:
```json
{ "device_id": "<android_id>" }
```

Response:
```json
{
  "session_id": "device-abc123",
  "token": "<jwt>",
  "url": "ws://localhost:7880",
  "room": "device-abc123",
  "identity": "publisher-abc123",
  "expires_at": "2026-07-14T10:00:00Z",
  "ice_servers": [
    { "urls": ["stun:stun.l.google.com:19302"] }
  ]
}
```

### GET /webrtc/connections?device-id=

Lists recent LiveKit session records for a device.

## Deprecated Endpoints

- `POST /frame` — still supported; prefer `/telemetry` (frames included in payload)
- `POST /device-metrics` — still supported; prefer `/telemetry` (battery fields included in payload)

## Server alignment

This document is the Android client contract. The Go server at `/dogan/api/v1` implements these routes plus web routes (`/devices/:id/*`, `/images`, `PATCH /settings`). Default Android base URL must include the `/dogan` path prefix.

Docker compose (`dogan.yml`) publishes:
- API: host **8090** → container `8080` → clients use `http://host:8090/dogan`
- Web UI: host **8092**; baked `VITE_API_BASE_URL` = `http://host:8090/dogan/api/v1`
- LiveKit: **7880** (WS), **7881** (TCP), **7882** (UDP); public URL `ws://host:7880`
- Device API key default: `dogan-dev-key`

