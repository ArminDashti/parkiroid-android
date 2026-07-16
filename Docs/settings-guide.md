# Settings Guide

All configurable options live on the **Settings** screen. Tap **Save** to persist changes.

## Server connection

### Server address

Base URL of your Dogan server including the `/dogan` path (e.g. `http://192.168.1.10:8090/dogan`). No trailing slash. Docker compose maps the API to host port **8090**.

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

Model assets are downloaded from the server when monitoring starts, verified with SHA-256, and loaded into the on-device NCNN runtime. Supported model ids: `yolov8_nano`, `yolov8_small` (YOLOv8 NCNN export with `in0`/`out0` blobs). `mobilenet_ssd` is not yet supported by the NCNN decoder.

## Screenshot interval (milliseconds)

Time between automatic captures. Minimum **100 ms**. Default **15000 ms** (15 seconds).

Very short intervals increase battery and data use.

## Object detection on device

When enabled, the capture service runs local inference on each frame (stub until model files are added).

## Keep-alive: turn on screen every N minutes

While monitoring runs in the background, the app can briefly wake the display on a timer so aggressive power management is less likely to stop the foreground service. Set the interval in **minutes**; use **0** to disable.

Recommended values: **3–10 minutes** for long parking sessions. Each wake is a short screen pulse (about 3 seconds) and is logged under **KeepAlive** in Logs.

## Pull settings from server (seconds)

While connected, the app polls `GET /api/v1/settings` on this interval (default **60** seconds, minimum **10**) and merges server values into local settings. Operational fields such as mode, intervals, and alert options can be managed centrally on the server.

Server address, API key, and active camera are never overwritten by the server.

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
| Server address | `https://dogan.xaigrok.ir/dogan` (local Docker: `http://<host-ip>:8090/dogan`) |
| API key | `dogan-dev-key` |
| Screenshot interval | 15000 ms |
| Object detection on device | Off |
| Screen wake interval | 0 (off) |
| Settings sync interval | 60 s |
| Detection quality | Balanced |
| Sending quality | Balanced |
| Realtime FPS | 5 |
| AI model | YOLOv8 Nano |
