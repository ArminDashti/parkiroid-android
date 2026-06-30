# Features & Capabilities

This document describes what Parkiroid can do today (version 1.0).

## Core monitoring

### Background operation

When the user taps **Start Monitoring**, the app continues working with the screen off. A persistent notification (*Parkiroid running*) confirms that monitoring is active. Camera capture runs on the foreground service lifecycle so photos still upload when the app is in the background (required on Android 10 and newer). The live preview pauses when you leave the app but resumes when you return.

### Rear-camera capture

All photos use the phone’s **rear camera**, aimed at the scene behind or around the vehicle. While monitoring is active, the main screen can show a live preview so the user can confirm the camera angle before leaving the car.

### Periodic photos

On a fixed schedule, the app takes a photo and sends it to the server. The default interval is **15 seconds**. The interval can be set between **1 and 60 seconds** (see Settings Guide).

### Battery reporting

Alongside each capture cycle, the app sends:

- **Battery level** (percentage)
- **Battery temperature** (degrees Celsius)

This helps operators confirm the monitoring phone is still powered and not overheating during long sessions.

## Motion detection (bump alerts)

The app listens for sudden movement of the phone—typical of a bump, jolt, or contact transferred through the vehicle body.

When motion is detected:

1. An **immediate extra photo** is taken (in addition to the regular schedule).
2. A **high-priority notification** appears on the phone: *Vehicle motion detected*.

Motion detection runs whenever monitoring is active. It does not replace scheduled captures; it adds reactive captures when something may have happened.

## Object detection (cloud)

Object detection runs **on the server**. The phone uploads photos on the chosen interval; the Parkiroid server performs detection and analysis. The monitoring notification shows that frames are being uploaded at the configured interval.

A valid server address is required to start monitoring.

## Settings & persistence

All configuration is stored on the phone and survives app restarts:

- Server address and API key
- Capture/upload interval

## User interface

| Screen | Purpose |
|--------|---------|
| **Main** | Start/stop monitoring, status, live camera preview, link to Settings |
| **Settings** | Server connection, upload interval, save configuration |

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
| **Photo (frame)** | On each capture cycle, and on motion-triggered captures |
| **Battery metrics** | On each capture cycle |
| **Device identity** | With every upload, so the server can tell phones apart |

Authentication uses an API key that must match the server configuration.
