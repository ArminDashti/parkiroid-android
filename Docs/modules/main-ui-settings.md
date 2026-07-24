# Module: Main UI and settings hub

## Responsibility

MainActivity hosts a dark button grid (72dp rows). Layout:

| Left | Right |
|------|-------|
| Copilot (mode; re-tap → OFF) | gear → Copilot settings |
| Spotter (mode; re-tap → OFF) | gear → Spotter settings |
| Watchman (mode; re-tap → OFF) | gear → Watchman settings |
| Connectivity status (Connected/Disconnected) | gear → Connectivity |
| Record → Recording settings | Settings → General |
| Logs | Exit |

No Camera button on main. Preview opens from section settings (`CameraActivity`) only when a mode is already active. Default unset `operating_mode` is **OFF**. Recording default is off (`recording_enabled`). No auto-connect on launch.

Mode persist via SettingsPublisher. Exit stops services and kills the process.

Connectivity uses username/password (`SessionCredentials` → `POST /auth`); the same bearer covers API and LiveKit. Defaults: API `dogan-api.xaigrok.ir:443`, LiveKit `dogan-livekit.xaigrok.ir:443`, `armin`/`dogan123`. Connect is manual; API URL / Stream URL are combined host:port fields.

Settings sections use standard order: **Configure → Retention → Browse → Danger**. Section Logs filter by `LogSection`; main Logs shows all.

## Key types

| Type | Role |
|------|------|
| `MainActivity` | Mode selection + navigation hub; mode activate starts CaptureService + history |
| `CameraActivity` | Live preview + detection overlay (surface attach only); Watchman HUD (jolt/sound) |
| `SensorHudBridge` | Live jolt/sound values for preview HUD |
| `SettingsActivity` | Focused section UI; auto-save + PUT; Preview requires active mode |
| `SettingsPublisher` | DataStore save + `PUT /api/v1/settings` |
| `ServerConnectionManager` | Connect/disconnect + ping/LiveKit refresh |
| `SessionCredentials` | In-memory username/password for all API clients |
| `LiveKitStatusCache` | Publisher + session count for Connectivity UI |
| `AppLogger` / `LogSection` | Section-tagged ring buffer; filtered Logs screens |
| `HistoryFramesActivity` | Gallery of retained frames with boxes |
| `HistoryFrameViewerActivity` | Full-screen frame with previous/next |
| `FrameHistoryStore` | SQLite + JPEG ring buffer |

## Dependencies

- `SettingsStore`, `ServerConnectionManager`, `CaptureService`, `DoganCamera`, `DetectionOverlayBridge`, `SensorHudBridge`
