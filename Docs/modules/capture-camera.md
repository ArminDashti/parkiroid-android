# Module: CaptureService / DoganCamera

Foreground monitoring service and CameraX bindings.

## Responsibility

- Start/stop when operating mode ≠ OFF
- Run on-device YOLO detection at mode FPS
- Telemetry, uploads, LiveKit, bump/sound sensors

## Camera policy

- **Default (duty-cycle):** open camera → capture one still → run NCNN → unbind. Sensor stays off between frames.
- **Continuous:** only while Preview listener is attached or `recording_enabled` is true.
- Still helpers: `DoganCamera.captureBitmap`, `captureFromFacing` (rebind only if `preferContinuousBind`).

## Recording

- Gated by global `recording_enabled` (default false).
- When recording is on, continuous camera bind is used.
