# Endpoints (Android client usage)

| Method | Path | What it does | Auth |
|--------|------|--------------|------|
| POST | /api/v1/auth | Authenticate with username/password; receive bearer token | Username + password |
| POST | /api/v1/telemetry | Upload device telemetry + frames | Bearer |
| GET | /api/v1/settings | Pull server operational settings | Bearer |
| GET | /api/v1/sounds | List downloadable alert sounds | Bearer |
| GET | /api/v1/sounds/:id | Download alert sound file | Bearer |
| POST | /api/v1/webrtc/session | LiveKit session / streaming token (same bearer as API) | Bearer |
| GET | /api/v1/webrtc/connections | List recent LiveKit sessions for device | Bearer |
| GET | /api/v1/health | Latency / reachability check | No |
| GET | /api/v1/actions/pending | Pending capture/actions for device | Bearer |

**Telemetry payload** (POST /api/v1/telemetry): `device_id`, `recorded_at`, `gps_location`, `gps_signal_quality`, `speed_kmh`, `network_signal_strength_dbm`, `network_type`, `cabin_noise_rms`, `battery_temperature_celsius`, `battery_percentage`, `rear_camera_frame_base64`, `front_camera_frame_base64`, `ambient_light_lux`, `server_latency_ms`, `device_ip_address`, `cpu_usage_percent`, `ram_usage_percent`

## Local CLI scripts

| Command | What it does |
|---------|--------------|
| `.\scripts\export-apk.ps1` / `./scripts/export-apk.sh` | Build APK and copy to `exports/` |
| `.\install-on-phone-directly.ps1` / `./scripts/install-apk.sh` | Rebuild + install APK on a USB-debugging device via ADB (`-SkipBuild` to reuse existing APK; `-Launch`/`--launch`) |

