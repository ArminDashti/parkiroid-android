# Settings Guide

All configurable options live on the **Settings** screen. Changes are saved to the phone and apply the next time monitoring runs or on the next capture cycle.

## Server connection

### Server address

The web address of your Parkiroid server—the place where photos and battery readings are sent.

- Enter the base URL only (no path after the host).
- Examples: `http://192.168.1.10:8080`, `https://parkiroid.example.com`
- Trailing slashes are optional; the app normalizes the value.

If left empty or invalid, the app can still run **on-device object detection**, but **photos and battery data will not upload**.

### API key

A shared secret that must **match the server**. Think of it as a password for the app to talk to your backend. Your server administrator provides this value. A default development key may be used in test environments only—production deployments should use a unique key.

## Object detection

Controls whether and how the app identifies objects (vehicles, people, etc.) in camera frames.

### Mode: On device

- Detection runs **on the phone**.
- Results appear in the monitoring notification after each capture.
- Does **not** require uploading frames for detection (uploads still occur if a server is configured).
- Best when you want immediate on-phone feedback or limited connectivity for analysis.

### Mode: On server

- The phone **uploads photos** on a schedule; the **server** performs detection.
- The notification shows that frames are being uploaded at the chosen interval.
- Best when you want centralized processing, logging, or integration with server-side tools.

## Frame upload interval

**Visible when server detection mode is selected.**

How often photos are sent to the server for analysis.

| Option | Typical use |
|--------|-------------|
| 1–5 seconds | High-frequency monitoring; higher battery and data use |
| 15 seconds (default) | Balanced default for most parking scenarios |
| 30–60 seconds | Longer sessions; lower battery and bandwidth |

Shorter intervals give more frequent updates but drain the phone faster.

## Confidence threshold

**Visible when on-device detection mode is selected.**

How confident the app must be before reporting an object (shown as a percentage).

- **Lower threshold** — More detections, including uncertain ones; may include false positives.
- **Higher threshold** — Fewer detections, only when the app is more confident; may miss faint or distant objects.

Default is **25%**. Adjust based on field testing in your typical parking environment.

## Show bounding boxes on camera preview

**Visible when on-device detection mode is selected.**

When enabled, colored boxes appear on the **live camera preview** on the main screen, outlining detected objects. Useful for aiming the camera and validating detection during setup. Can be turned off for privacy or a cleaner preview during active monitoring.

## Default values (reference)

| Setting | Default |
|---------|---------|
| Server address | Pre-filled with a development host in some builds; should be replaced for production |
| API key | Development default; replace for production |
| Capture / upload interval | 15 seconds |
| Object detection mode | On device |
| Confidence threshold | 25% |
| Show bounding boxes | Off |
