# Features & Capabilities

This document describes what Parkiroid can do today (version 1.0).

## Core monitoring

### Background operation

When the user taps **Start Monitoring**, the app continues working with the screen off. A persistent notification (*Parkiroid running*) confirms that monitoring is active. This is intentional: Android requires visible background activity for reliable long-running camera use.

### Rear-camera capture

All photos use the phone’s **rear camera**, aimed at the scene behind or around the vehicle. While monitoring is active, the main screen can show a live preview so the user can confirm the camera angle before leaving the car.

### Periodic photos

On a fixed schedule, the app takes a photo and optionally sends it to the server. The default interval is **15 seconds**. In server-based object detection mode, the interval can be set between **1 and 60 seconds** (see Settings Guide).

### Battery reporting

Alongside each capture cycle (when a server is configured), the app sends:

- **Battery level** (percentage)
- **Battery temperature** (degrees Celsius)

This helps operators confirm the monitoring phone is still powered and not overheating during long sessions.

## Motion detection (bump alerts)

The app listens for sudden movement of the phone—typical of a bump, jolt, or contact transferred through the vehicle body.

When motion is detected:

1. An **immediate extra photo** is taken (in addition to the regular schedule).
2. A **high-priority notification** appears on the phone: *Vehicle motion detected*.

Motion detection runs whenever monitoring is active. It does not replace scheduled captures; it adds reactive captures when something may have happened.

## Object detection

Parkiroid can identify objects in camera frames (for example, cars, trucks, buses, motorcycles, bicycles, and people). Two modes are available:

### On device

- Detection runs **on the phone** using a built-in AI model.
- Results appear in the ongoing monitoring notification (e.g. *Detected: car, person*).
- Optionally, **bounding boxes** can be drawn on the live camera preview so the user sees what the app detected in real time.
- A **confidence threshold** setting controls how sure the app must be before reporting an object (higher = fewer, more certain detections).

### On server

- The phone **uploads photos** to the Parkiroid server on the chosen interval.
- **Detection happens on the server** (not on the phone).
- The notification indicates that frames are being uploaded for server-side analysis.

Users choose one mode in Settings. On-device mode can run even without a server configured for uploads; server mode requires a valid server address.

## Settings & persistence

All configuration is stored on the phone and survives app restarts:

- Server address and API key
- Object detection mode and related options
- Capture/upload interval (where applicable)

## User interface

| Screen | Purpose |
|--------|---------|
| **Main** | Start/stop monitoring, status, live camera preview, link to Settings |
| **Settings** | Server connection, object detection options, save configuration |

## Permissions (what the app needs from the user)

| Permission | Why |
|------------|-----|
| **Camera** | To capture photos and show preview |
| **Internet** | To send photos and battery data to the server |
| **Notifications** | To show monitoring status and motion alerts |

The app asks for camera access before monitoring can start.

## What the app sends to the server

When a server is configured and reachable, each device sends:

| Data | When |
|------|------|
| **Photo (frame)** | On each capture cycle, and on motion-triggered captures in server mode |
| **Battery metrics** | On each capture cycle |
| **Device identity** | With every upload, so the server can tell phones apart |

Authentication uses an API key that must match the server configuration.
