# Features & Capabilities

Dogan v2.0 multi-mode vehicle monitoring and driving assistance platform.

## Operating Modes

| Mode | Behavior |
|------|----------|
| **Watchman** | Jolts, people near car, sharp sound alerts |
| **Spotter** | Tap-to-watch parked vehicles; departure alerts |
| **Watchman-Spotter** | Both Watchman and Spotter simultaneously |
| **Copilot** | Driving: intrusion warnings, speed limits, speed cameras |

## Core Monitoring

- Background foreground service with camera, microphone, and GPS
- Front and rear camera capture for telemetry
- NCNN on-device object detection (models downloaded from server)
- Server-downloaded alert sounds with configurable volume and duration
- WebRTC streaming: video-only, audio-only, or video+audio

## Telemetry

Fixed 14-field payload uploaded via `POST /dogan/api/v1/telemetry`:
GPS location, GPS quality, speed (km/h), network signal, network type, cabin noise RMS, battery temp/%, rear+front frames, timestamp, ambient light, server latency, IP address.

SQLite buffer flushed after successful upload.

## Diagnostics Screen

Tests internet connectivity, server API (health + auth), and WebRTC session/signaling.

## Settings

| Setting | Description |
|---------|-------------|
| Operating mode | Watchman / Spotter / Watchman-Spotter / Copilot |
| Server address | Base URL of the Dogan server |
| AI model | NCNN model from server manifest |
| Alert volume | Off / Very Low / Low / Balanced / High / Very High |
| Alert duration | 1–5 seconds |
| Min detection confidence | 0.10–0.95 |
| Telemetry interval | Default 1000 ms |
| Stream mode | Video / Audio / Video+Audio |
| Wi-Fi only downloads | Models and sounds |

## Cabin Noise Diagnostics

Ordered WAV segment archival with SQLite index and server upload for diagnosis.
