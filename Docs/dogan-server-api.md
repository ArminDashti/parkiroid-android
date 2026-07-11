# Dogan Server API Specification

API prefix: `{base}/dogan/api/v1/`

## Authentication

### POST /auth

Request:
```json
{ "api_key": "dogan-dev-key" }
```

Response:
```json
{ "token": "<bearer>", "expires_at": "2026-07-11T12:00:00Z" }
```

## Health

### GET /health

Returns HTTP 200 when the server is reachable. Used for latency measurement.

## Models (NCNN)

### GET /models

Requires bearer token.

Response:
```json
{
  "models": [
    {
      "id": "yolov8_nano",
      "param_url": "https://.../model.param",
      "bin_url": "https://.../model.bin",
      "param_sha256": "...",
      "bin_sha256": "...",
      "format": "ncnn",
      "labels": ["person", "car", "motorcycle", "truck", "speed_camera", "speed_limit_sign"]
    }
  ]
}
```

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

## WebRTC

### POST /webrtc/session

Request:
```json
{ "device_id": "<android_id>" }
```

Response:
```json
{
  "session_id": "abc123",
  "signaling_url": "wss://{host}/dogan/api/v1/webrtc/signal",
  "ice_servers": [
    { "urls": ["stun:stun.l.google.com:19302"] },
    { "urls": ["turn:..."], "username": "...", "credential": "..." }
  ]
}
```

### WS /webrtc/signal

WebSocket signaling for SDP offer/answer and ICE candidates. Auth via bearer token.

## Deprecated Endpoints

- `POST /frame` — replaced by `/telemetry` (frames included in payload)
- `POST /device-metrics` — replaced by `/telemetry` (battery fields included in payload)
