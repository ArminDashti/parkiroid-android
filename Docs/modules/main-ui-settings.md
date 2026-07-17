# Module: Main UI and settings hub

## Responsibility

MainActivity hosts the dark button grid (Camera 80dp; other buttons 72dp). Mode tabs persist `operating_mode` (including OFF) via SettingsPublisher. Section buttons open SettingsActivity with `EXTRA_SECTION`. Camera opens CameraActivity when mode ≠ OFF. Exit stops services and kills the process.

Connectivity uses username/password (`SessionCredentials` → `POST /auth`); the same bearer covers API and LiveKit. The Connect button shows only Connected/Disconnected; ping, API, and LiveKit status sit above it with error text on failure.

## Key types

| Type | Role |
|------|------|
| `MainActivity` | Mode selection + navigation hub |
| `CameraActivity` | Live preview + detection overlay (eye) |
| `SettingsActivity` | Focused section UI; auto-save + PUT |
| `SettingsPublisher` | DataStore save + `PUT /api/v1/settings` |
| `ServerConnectionManager` | Connect/disconnect + ping/LiveKit refresh |
| `SessionCredentials` | In-memory username/password for all API clients |
| `LiveKitStatusCache` | Publisher + session count for Connectivity UI |
| `HistoryFramesActivity` | Gallery of retained frames with boxes |
| `HistoryFrameViewerActivity` | Full-screen frame with previous/next |
| `FrameHistoryStore` | SQLite + JPEG ring buffer |

## Dependencies

- `SettingsStore`, `ServerConnectionManager`, `CaptureService`, `DoganCamera`, `DetectionOverlayBridge`
