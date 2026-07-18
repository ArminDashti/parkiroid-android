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

No Camera button on main. Preview opens from section settings (`CameraActivity`). Default unset `operating_mode` is **OFF**. No auto-connect on launch.

Mode persist via SettingsPublisher. Exit stops services and kills the process.

Connectivity uses username/password (`SessionCredentials` → `POST /auth`); the same bearer covers API and LiveKit. Connect is manual; API URL / Stream URL are combined host:port fields.

Settings sections use standard order: **Configure → Retention → Browse → Danger**.

## Key types

| Type | Role |
|------|------|
| `MainActivity` | Mode selection + navigation hub |
| `CameraActivity` | Live preview + detection overlay; Watchman HUD (jolt/sound) |
| `SensorHudBridge` | Live jolt/sound values for preview HUD |
| `SettingsActivity` | Focused section UI; auto-save + PUT; Preview starts CaptureService |
| `SettingsPublisher` | DataStore save + `PUT /api/v1/settings` |
| `ServerConnectionManager` | Connect/disconnect + ping/LiveKit refresh |
| `SessionCredentials` | In-memory username/password for all API clients |
| `LiveKitStatusCache` | Publisher + session count for Connectivity UI |
| `HistoryFramesActivity` | Gallery of retained frames with boxes |
| `HistoryFrameViewerActivity` | Full-screen frame with previous/next |
| `FrameHistoryStore` | SQLite + JPEG ring buffer |

## Dependencies

- `SettingsStore`, `ServerConnectionManager`, `CaptureService`, `DoganCamera`, `DetectionOverlayBridge`, `SensorHudBridge`
