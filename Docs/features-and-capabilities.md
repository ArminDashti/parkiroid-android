# Features & Capabilities

This document describes what Parkiroid can do today.

## Core monitoring

### Background operation

Selecting a camera while connected to the server starts background monitoring. A persistent notification (*Parkiroid running*) confirms activity. Capture runs in a foreground service so photos and sound monitoring continue with the screen off.

### Front and rear camera

The main screen provides **Front Camera** and **Rear Camera** buttons to switch the active lens. Preview updates immediately; background capture follows the selected camera.

### Sound monitoring

While monitoring runs, the app samples the microphone and logs significant sound-level spikes to the in-app **Logs** screen.

### Periodic photos

Screenshots are taken on a configurable schedule. The interval is set in **milliseconds** on the Settings screen (minimum 100 ms; default 15,000 ms).

### Battery reporting

Each capture cycle sends battery level (%) and temperature (°C) to the server when connected.

## Motion detection (bump alerts)

Accelerometer-based bump detection triggers an extra photo and a high-priority *Vehicle motion detected* notification.

## Object detection

- **On device** — toggle in Settings; pipeline is wired (model assets can be added later).
- **Server** — frames upload to the Parkiroid server when connected.

## Server connection

- **Connect / Disconnect** on the Settings screen
- **Connection status** on the main screen (Connected / Connecting / Failed / Disconnected)

## Settings

| Setting | Description |
|---------|-------------|
| Server address | Base URL of the Parkiroid server |
| Connect / Disconnect | Test auth and manage session |
| AI model | YOLOv8 Nano, YOLOv8 Small, or MobileNet SSD |
| Screenshot interval | Milliseconds between captures |
| Object detection on device | Enable/disable local inference |
| Turn on screen every N seconds | Wakes the display periodically (0 = off) |
| Detection image quality | Very Low / Low / Balanced / High / Original |
| Sending image quality | JPEG quality for server uploads |
| Realtime FPS | Target preview analysis rate (1–30 FPS) |

## User interface

| Screen | Purpose |
|--------|---------|
| **Main** | Server status, front/rear camera, settings, logs, live preview |
| **Settings** | Server, AI, timing, quality, detection options |
| **Logs** | Scrollable in-app event log |

## Permissions

| Permission | Why |
|------------|-----|
| Camera | Photos and preview (front and rear) |
| Microphone | Sound monitoring |
| Internet | Server uploads |
| Notifications | Monitoring status and motion alerts |
