# Settings Guide

All configurable options live on the **Settings** screen. Tap **Save** to persist changes.

## Server connection

### Server address

Base URL of your Dogan server (e.g. `http://192.168.1.10:8080`). No trailing path required.

### API key

Shared secret matching the server configuration.

### Connect / Disconnect

- **Connect** — saves the current form, authenticates, and updates connection status on the main screen.
- **Disconnect** — clears the active server session.

Monitoring uploads only run while **Connected**.

## AI model

Select the on-device model identifier:

- YOLOv8 Nano (default)
- YOLOv8 Small
- MobileNet SSD

Model assets are not bundled yet; the selection is persisted for future integration.

## Screenshot interval (milliseconds)

Time between automatic captures. Minimum **100 ms**. Default **15000 ms** (15 seconds).

Very short intervals increase battery and data use.

## Object detection on device

When enabled, the capture service runs local inference on each frame (stub until model files are added).

## Turn on screen every N seconds

Periodically wakes the display during monitoring. Set to **0** to disable.

## Image quality

Two independent quality settings:

| Setting | Used for |
|---------|----------|
| **Detection image quality** | On-device object detection input |
| **Sending image quality** | JPEG compression for server uploads |

Levels: **Very Low**, **Low**, **Balanced** (default), **High**, **Original**.

## Realtime FPS

Target frames-per-second for live preview analysis when on-device detection is enabled. Options: 1, 2, 5, 10, 15, 24, 30. Default: **5 FPS**.

## Default values

| Setting | Default |
|---------|---------|
| Server address | `https://dogan.xaigrok.ir` |
| API key | `dogan-dev-key` |
| Screenshot interval | 15000 ms |
| Object detection on device | Off |
| Screen wake interval | 0 (off) |
| Detection quality | Balanced |
| Sending quality | Balanced |
| Realtime FPS | 5 |
| AI model | YOLOv8 Nano |
