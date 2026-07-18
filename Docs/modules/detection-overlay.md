# Module: Detection overlay

Draw on-device YOLO detections as bounding boxes on the Preview screen and on history frame thumbnails. Watchman Preview also shows live jolt/sound via `SensorHudBridge`.

## Behavior

- `DetectionOverlayView` maps image-space boxes to view coordinates
- Spotter tap-to-watch enabled on CameraActivity when mode is Spotter
- Live overlays published via `DetectionOverlayBridge` from `CaptureService`
- `SensorHudBridge` publishes jolt m/s² and sound RMS for Watchman HUD

## Settings

- Preview always draws boxes when detections exist
- DataStore key `show_bounding_boxes` remains stored but is no longer exposed in General UI
- On-device detection is always on
